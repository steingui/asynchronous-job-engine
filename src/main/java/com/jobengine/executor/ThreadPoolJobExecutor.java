package com.jobengine.executor;

import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;
import com.jobengine.model.JobStatus;
import com.jobengine.service.CPUSimulator;
import com.jobengine.service.IOSimulator;
import com.jobengine.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import com.jobengine.exception.InvalidJobException;

/**
 * Thread pool job executor - processes jobs using a pool of platform threads.
 *
 * <h2>How It Works</h2>
 * <p>Jobs are submitted to a {@link ThreadPoolExecutor} which manages a pool of
 * reusable worker threads. Each job runs in its own thread, enabling parallel
 * execution up to the pool's maximum size.</p>
 *
 * <h3>Execution Flow</h3>
 * <ol>
 *   <li>Job is submitted to the executor</li>
 *   <li>If a core thread is available, it picks up the job immediately</li>
 *   <li>If all core threads are busy, job goes to the work queue</li>
 *   <li>If queue is full, new threads are created up to maxSize</li>
 *   <li>If at max capacity, rejection policy kicks in (CallerRunsPolicy)</li>
 * </ol>
 *
 * <h2>JVM Internals</h2>
 * <ul>
 *   <li><b>Stack:</b> Each platform thread has its own stack (~1MB by default on Linux).
 *       With 16 threads, that's ~16MB of stack space reserved.</li>
 *   <li><b>Heap:</b> Thread objects, Runnable tasks, and job data live on the heap.
 *       The work queue also consumes heap space.</li>
 *   <li><b>Context Switching:</b> OS scheduler switches between threads. Each switch
 *       saves/restores registers, flushes caches. Cost: ~1-10 microseconds.</li>
 *   <li><b>GC Impact:</b> More threads = more thread-local allocations = more GC roots
 *       to scan. Can increase GC pause times under heavy load.</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Throughput:</b> Parallel execution improves throughput for CPU-bound tasks.
 *       Limited by pool size and available CPU cores.</li>
 *   <li><b>Latency:</b> Queue wait time adds latency. Priority: running jobs > queued jobs.</li>
 *   <li><b>Resource Usage:</b> Each thread consumes memory and OS resources.
 *       Context switching adds CPU overhead.</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>CPU-bound tasks that benefit from parallelism</li>
 *   <li>When you need controlled, bounded concurrency</li>
 *   <li>Mixed workloads with both CPU and I/O operations</li>
 *   <li>When you need thread-local state isolation</li>
 * </ul>
 *
 * <h2>Trade-offs</h2>
 * <table border="1">
 *   <caption>Thread Pool Executor Pros and Cons</caption>
 *   <tr><th>Pros</th><th>Cons</th></tr>
 *   <tr>
 *     <td>True parallelism on multi-core CPUs</td>
 *     <td>Limited scalability (threads are expensive)</td>
 *   </tr>
 *   <tr>
 *     <td>Fine-grained control over pool size</td>
 *     <td>Context switching overhead</td>
 *   </tr>
 *   <tr>
 *     <td>Mature, well-understood model</td>
 *     <td>Threads blocked on I/O waste resources</td>
 *   </tr>
 *   <tr>
 *     <td>Good debugger/profiler support</td>
 *     <td>Risk of thread starvation under load</td>
 *   </tr>
 * </table>
 *
 * <h2>Configuration Tips</h2>
 * <ul>
 *   <li>For CPU-bound: poolSize ≈ number of CPU cores</li>
 *   <li>For I/O-bound: poolSize can be larger (cores * 2 or more)</li>
 *   <li>Queue size trades memory for burst handling capacity</li>
 *   <li>Keep-alive time balances responsiveness vs resource usage</li>
 * </ul>
 *
 * @author gsk
 * @see java.util.concurrent.ThreadPoolExecutor
 */
@Component
public class ThreadPoolJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolJobExecutor.class);

    private final ThreadPoolExecutor threadPoolExecutor;
    private final CPUSimulator cpuSimulator;
    private final IOSimulator ioSimulator;
    private final MetricsService metricsService;

    /**
     * Constructs a ThreadPoolJobExecutor with the required dependencies.
     *
     * @param threadPoolExecutor the thread pool to use for execution
     * @param cpuSimulator       simulator for CPU-bound operations
     * @param ioSimulator        simulator for I/O operations
     * @param metricsService     service for recording metrics
     */
    public ThreadPoolJobExecutor(ThreadPoolExecutor threadPoolExecutor,
                                  CPUSimulator cpuSimulator,
                                  IOSimulator ioSimulator,
                                  MetricsService metricsService) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.cpuSimulator = cpuSimulator;
        this.ioSimulator = ioSimulator;
        this.metricsService = metricsService;
    }

    @Override
    public CompletableFuture<JobResult> execute(Job job) {
        if (job == null) {
            throw new InvalidJobException("Job must not be null");
        }

        log.debug("Submitting to thread pool: jobId={}, jobName={}, poolSize={}, activeThreads={}, queueSize={}",
                job.getId(), job.getName(), 
                threadPoolExecutor.getPoolSize(),
                threadPoolExecutor.getActiveCount(),
                threadPoolExecutor.getQueue().size());

        job.setStatus(JobStatus.PENDING);

        return CompletableFuture.supplyAsync(() -> executeJob(job), threadPoolExecutor);
    }

    private JobResult executeJob(Job job) {
        metricsService.incrementActive(ExecutionMode.THREAD_POOL);
        var startTime = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startTime);

        log.debug("Thread pool execution started: jobId={}, thread={}", 
                job.getId(), Thread.currentThread().getName());

        // Generate random limit ONCE (reused across retries)
        var primeLimit = cpuSimulator.generateRandomLimit();

        try {
            // CPU-bound work: calculate primes
            var primesFound = cpuSimulator.countPrimesUpTo(primeLimit);
            
            // I/O-bound work: simulate network/database call
            var result = ioSimulator.simulateWork(job.getPayload() + " [primes=" + primesFound + "]");

            var executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());

            var jobResult = JobResult.success(job, result, executionTime);
            metricsService.recordJobCompletion(ExecutionMode.THREAD_POOL, executionTime, true);

            log.info("Thread pool execution completed: jobId={}, thread={}, duration={}ms",
                    job.getId(), Thread.currentThread().getName(), executionTime.toMillis());

            return jobResult;

        } catch (Exception e) {
            var executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());

            var jobResult = JobResult.failure(job, e.getMessage(), executionTime);
            metricsService.recordJobCompletion(ExecutionMode.THREAD_POOL, executionTime, false);

            log.error("Thread pool execution failed: jobId={}, thread={}, error={}",
                    job.getId(), Thread.currentThread().getName(), e.getMessage());

            return jobResult;
        } finally {
            metricsService.decrementActive(ExecutionMode.THREAD_POOL);
        }
    }

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.THREAD_POOL;
    }

    @Override
    public int getActiveCount() {
        return threadPoolExecutor.getActiveCount();
    }

    /**
     * Returns the current size of the work queue.
     *
     * @return number of tasks waiting in queue
     */
    public int getQueueSize() {
        return threadPoolExecutor.getQueue().size();
    }

    /**
     * Returns the current pool size (number of threads created).
     *
     * @return current number of threads in the pool
     */
    public int getPoolSize() {
        return threadPoolExecutor.getPoolSize();
    }
}

