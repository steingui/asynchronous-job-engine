package com.jobengine.controller.dto;

import com.jobengine.model.ExecutionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for submitting a batch of jobs.
 *
 * @param count         number of jobs to create (1-1000)
 * @param executionMode mode to use (null = all modes)
 */
public record BatchSubmitRequest(
        @Min(value = 1, message = "Count must be at least 1")
        @Max(value = 1000, message = "Count must not exceed 1000")
        int count,

        ExecutionMode executionMode
) {
    public BatchSubmitRequest {
        if (count <= 0) {
            count = 10; // default
        }
    }
}

