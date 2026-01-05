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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.jobengine.exception.InvalidJobException;

/**
 * Async job executor - processes jobs using virtual threads (Java 21+).
 *
 * <h2>How It Works</h2>
 * <p>Jobs are executed using {@link CompletableFuture} backed by virtual threads.
 * Virtual threads are lightweight threads managed by the JVM, not the OS.
 * They're designed for high-throughput I/O-bound workloads.</p>
 *
 * <h3>Virtual Threads vs Platform Threads</h3>
 * <table border="1">
 *   <caption>Comparison of Platform vs Virtual Threads</caption>
 *   <tr><th>Aspect</th><th>Platform Thread</th><th>Virtual Thread</th></tr>
 *   <tr><td>Creation cost</td><td>~1ms, ~1MB stack</td><td>~1μs, ~few KB</td></tr>
 *   <tr><td>Max count</td><td>Thousands</td><td>Millions</td></tr>
 *   <tr><td>Blocking behavior</td><td>Blocks OS thread</td><td>Unmounts, frees carrier</td></tr>
 *   <tr><td>Scheduling</td><td>OS scheduler</td><td>JVM scheduler</td></tr>
 *   <tr><td>Best for</td><td>CPU-bound</td><td>I/O-bound</td></tr>
 * </table>
 *
 * <h2>JVM Internals</h2>
 * <ul>
 *   <li><b>Carrier Threads:</b> Virtual threads run on a pool of platform threads
 *       called "carrier threads" (typically = CPU cores). When a virtual thread
 *       blocks on I/O, it's unmounted from its carrier, freeing it for other work.</li>
 *   <li><b>Stack:</b> Virtual thread stacks are stored on the heap, not in native
 *       memory. They start small (~few KB) and grow as needed. This enables
 *       millions of concurrent virtual threads.</li>
 *   <li><b>GC Impact:</b> Virtual thread stacks are garbage collected when the
 *       thread completes. More virtual threads = more heap pressure, but the
 *       lightweight nature usually wins vs platform threads.</li>
 *   <li><b>Continuation:</b> When a virtual thread blocks, its state (stack frames,
 *       local variables) is saved as a "continuation" on the heap. When unblocked,
 *       the continuation is resumed on an available carrier thread.</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Throughput:</b> Excellent for I/O-bound tasks. Thousands of concurrent
 *       requests with minimal threads.</li>
 *   <li><b>Latency:</b> Low latency for I/O operations since blocking doesn't
 *       waste resources.</li>
 *   <li><b>Resource Usage:</b> Minimal per-thread overhead. Memory usage scales
 *       with actual work, not thread count.</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>I/O-bound workloads (database, network, file I/O)</li>
 *   <li>High-concurrency scenarios (thousands of concurrent tasks)</li>
 *   <li>Microservices making many outbound HTTP calls</li>
 *   <li>When you want simple blocking code with async performance</li>
 * </ul>
 *
 * <h2>Trade-offs</h2>
 * <table border="1">
 *   <caption>Async Executor Pros and Cons</caption>
 *   <tr><th>Pros</th><th>Cons</th></tr>
 *   <tr>
 *     <td>Massive concurrency (millions of threads)</td>
 *     <td>Not faster for CPU-bound work</td>
 *   </tr>
 *   <tr>
 *     <td>Simple blocking code model</td>
 *     <td>Requires Java 21+</td>
 *   </tr>
 *   <tr>
 *     <td>Efficient I/O handling</td>
 *     <td>Some libraries not yet compatible (pinning issues)</td>
 *   </tr>
 *   <tr>
 *     <td>Low memory footprint per thread</td>
 *     <td>Different debugging/profiling approach needed</td>
 *   </tr>
 * </table>
 *
 * <h2>Pinning Warning</h2>
 * <p>Virtual threads can get "pinned" to their carrier thread in certain situations:</p>
 * <ul>
 *   <li>Inside synchronized blocks (use ReentrantLock instead)</li>
 *   <li>During native method calls</li>
 * </ul>
 * <p>Pinning defeats the purpose of virtual threads. Monitor with:</p>
 * <pre>-Djdk.tracePinnedThreads=full</pre>
 *
 * @author gsk
 * @see java.lang.Thread#ofVirtual()
 * @see java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()
 */
@Component
public class AsyncJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobExecutor.class);

    private final ExecutorService virtualThreadExecutor;
    private final CPUSimulator cpuSimulator;
    private final IOSimulator ioSimulator;
    private final MetricsService metricsService;
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /**
     * Constructs an AsyncJobExecutor with the required dependencies.
     *
     * @param virtualThreadExecutor executor service using virtual threads
     * @param cpuSimulator          simulator for CPU-bound operations
     * @param ioSimulator           simulator for I/O operations
     * @param metricsService        service for recording metrics
     */
    public AsyncJobExecutor(@Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
                            CPUSimulator cpuSimulator,
                            IOSimulator ioSimulator,
                            MetricsService metricsService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.cpuSimulator = cpuSimulator;
        this.ioSimulator = ioSimulator;
        this.metricsService = metricsService;
    }

    @Override
    public CompletableFuture<JobResult> execute(Job job) {
        if (job == null) {
            throw new InvalidJobException("Job must not be null");
        }

        log.debug("Submitting to virtual thread executor: jobId={}, jobName={}",
                job.getId(), job.getName());

        job.setStatus(JobStatus.PENDING);

        return CompletableFuture.supplyAsync(() -> executeJob(job), virtualThreadExecutor);
    }

    private JobResult executeJob(Job job) {
        activeCount.incrementAndGet();
        metricsService.incrementActive(ExecutionMode.ASYNC);
        var startTime = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startTime);

        var currentThread = Thread.currentThread();
        log.debug("Async execution started: jobId={}, thread={}, isVirtual={}",
                job.getId(), currentThread.getName(), currentThread.isVirtual());

        // Generate random limit ONCE (reused across retries)
        var primeLimit = cpuSimulator.generateRandomLimit();

        try {
            // CPU-bound work: calculate primes
            var primesFound = cpuSimulator.countPrimesUpTo(primeLimit);
            
            // I/O-bound work - virtual threads excel at this
            var result = ioSimulator.simulateWork(job.getPayload() + " [primes=" + primesFound + "]");

            var executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());

            var jobResult = JobResult.success(job, result, executionTime);
            metricsService.recordJobCompletion(ExecutionMode.ASYNC, executionTime, true);

            log.info("Async execution completed: jobId={}, thread={}, isVirtual={}, duration={}ms",
                    job.getId(), currentThread.getName(), currentThread.isVirtual(), executionTime.toMillis());

            return jobResult;

        } catch (Exception e) {
            var executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());

            var jobResult = JobResult.failure(job, e.getMessage(), executionTime);
            metricsService.recordJobCompletion(ExecutionMode.ASYNC, executionTime, false);

            log.error("Async execution failed: jobId={}, thread={}, error={}",
                    job.getId(), currentThread.getName(), e.getMessage());

            return jobResult;

        } finally {
            activeCount.decrementAndGet();
            metricsService.decrementActive(ExecutionMode.ASYNC);
        }
    }

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.ASYNC;
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }
}

