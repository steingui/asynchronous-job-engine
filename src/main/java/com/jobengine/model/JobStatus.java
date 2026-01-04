package com.jobengine.model;

/**
 * Represents the lifecycle status of a job in the processing engine.
 *
 * <p>Jobs transition through these states:</p>
 * <pre>
 * PENDING → RUNNING → COMPLETED
 *                  ↘ FAILED
 * </pre>
 *
 * @author gsk
 */
public enum JobStatus {
    
    /**
     * Job has been submitted but not yet started.
     */
    PENDING,
    
    /**
     * Job is currently being processed by an executor.
     */
    RUNNING,
    
    /**
     * Job completed successfully.
     */
    COMPLETED,
    
    /**
     * Job failed during execution.
     */
    FAILED
}

