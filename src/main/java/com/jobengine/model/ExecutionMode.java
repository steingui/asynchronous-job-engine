package com.jobengine.model;

/**
 * Defines the execution strategy for processing jobs.
 *
 * <p>Each mode has different characteristics regarding concurrency,
 * resource usage, and performance trade-offs:</p>
 *
 * <h2>SEQUENTIAL</h2>
 * <ul>
 *   <li><b>How it works:</b> Jobs execute one at a time in the calling thread</li>
 *   <li><b>JVM Impact:</b> Minimal heap usage, single stack frame per job</li>
 *   <li><b>Best for:</b> Simple workloads, debugging, guaranteed ordering</li>
 *   <li><b>Trade-offs:</b> No parallelism, blocks the caller thread</li>
 * </ul>
 *
 * <h2>THREAD_POOL</h2>
 * <ul>
 *   <li><b>How it works:</b> Jobs are submitted to a ThreadPoolExecutor</li>
 *   <li><b>JVM Impact:</b> Each thread has its own stack (~1MB default), context switching overhead</li>
 *   <li><b>Best for:</b> CPU-bound tasks, controlled parallelism</li>
 *   <li><b>Trade-offs:</b> Limited by pool size, resource-intensive for I/O-heavy workloads</li>
 * </ul>
 *
 * <h2>ASYNC</h2>
 * <ul>
 *   <li><b>How it works:</b> Uses CompletableFuture with Virtual Threads (Java 21+)</li>
 *   <li><b>JVM Impact:</b> Virtual threads are heap-allocated (~few KB), GC manages lifecycle</li>
 *   <li><b>Best for:</b> I/O-bound tasks, high concurrency scenarios</li>
 *   <li><b>Trade-offs:</b> Less efficient for CPU-bound work, requires understanding of async patterns</li>
 * </ul>
 *
 * @author gsk
 */
public enum ExecutionMode {
    
    /**
     * Sequential execution - one job at a time, blocking.
     */
    SEQUENTIAL,
    
    /**
     * Thread pool execution - parallel processing with platform threads.
     */
    THREAD_POOL,
    
    /**
     * Asynchronous execution - non-blocking with virtual threads.
     */
    ASYNC
}

