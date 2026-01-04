package com.jobengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for job executors.
 *
 * <p>This class creates and configures the executor services used by the job engine:</p>
 * <ul>
 *   <li><b>Platform Thread Pool:</b> A bounded ThreadPoolExecutor for THREAD_POOL mode</li>
 *   <li><b>Virtual Thread Executor:</b> An unbounded executor using virtual threads for ASYNC mode</li>
 * </ul>
 *
 * <h2>JVM Considerations</h2>
 * <p><b>Platform Threads (ThreadPoolExecutor):</b></p>
 * <ul>
 *   <li>Each thread allocates ~1MB of stack space by default</li>
 *   <li>Context switching between threads has CPU overhead</li>
 *   <li>Blocked threads still consume memory resources</li>
 *   <li>Good for CPU-bound tasks where parallelism is needed</li>
 * </ul>
 *
 * <p><b>Virtual Threads (Java 21+):</b></p>
 * <ul>
 *   <li>Lightweight - stack stored on heap, grows as needed (~few KB initially)</li>
 *   <li>Managed by the JVM, not the OS - minimal context switch overhead</li>
 *   <li>Can create millions of virtual threads</li>
 *   <li>Ideal for I/O-bound tasks with many blocking operations</li>
 *   <li>GC handles cleanup when virtual threads complete</li>
 * </ul>
 *
 * @author gsk
 */
@Configuration
@EnableConfigurationProperties(JobEngineProperties.class)
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);

    private final JobEngineProperties properties;

    /**
     * Constructs an ExecutorConfig with the required properties.
     *
     * @param properties the job engine configuration properties
     */
    public ExecutorConfig(JobEngineProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a ThreadPoolExecutor for the THREAD_POOL execution mode.
     *
     * <p>This executor uses platform threads with configurable pool sizes.
     * It's suitable for CPU-bound workloads where you want controlled parallelism.</p>
     *
     * <h4>How it works:</h4>
     * <ol>
     *   <li>New tasks go to core threads (up to coreSize)</li>
     *   <li>If core threads are busy, tasks queue up (up to queueCapacity)</li>
     *   <li>If queue is full, new threads are created (up to maxSize)</li>
     *   <li>If all threads are busy and queue is full, task is rejected</li>
     * </ol>
     *
     * @return configured ThreadPoolExecutor
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor threadPoolExecutor() {
        var config = properties.getThreadPool();
        
        log.info("Creating ThreadPoolExecutor: coreSize={}, maxSize={}, queueCapacity={}, keepAlive={}s",
                config.coreSize(), config.maxSize(), config.queueCapacity(), config.keepAliveSeconds());

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.coreSize(),
                config.maxSize(),
                config.keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Allow core threads to timeout when idle
        executor.allowCoreThreadTimeOut(true);

        return executor;
    }

    /**
     * Creates an ExecutorService using virtual threads for ASYNC mode.
     *
     * <p>Virtual threads (introduced in Java 21) are lightweight threads managed
     * by the JVM rather than the OS. They're ideal for I/O-bound workloads.</p>
     *
     * <h4>Key characteristics:</h4>
     * <ul>
     *   <li>Unbounded - can create as many as needed</li>
     *   <li>Cheap to create (~1000x cheaper than platform threads)</li>
     *   <li>Automatically unmounted when blocked, freeing carrier thread</li>
     *   <li>Stack grows on heap, managed by GC</li>
     * </ul>
     *
     * @return ExecutorService using virtual threads
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        boolean useVirtual = properties.getAsync().useVirtualThreads();
        
        if (useVirtual) {
            log.info("Creating Virtual Thread Executor (Java 21+ feature)");
            return Executors.newVirtualThreadPerTaskExecutor();
        } else {
            log.info("Virtual threads disabled, using cached thread pool");
            return Executors.newCachedThreadPool();
        }
    }
}

