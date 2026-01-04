package com.jobengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the job engine.
 *
 * <p>These properties are loaded from application.yml under the "job-engine" prefix.</p>
 *
 * @author gsk
 */
@ConfigurationProperties(prefix = "job-engine")
@Validated
public class JobEngineProperties {

    private final ThreadPoolConfig threadPool;
    private final AsyncConfig async;
    private final IoSimulationConfig ioSimulation;

    public JobEngineProperties(ThreadPoolConfig threadPool, AsyncConfig async, IoSimulationConfig ioSimulation) {
        this.threadPool = threadPool != null ? threadPool : new ThreadPoolConfig(4, 16, 100, 60);
        this.async = async != null ? async : new AsyncConfig(300, true);
        this.ioSimulation = ioSimulation != null ? ioSimulation : new IoSimulationConfig(50, 500);
    }

    public ThreadPoolConfig getThreadPool() {
        return threadPool;
    }

    public AsyncConfig getAsync() {
        return async;
    }

    public IoSimulationConfig getIoSimulation() {
        return ioSimulation;
    }

    /**
     * Thread pool configuration for THREAD_POOL execution mode.
     *
     * @param coreSize         minimum number of threads to keep alive
     * @param maxSize          maximum number of threads allowed
     * @param queueCapacity    size of the work queue
     * @param keepAliveSeconds time to keep idle threads alive
     */
    public record ThreadPoolConfig(
            @Min(1) @Max(100) int coreSize,
            @Min(1) @Max(500) int maxSize,
            @Min(1) @Max(10000) int queueCapacity,
            @Positive int keepAliveSeconds
    ) {
        public ThreadPoolConfig {
            if (maxSize < coreSize) {
                throw new IllegalArgumentException("maxSize must be >= coreSize");
            }
        }
    }

    /**
     * Async execution configuration.
     *
     * @param timeoutSeconds    maximum time to wait for job completion
     * @param useVirtualThreads whether to use virtual threads (Java 21+)
     */
    public record AsyncConfig(
            @Positive int timeoutSeconds,
            boolean useVirtualThreads
    ) {}

    /**
     * I/O simulation configuration for testing.
     *
     * @param minLatencyMs minimum simulated latency in milliseconds
     * @param maxLatencyMs maximum simulated latency in milliseconds
     */
    public record IoSimulationConfig(
            @Min(0) int minLatencyMs,
            @Min(0) int maxLatencyMs
    ) {
        public IoSimulationConfig {
            if (maxLatencyMs < minLatencyMs) {
                throw new IllegalArgumentException("maxLatencyMs must be >= minLatencyMs");
            }
        }
    }
}

