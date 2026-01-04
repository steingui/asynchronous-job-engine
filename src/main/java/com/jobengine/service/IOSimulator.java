package com.jobengine.service;

import com.jobengine.config.JobEngineProperties;
import com.jobengine.exception.IOSimulationException;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates I/O-bound operations with configurable latency.
 *
 * <p>This service is used to simulate real-world I/O operations like database queries,
 * HTTP calls, or file operations. It introduces artificial delays to test how different
 * execution modes handle blocking operations.</p>
 *
 * <h2>Why This Matters</h2>
 * <p>The behavior of different execution modes varies significantly based on workload type:</p>
 * <ul>
 *   <li><b>CPU-bound:</b> Work that keeps the CPU busy (calculations, parsing)</li>
 *   <li><b>I/O-bound:</b> Work that waits for external resources (network, disk)</li>
 * </ul>
 *
 * <p>For I/O-bound work:</p>
 * <ul>
 *   <li>Sequential: Thread waits idle during I/O - very inefficient</li>
 *   <li>Thread Pool: Threads block on I/O, limiting concurrency to pool size</li>
 *   <li>Virtual Threads: Thread unmounts during I/O, carrier thread serves others</li>
 * </ul>
 *
 * @author gsk
 */
@Service
public class IOSimulator {

    private static final Logger log = LoggerFactory.getLogger(IOSimulator.class);

    private final int minLatencyMs;
    private final int maxLatencyMs;
    private final double failureRate;
    private final double timeoutRate;
    private final int timeoutLatencyMs;

    /**
     * Constructs an IOSimulator with the configured latency and chaos settings.
     *
     * @param properties the job engine configuration properties
     */
    public IOSimulator(JobEngineProperties properties) {
        var config = properties.getIoSimulation();
        this.minLatencyMs = config.minLatencyMs();
        this.maxLatencyMs = config.maxLatencyMs();
        this.failureRate = config.failureRate();
        this.timeoutRate = config.timeoutRate();
        this.timeoutLatencyMs = config.timeoutLatencyMs();
        
        log.info("IOSimulator initialized: latency={}–{}ms, failureRate={}%, timeoutRate={}%", 
                minLatencyMs, maxLatencyMs, 
                (int)(failureRate * 100), (int)(timeoutRate * 100));
    }

    /**
     * Simulates I/O work by sleeping for a random duration.
     *
     * <p>The sleep duration is randomly chosen between configured min and max latency.
     * This simulates the variability of real I/O operations.</p>
     *
     * <p><b>Chaos mode:</b> With configured probability, this method may:
     * <ul>
     *   <li>Throw an exception (simulates network/DB failure)</li>
     *   <li>Take much longer than normal (simulates timeout)</li>
     * </ul></p>
     *
     * @param payload the job payload to "process"
     * @return a processed result string
     * @throws IOSimulationException if random failure occurs or interrupted after all retries
     */
    @Retry(name = "ioSimulator", fallbackMethod = "simulateWorkFallback")
    public String simulateWork(String payload) {
        var random = ThreadLocalRandom.current();
        
        // Chaos: random failure
        if (failureRate > 0 && random.nextDouble() < failureRate) {
            log.warn("Chaos: random failure triggered for payload={}", payload);
            throw new IOSimulationException("Random failure (chaos mode)", null);
        }
        
        // Chaos: timeout (extreme latency)
        int latency;
        if (timeoutRate > 0 && random.nextDouble() < timeoutRate) {
            latency = timeoutLatencyMs;
            log.warn("Chaos: timeout triggered, latency={}ms", latency);
        } else {
            latency = calculateLatency();
        }
        
        log.debug("Simulating I/O: latency={}ms, payloadLength={}", latency, 
                payload != null ? payload.length() : 0);

        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOSimulationException("I/O simulation interrupted", e);
        }

        return "Processed: " + (payload != null ? payload : "empty") + " [latency=" + latency + "ms]";
    }

    /**
     * Calculates a random latency within the configured range.
     *
     * @return latency in milliseconds
     */
    private int calculateLatency() {
        if (minLatencyMs == maxLatencyMs) {
            return minLatencyMs;
        }
        return ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1);
    }

    /**
     * Fallback method called when all retry attempts are exhausted.
     *
     * @param payload the original payload
     * @param ex the exception that caused the failure
     * @return never returns, always throws
     * @throws IOSimulationException wrapping the original exception
     */
    private String simulateWorkFallback(String payload, Exception ex) {
        log.error("All retry attempts exhausted for payload={}, error={}", payload, ex.getMessage());
        throw new IOSimulationException("I/O operation failed after 3 retries: " + ex.getMessage(), ex);
    }

    /**
     * Returns the configured minimum latency.
     *
     * @return minimum latency in milliseconds
     */
    public int getMinLatencyMs() {
        return minLatencyMs;
    }

    /**
     * Returns the configured maximum latency.
     *
     * @return maximum latency in milliseconds
     */
    public int getMaxLatencyMs() {
        return maxLatencyMs;
    }
}

