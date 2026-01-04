package com.jobengine.model;

import java.time.Duration;

/**
 * Contains the result of a job execution along with performance metrics.
 *
 * <p>This record captures:</p>
 * <ul>
 *   <li>The job that was executed</li>
 *   <li>Whether execution succeeded</li>
 *   <li>Output data or error message</li>
 *   <li>Performance metrics for analysis</li>
 * </ul>
 *
 * <p>Performance metrics include execution time and thread information,
 * which are useful for comparing different execution modes.</p>
 *
 * @param job             the executed job
 * @param success         true if job completed without errors
 * @param output          result data if successful, null otherwise
 * @param errorMessage    error description if failed, null otherwise
 * @param executionTime   duration of job execution
 * @param threadName      name of the thread that executed the job
 * @param threadId        ID of the thread that executed the job
 * @param isVirtualThread true if executed on a virtual thread
 *
 * @author gsk
 */
public record JobResult(
        Job job,
        boolean success,
        String output,
        String errorMessage,
        Duration executionTime,
        String threadName,
        long threadId,
        boolean isVirtualThread
) {

    /**
     * Creates a successful result.
     *
     * @param job           the executed job
     * @param output        the result output
     * @param executionTime time taken to execute
     * @return a successful JobResult
     */
    public static JobResult success(Job job, String output, Duration executionTime) {
        Thread current = Thread.currentThread();
        return new JobResult(
                job,
                true,
                output,
                null,
                executionTime,
                current.getName(),
                current.threadId(),
                current.isVirtual()
        );
    }

    /**
     * Creates a failed result.
     *
     * @param job           the executed job
     * @param errorMessage  description of the failure
     * @param executionTime time taken before failure
     * @return a failed JobResult
     */
    public static JobResult failure(Job job, String errorMessage, Duration executionTime) {
        Thread current = Thread.currentThread();
        return new JobResult(
                job,
                false,
                null,
                errorMessage,
                executionTime,
                current.getName(),
                current.threadId(),
                current.isVirtual()
        );
    }
}

