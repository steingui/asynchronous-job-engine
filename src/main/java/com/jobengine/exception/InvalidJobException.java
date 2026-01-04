package com.jobengine.exception;

/**
 * Exception thrown when a job is invalid or null.
 *
 * <p>This exception is part of the sealed {@link JobEngineException} hierarchy
 * and is thrown when job validation fails (e.g., null job passed to executor).</p>
 *
 * @author gsk
 */
public final class InvalidJobException extends JobEngineException {

    /**
     * Creates a new InvalidJobException with the specified message.
     *
     * @param message the error message describing the validation failure
     */
    public InvalidJobException(String message) {
        super(message);
    }
}

