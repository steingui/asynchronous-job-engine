package com.jobengine.service;

import com.jobengine.controller.dto.SystemMetrics;
import com.jobengine.model.ExecutionMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects and exposes metrics for job execution.
 *
 * <p>This service integrates with Micrometer to expose metrics that can be
 * scraped by Prometheus and visualized in Grafana.</p>
 *
 * <h2>Metrics Collected</h2>
 * <ul>
 *   <li><b>job.execution.time:</b> Histogram of execution times by mode</li>
 *   <li><b>job.completed.total:</b> Counter of completed jobs by mode</li>
 *   <li><b>job.failed.total:</b> Counter of failed jobs by mode</li>
 *   <li><b>job.active:</b> Gauge of currently active jobs by mode</li>
 *   <li><b>job.thread_pool.active:</b> Gauge of active threads in pool</li>
 *   <li><b>job.thread_pool.queue_size:</b> Gauge of queued tasks</li>
 * </ul>
 *
 * <h2>Accessing Metrics</h2>
 * <ul>
 *   <li>Prometheus: GET /actuator/prometheus</li>
 *   <li>JSON: GET /actuator/metrics/job.execution.time</li>
 * </ul>
 *
 * @author gsk
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final MeterRegistry meterRegistry;
    private final Map<ExecutionMode, Timer> executionTimers;
    private final Map<ExecutionMode, Counter> completedCounters;
    private final Map<ExecutionMode, Counter> failedCounters;
    private final Map<ExecutionMode, AtomicInteger> activeGauges;

    /**
     * Constructs a MetricsService with the required dependencies.
     *
     * @param meterRegistry        the Micrometer registry for metrics
     * @param threadPoolExecutor   the thread pool to monitor
     */
    public MetricsService(MeterRegistry meterRegistry, ThreadPoolExecutor threadPoolExecutor) {
        this.meterRegistry = meterRegistry;
        this.executionTimers = new EnumMap<>(ExecutionMode.class);
        this.completedCounters = new EnumMap<>(ExecutionMode.class);
        this.failedCounters = new EnumMap<>(ExecutionMode.class);
        this.activeGauges = new EnumMap<>(ExecutionMode.class);

        initializeMetrics(threadPoolExecutor);
        log.info("MetricsService initialized with Micrometer registry");
    }

    private void initializeMetrics(ThreadPoolExecutor threadPoolExecutor) {
        // Initialize per-mode metrics
        for (ExecutionMode mode : ExecutionMode.values()) {
            String modeTag = mode.name().toLowerCase();

            executionTimers.put(mode, Timer.builder("job.execution.time")
                    .tag("mode", modeTag)
                    .description("Time taken to execute jobs")
                    .register(meterRegistry));

            completedCounters.put(mode, Counter.builder("job.completed.total")
                    .tag("mode", modeTag)
                    .description("Total number of completed jobs")
                    .register(meterRegistry));

            failedCounters.put(mode, Counter.builder("job.failed.total")
                    .tag("mode", modeTag)
                    .description("Total number of failed jobs")
                    .register(meterRegistry));

            AtomicInteger activeGauge = new AtomicInteger(0);
            activeGauges.put(mode, activeGauge);
            meterRegistry.gauge("job.active", 
                    io.micrometer.core.instrument.Tags.of("mode", modeTag), 
                    activeGauge);
        }

        // Thread pool specific metrics
        meterRegistry.gauge("job.thread_pool.active", threadPoolExecutor, ThreadPoolExecutor::getActiveCount);
        meterRegistry.gauge("job.thread_pool.pool_size", threadPoolExecutor, ThreadPoolExecutor::getPoolSize);
        meterRegistry.gauge("job.thread_pool.queue_size", threadPoolExecutor, e -> e.getQueue().size());
        meterRegistry.gauge("job.thread_pool.completed", threadPoolExecutor, ThreadPoolExecutor::getCompletedTaskCount);
    }

    /**
     * Records the completion of a job execution.
     *
     * @param mode          the execution mode used
     * @param executionTime time taken to execute
     * @param success       true if job completed successfully
     */
    public void recordJobCompletion(ExecutionMode mode, Duration executionTime, boolean success) {
        executionTimers.get(mode).record(executionTime);

        if (success) {
            completedCounters.get(mode).increment();
        } else {
            failedCounters.get(mode).increment();
        }

        log.debug("Recorded job metrics: mode={}, duration={}ms, success={}",
                mode, executionTime.toMillis(), success);
    }

    /**
     * Increments the active job counter for a mode.
     *
     * @param mode the execution mode
     */
    public void incrementActive(ExecutionMode mode) {
        activeGauges.get(mode).incrementAndGet();
    }

    /**
     * Decrements the active job counter for a mode.
     *
     * @param mode the execution mode
     */
    public void decrementActive(ExecutionMode mode) {
        activeGauges.get(mode).decrementAndGet();
    }

    /**
     * Resets all job execution metrics.
     *
     * <p>Clears and recreates all timers and counters, enabling fresh stress tests
     * without restarting the server. Micrometer doesn't support direct reset,
     * so we remove and re-register each meter.</p>
     */
    public void resetMetrics() {
        for (ExecutionMode mode : ExecutionMode.values()) {
            var modeTag = mode.name().toLowerCase();

            // Remove old meters
            meterRegistry.remove(executionTimers.get(mode));
            meterRegistry.remove(completedCounters.get(mode));
            meterRegistry.remove(failedCounters.get(mode));

            // Recreate meters
            executionTimers.put(mode, Timer.builder("job.execution.time")
                    .tag("mode", modeTag)
                    .description("Time taken to execute jobs")
                    .register(meterRegistry));

            completedCounters.put(mode, Counter.builder("job.completed.total")
                    .tag("mode", modeTag)
                    .description("Total number of completed jobs")
                    .register(meterRegistry));

            failedCounters.put(mode, Counter.builder("job.failed.total")
                    .tag("mode", modeTag)
                    .description("Total number of failed jobs")
                    .register(meterRegistry));

            // Reset active gauge
            activeGauges.get(mode).set(0);
        }

        log.info("All metrics reset");
    }

    /**
     * Returns statistics for all execution modes.
     *
     * @return map of mode to statistics
     */
    public Map<String, ModeStats> getStats() {
        var stats = new java.util.HashMap<String, ModeStats>();

        for (ExecutionMode mode : ExecutionMode.values()) {
            var timer = executionTimers.get(mode);
            stats.put(mode.name(), new ModeStats(
                    (long) completedCounters.get(mode).count(),
                    (long) failedCounters.get(mode).count(),
                    activeGauges.get(mode).get(),
                    timer.count() > 0 ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0,
                    timer.count() > 0 ? timer.max(java.util.concurrent.TimeUnit.MILLISECONDS) : 0,
                    timer.count(),
                    MODE_DESCRIPTIONS.get(mode)
            ));
        }

        return stats;
    }

    /**
     * Returns current system metrics from the JVM.
     *
     * <p>Reads metrics already collected by Spring Boot Actuator/Micrometer:</p>
     * <ul>
     *   <li>jvm.memory.used/max - Heap memory usage</li>
     *   <li>process.cpu.usage - Process CPU utilization</li>
     *   <li>jvm.threads.live/peak - Thread counts</li>
     * </ul>
     *
     * @return system metrics snapshot
     */
    public SystemMetrics getSystemMetrics() {
        var heapUsed = (long) getGaugeValue("jvm.memory.used", "area", "heap");
        var heapMax = (long) getGaugeValue("jvm.memory.max", "area", "heap");
        var cpuUsage = getGaugeValue("process.cpu.usage") * 100;
        var liveThreads = (int) getGaugeValue("jvm.threads.live");
        var peakThreads = (int) getGaugeValue("jvm.threads.peak");

        var heapUsedMb = heapUsed / (1024 * 1024);
        var heapMaxMb = heapMax / (1024 * 1024);
        var heapUsagePercent = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;

        return new SystemMetrics(
                heapUsedMb,
                heapMaxMb,
                heapUsagePercent,
                cpuUsage,
                Runtime.getRuntime().availableProcessors(),
                liveThreads,
                peakThreads
        );
    }

    /**
     * Helper method to read gauge values from the meter registry.
     *
     * @param name the metric name
     * @param tags optional tag key-value pairs (key1, value1, key2, value2, ...)
     * @return the gauge value, or 0 if not found
     */
    private double getGaugeValue(String name, String... tags) {
        var search = meterRegistry.find(name);
        if (tags.length >= 2) {
            search = search.tag(tags[0], tags[1]);
        }
        Gauge gauge = search.gauge();
        return gauge != null ? gauge.value() : 0;
    }

    /**
     * Statistics for a single execution mode.
     *
     * @param completedCount     total completed jobs
     * @param failedCount        total failed jobs
     * @param activeCount        currently active jobs
     * @param avgExecutionTimeMs average execution time in milliseconds
     * @param maxExecutionTimeMs maximum execution time in milliseconds
     * @param totalExecutions    total number of executions
     * @param description        technical description of the execution mode
     */
    public record ModeStats(
            long completedCount,
            long failedCount,
            int activeCount,
            double avgExecutionTimeMs,
            double maxExecutionTimeMs,
            long totalExecutions,
            String description
    ) {}

    private static final Map<ExecutionMode, String> MODE_DESCRIPTIONS = Map.of(
            ExecutionMode.SEQUENTIAL,
            "Executes jobs synchronously in the caller's thread (HTTP thread). " +
            "Stack: Uses caller's stack frame (~few KB per call). " +
            "Heap: Minimal - only Job and JobResult objects. " +
            "GC: Low pressure, short-lived objects in Young Gen. " +
            "Blocking: YES - blocks HTTP thread until completion. " +
            "Parallelism: NONE - one job at a time. " +
            "Best for: Simple workloads, debugging, when ordering matters.",

            ExecutionMode.THREAD_POOL,
            "Executes jobs in a fixed pool of platform (OS) threads. " +
            "Stack: Each thread has ~1MB native stack (16 threads = ~16MB reserved). " +
            "Heap: Thread objects, Runnable tasks, work queue consume heap. " +
            "GC: More threads = more GC roots to scan, possible longer pauses. " +
            "Context Switching: OS scheduler switches threads (~1-10μs per switch). " +
            "Parallelism: Limited to pool size (default: CPU cores). " +
            "Best for: CPU-bound tasks, controlled concurrency, mixed workloads.",

            ExecutionMode.ASYNC,
            "Executes jobs using Virtual Threads (Java 21+ Project Loom). " +
            "Stack: Stored on heap (~few KB), grows as needed. Millions possible. " +
            "Heap: Virtual thread stacks are GC'd when thread completes. " +
            "Carrier Threads: Virtual threads run on platform thread pool (≈ CPU cores). " +
            "Blocking: When blocked on I/O, virtual thread unmounts, freeing carrier. " +
            "Continuation: Stack saved as heap object, resumed on any carrier. " +
            "Parallelism: Massive - thousands/millions concurrent with minimal overhead. " +
            "Best for: I/O-bound tasks, high-concurrency, microservices."
    );
}

