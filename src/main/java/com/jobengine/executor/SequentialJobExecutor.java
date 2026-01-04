package com.jobengine.executor;

import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;
import com.jobengine.model.JobStatus;
import com.jobengine.service.IOSimulator;
import com.jobengine.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequential job executor - processes jobs one at a time in the calling thread.
 *
 * <h2>How It Works</h2>
 * <p>This executor processes jobs synchronously in the caller's thread. When you call
 * {@link #execute(Job)}, the job runs immediately in the current thread, blocking
 * until completion. The returned CompletableFuture is already completed.</p>
 *
 * <h2>JVM Internals</h2>
 * <ul>
 *   <li><b>Stack:</b> Uses the caller's stack frame. Each method call adds a frame
 *       (~few KB), popped on return. Simple, predictable memory usage.</li>
 *   <li><b>Heap:</b> Only the Job and JobResult objects are heap-allocated.
 *       Minimal GC pressure compared to thread-based approaches.</li>
 *   <li><b>GC Impact:</b> Low. Short-lived objects are collected in Young Gen.
 *       No thread-local allocations or synchronization overhead.</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Throughput:</b> Limited to one job at a time. Total time = sum of all job times.</li>
 *   <li><b>Latency:</b> Predictable - no queue wait time, no context switching.</li>
 *   <li><b>Resource Usage:</b> Minimal - no additional threads or synchronization.</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>Simple workloads with few jobs</li>
 *   <li>When job ordering must be preserved</li>
 *   <li>Debugging and testing scenarios</li>
 *   <li>When parallelism provides no benefit (single-core, I/O bottleneck elsewhere)</li>
 * </ul>
 *
 * <h2>Trade-offs</h2>
 * <table border="1">
 *   <caption>Sequential Executor Pros and Cons</caption>
 *   <tr><th>Pros</th><th>Cons</th></tr>
 *   <tr>
 *     <td>Simple, predictable behavior</td>
 *     <td>No parallelism - can't utilize multiple cores</td>
 *   </tr>
 *   <tr>
 *     <td>No thread safety concerns</td>
 *     <td>Blocks the calling thread</td>
 *   </tr>
 *   <tr>
 *     <td>Easy to debug (single call stack)</td>
 *     <td>Poor throughput for I/O-bound tasks</td>
 *   </tr>
 *   <tr>
 *     <td>Minimal memory overhead</td>
 *     <td>One slow job delays all subsequent jobs</td>
 *   </tr>
 * </table>
 *
 * @author gsk
 */
@Component
public class SequentialJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(SequentialJobExecutor.class);

    private final IOSimulator ioSimulator;
    private final MetricsService metricsService;
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /**
     * Constructs a SequentialJobExecutor with the required dependencies.
     *
     * @param ioSimulator    simulator for I/O operations
     * @param metricsService service for recording metrics
     */
    public SequentialJobExecutor(IOSimulator ioSimulator, MetricsService metricsService) {
        this.ioSimulator = ioSimulator;
        this.metricsService = metricsService;
    }

    @Override
    public CompletableFuture<JobResult> execute(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Job must not be null");
        }

        log.debug("Starting sequential execution: jobId={}, jobName={}", job.getId(), job.getName());
        
        activeCount.incrementAndGet();
        Instant startTime = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startTime);

        try {
            // Simulate I/O work
            String result = ioSimulator.simulateWork(job.getPayload());
            
            Duration executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());

            JobResult jobResult = JobResult.success(job, result, executionTime);
            metricsService.recordJobCompletion(ExecutionMode.SEQUENTIAL, executionTime, true);

            log.info("Sequential execution completed: jobId={}, duration={}ms", 
                    job.getId(), executionTime.toMillis());

            return CompletableFuture.completedFuture(jobResult);

        } catch (Exception e) {
            Duration executionTime = Duration.between(startTime, Instant.now());
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());

            JobResult jobResult = JobResult.failure(job, e.getMessage(), executionTime);
            metricsService.recordJobCompletion(ExecutionMode.SEQUENTIAL, executionTime, false);

            log.error("Sequential execution failed: jobId={}, error={}", job.getId(), e.getMessage());

            return CompletableFuture.completedFuture(jobResult);

        } finally {
            activeCount.decrementAndGet();
        }
    }

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.SEQUENTIAL;
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }
}

