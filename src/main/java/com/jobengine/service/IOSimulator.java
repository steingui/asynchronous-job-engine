package com.jobengine.service;

import com.jobengine.config.JobEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

import com.jobengine.exception.IOSimulationException;

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

    /**
     * Constructs an IOSimulator with the configured latency settings.
     *
     * @param properties the job engine configuration properties
     */
    public IOSimulator(JobEngineProperties properties) {
        this.minLatencyMs = properties.getIoSimulation().minLatencyMs();
        this.maxLatencyMs = properties.getIoSimulation().maxLatencyMs();
        
        log.info("IOSimulator initialized: minLatency={}ms, maxLatency={}ms", minLatencyMs, maxLatencyMs);
    }

    /**
     * Simulates I/O work by sleeping for a random duration.
     *
     * <p>The sleep duration is randomly chosen between configured min and max latency.
     * This simulates the variability of real I/O operations.</p>
     *
     * @param payload the job payload to "process"
     * @return a processed result string
     */
    public String simulateWork(String payload) {
        var latency = calculateLatency();
        
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

