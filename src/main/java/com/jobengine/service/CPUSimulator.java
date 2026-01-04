package com.jobengine.service;

import com.jobengine.config.JobEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates CPU-bound operations by calculating prime numbers.
 *
 * <p>This service demonstrates the difference between CPU-bound and I/O-bound workloads.
 * CPU-bound work keeps the processor busy with calculations, while I/O-bound work
 * waits for external resources.</p>
 *
 * <h2>CPU-bound vs I/O-bound</h2>
 * <table border="1">
 *   <caption>Comparison of workload types</caption>
 *   <tr><th>Aspect</th><th>CPU-bound</th><th>I/O-bound</th></tr>
 *   <tr><td>Example</td><td>Prime calculation</td><td>Database query</td></tr>
 *   <tr><td>Bottleneck</td><td>CPU cycles</td><td>External resource</td></tr>
 *   <tr><td>Thread behavior</td><td>Runs continuously</td><td>Waits/blocks</td></tr>
 *   <tr><td>Best mode</td><td>THREAD_POOL</td><td>ASYNC (Virtual)</td></tr>
 * </table>
 *
 * <h2>Impact on Execution Modes</h2>
 * <ul>
 *   <li><b>SEQUENTIAL:</b> CPU work blocks the HTTP thread entirely</li>
 *   <li><b>THREAD_POOL:</b> Parallel execution on multiple cores - best for CPU work</li>
 *   <li><b>ASYNC:</b> Virtual threads don't help - CPU still needs real cores</li>
 * </ul>
 *
 * @author gsk
 */
@Service
public class CPUSimulator {

    private static final Logger log = LoggerFactory.getLogger(CPUSimulator.class);

    private final boolean enabled;
    private final int minPrimeLimit;
    private final int maxPrimeLimit;

    /**
     * Constructs a CPUSimulator with the configured settings.
     *
     * @param properties the job engine configuration properties
     */
    public CPUSimulator(JobEngineProperties properties) {
        var config = properties.getCpuSimulation();
        this.enabled = config.enabled();
        this.minPrimeLimit = config.minPrimeLimit();
        this.maxPrimeLimit = config.maxPrimeLimit();

        log.info("CPUSimulator initialized: enabled={}, primeLimit={}–{}",
                enabled, minPrimeLimit, maxPrimeLimit);
    }

    /**
     * Generates a random limit for prime calculation.
     *
     * <p>This value should be generated once per job execution and reused
     * across retries to ensure consistent CPU work.</p>
     *
     * @return random value between minPrimeLimit and maxPrimeLimit
     */
    public int generateRandomLimit() {
        if (minPrimeLimit == maxPrimeLimit) {
            return minPrimeLimit;
        }
        return ThreadLocalRandom.current().nextInt(minPrimeLimit, maxPrimeLimit + 1);
    }

    /**
     * Counts prime numbers up to the specified limit.
     *
     * <p>This is a CPU-intensive operation that demonstrates CPU-bound work.
     * The algorithm uses trial division with O(√n) complexity per number.</p>
     *
     * <h3>Performance Reference</h3>
     * <ul>
     *   <li>limit = 10,000 → ~1ms</li>
     *   <li>limit = 100,000 → ~5-10ms</li>
     *   <li>limit = 1,000,000 → ~50-100ms</li>
     * </ul>
     *
     * @param limit the upper bound for prime search
     * @return the count of prime numbers found
     */
    public long countPrimesUpTo(int limit) {
        if (!enabled) {
            log.debug("CPU simulation disabled, skipping prime calculation");
            return 0;
        }

        var startTime = System.nanoTime();
        long count = 0;

        for (int n = 2; n <= limit; n++) {
            if (isPrime(n)) {
                count++;
            }
        }

        var durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
        log.debug("CPU work completed: limit={}, primesFound={}, duration={:.2f}ms",
                limit, count, durationMs);

        return count;
    }

    /**
     * Checks if a number is prime using trial division.
     *
     * <p>Time complexity: O(√n)</p>
     *
     * @param n the number to check
     * @return true if n is prime, false otherwise
     */
    private boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    /**
     * Returns whether CPU simulation is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
}

