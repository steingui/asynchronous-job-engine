package com.jobengine.controller.dto;

import com.jobengine.model.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting a new job.
 *
 * @param name          descriptive name for the job (required)
 * @param payload       data to be processed (required)
 * @param executionMode strategy for executing the job (required)
 */
public record JobSubmitRequest(
        @NotBlank(message = "Job name is required")
        @Size(max = 255, message = "Job name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Payload is required")
        @Size(max = 10000, message = "Payload must not exceed 10000 characters")
        String payload,

        @NotNull(message = "Execution mode is required")
        ExecutionMode executionMode
) {}

