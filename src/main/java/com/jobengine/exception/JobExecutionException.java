package com.jobengine.exception;

/**
 * Exception thrown when job execution fails.
 *
 * <p>This exception is part of the sealed {@link JobEngineException} hierarchy
 * and wraps errors that occur during job processing.</p>
 *
 * @author gsk
 */
public final class JobExecutionException extends JobEngineException {

    /**
     * Creates a new JobExecutionException with the specified message and cause.
     *
     * @param message the error message describing the execution failure
     * @param cause   the underlying exception that caused the failure
     */
    public JobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

