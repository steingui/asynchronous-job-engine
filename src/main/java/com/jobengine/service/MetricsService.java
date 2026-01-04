package com.jobengine.service;

import com.jobengine.model.ExecutionMode;
import io.micrometer.core.instrument.Counter;
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
     * Returns statistics for all execution modes.
     *
     * @return map of mode to statistics
     */
    public Map<String, ModeStats> getStats() {
        Map<String, ModeStats> stats = new java.util.HashMap<>();

        for (ExecutionMode mode : ExecutionMode.values()) {
            Timer timer = executionTimers.get(mode);
            stats.put(mode.name(), new ModeStats(
                    (long) completedCounters.get(mode).count(),
                    (long) failedCounters.get(mode).count(),
                    activeGauges.get(mode).get(),
                    timer.count() > 0 ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0,
                    timer.count() > 0 ? timer.max(java.util.concurrent.TimeUnit.MILLISECONDS) : 0,
                    timer.count()
            ));
        }

        return stats;
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
     */
    public record ModeStats(
            long completedCount,
            long failedCount,
            int activeCount,
            double avgExecutionTimeMs,
            double maxExecutionTimeMs,
            long totalExecutions
    ) {}
}

