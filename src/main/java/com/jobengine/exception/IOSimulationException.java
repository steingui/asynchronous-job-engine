package com.jobengine.exception;

/**
 * Exception thrown when I/O simulation is interrupted or fails.
 *
 * <p>This exception is part of the sealed {@link JobEngineException} hierarchy
 * and is thrown when simulated I/O operations encounter errors.</p>
 *
 * @author gsk
 */
public final class IOSimulationException extends JobEngineException {

    /**
     * Creates a new IOSimulationException with the specified message and cause.
     *
     * @param message the error message describing the I/O failure
     * @param cause   the underlying exception (typically InterruptedException)
     */
    public IOSimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}

