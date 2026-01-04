package com.jobengine.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a unit of work to be processed by the job engine.
 *
 * <p>A job encapsulates:</p>
 * <ul>
 *   <li>Unique identifier for tracking</li>
 *   <li>Descriptive name for logging/monitoring</li>
 *   <li>Payload data to process</li>
 *   <li>Execution mode determining the processing strategy</li>
 *   <li>Current status in the job lifecycle</li>
 *   <li>Timestamps for auditing and metrics</li>
 * </ul>
 *
 * @author gsk
 */
public class Job {

    private final String id;
    private final String name;
    private final String payload;
    private final ExecutionMode executionMode;
    private volatile JobStatus status;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;

    /**
     * Creates a new job with the specified parameters.
     *
     * @param name          descriptive name for the job
     * @param payload       data to be processed
     * @param executionMode strategy for executing this job
     */
    public Job(String name, String payload, ExecutionMode executionMode) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.payload = payload;
        this.executionMode = executionMode;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPayload() {
        return payload;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", executionMode=" + executionMode +
                ", status=" + status +
                '}';
    }
}

