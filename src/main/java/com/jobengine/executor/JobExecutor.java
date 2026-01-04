package com.jobengine.executor;

import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for job execution strategies.
 *
 * <p>This interface defines the contract for executing jobs in different modes.
 * Each implementation provides a different execution strategy with distinct
 * performance characteristics and trade-offs.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>All implementations must be thread-safe</li>
 *   <li>Implementations should handle exceptions gracefully</li>
 *   <li>Metrics should be recorded for observability</li>
 *   <li>Logging should use structured format without sensitive data</li>
 * </ul>
 *
 * @author gsk
 * @see SequentialJobExecutor
 * @see ThreadPoolJobExecutor
 * @see AsyncJobExecutor
 */
public interface JobExecutor {

    /**
     * Executes the given job and returns the result asynchronously.
     *
     * <p>The returned CompletableFuture will complete with a JobResult
     * containing either the success output or failure information.</p>
     *
     * @param job the job to execute (must not be null)
     * @return a CompletableFuture that completes with the job result
     * @throws IllegalArgumentException if job is null
     */
    CompletableFuture<JobResult> execute(Job job);

    /**
     * Returns the execution mode this executor handles.
     *
     * @return the execution mode
     */
    ExecutionMode getMode();

    /**
     * Returns the number of currently active tasks.
     *
     * @return count of active tasks
     */
    int getActiveCount();
}

