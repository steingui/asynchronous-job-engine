package com.jobengine.exception;

/**
 * Base sealed exception for all job engine errors.
 *
 * <p>This sealed class hierarchy provides type-safe error handling
 * following Java 21 best practices. All domain-specific exceptions
 * must extend this class.</p>
 *
 * @author gsk
 */
public sealed class JobEngineException extends RuntimeException
        permits InvalidJobException, JobExecutionException, IOSimulationException {

    /**
     * Creates a new JobEngineException with the specified message.
     *
     * @param message the error message
     */
    public JobEngineException(String message) {
        super(message);
    }

    /**
     * Creates a new JobEngineException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public JobEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}

