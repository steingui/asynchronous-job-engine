package com.jobengine.controller.dto;

import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;
import com.jobengine.model.JobStatus;

import java.time.Instant;

/**
 * Response DTO for job information.
 *
 * @param id            unique job identifier
 * @param name          job name
 * @param status        current job status
 * @param executionMode execution strategy used
 * @param createdAt     when the job was created
 * @param startedAt     when execution started (null if pending)
 * @param completedAt   when execution completed (null if not finished)
 * @param result        result details (null if not completed)
 */
public record JobResponse(
        String id,
        String name,
        JobStatus status,
        ExecutionMode executionMode,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        ResultDetails result
) {

    /**
     * Creates a response from a Job entity.
     *
     * @param job the job entity
     * @return job response DTO
     */
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getStatus(),
                job.getExecutionMode(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                null
        );
    }

    /**
     * Creates a response from a Job entity with result details.
     *
     * @param job    the job entity
     * @param result the job result
     * @return job response DTO with result
     */
    public static JobResponse from(Job job, JobResult result) {
        ResultDetails details = null;
        if (result != null) {
            details = new ResultDetails(
                    result.success(),
                    result.output(),
                    result.errorMessage(),
                    result.executionTime().toMillis(),
                    result.threadName(),
                    result.threadId(),
                    result.isVirtualThread()
            );
        }
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getStatus(),
                job.getExecutionMode(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                details
        );
    }

    /**
     * Result details for completed jobs.
     *
     * @param success         whether the job succeeded
     * @param output          result output (null if failed)
     * @param errorMessage    error message (null if succeeded)
     * @param executionTimeMs execution time in milliseconds
     * @param threadName      name of the executing thread
     * @param threadId        ID of the executing thread
     * @param virtualThread   whether executed on a virtual thread
     */
    public record ResultDetails(
            boolean success,
            String output,
            String errorMessage,
            long executionTimeMs,
            String threadName,
            long threadId,
            boolean virtualThread
    ) {}
}

