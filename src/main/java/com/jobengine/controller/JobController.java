package com.jobengine.controller;

import com.jobengine.controller.dto.BatchSubmitRequest;
import com.jobengine.controller.dto.JobResponse;
import com.jobengine.controller.dto.JobSubmitRequest;
import com.jobengine.controller.dto.MetricsResponse;
import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;
import com.jobengine.model.JobStatus;
import com.jobengine.service.JobService;
import com.jobengine.service.MetricsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for job management.
 *
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Submitting jobs for execution</li>
 *   <li>Checking job status</li>
 *   <li>Retrieving job results</li>
 *   <li>Batch job submission for load testing</li>
 *   <li>Metrics comparison across execution modes</li>
 * </ul>
 *
 * @author gsk
 */
@RestController
@RequestMapping("/api")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobService jobService;
    private final MetricsService metricsService;

    public JobController(JobService jobService, MetricsService metricsService) {
        this.jobService = jobService;
        this.metricsService = metricsService;
    }

    /**
     * Submits a new job for execution.
     *
     * @param request the job submission request
     * @return the created job
     */
    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobSubmitRequest request) {
        log.info("Submitting job: name={}, mode={}", request.name(), request.executionMode());

        Job job = jobService.submitJob(request.name(), request.payload(), request.executionMode());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(JobResponse.from(job));
    }

    /**
     * Retrieves a job by ID.
     *
     * @param id the job identifier
     * @return the job if found
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String id) {
        Optional<Job> job = jobService.getJob(id);
        Optional<JobResult> result = jobService.getJobResult(id);

        return job
                .map(j -> ResponseEntity.ok(JobResponse.from(j, result.orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves the status of a job.
     *
     * @param id the job identifier
     * @return the job status
     */
    @GetMapping("/jobs/{id}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String id) {
        return jobService.getJobStatus(id)
                .map(status -> ResponseEntity.ok(Map.<String, Object>of(
                        "jobId", id,
                        "status", status
            )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves the result of a completed job.
     *
     * @param id the job identifier
     * @return the job result if available
     */
    @GetMapping("/jobs/{id}/results")
    public ResponseEntity<JobResponse> getJobResult(@PathVariable String id) {
        Optional<Job> job = jobService.getJob(id);
        Optional<JobResult> result = jobService.getJobResult(id);

        if (job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (result.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(JobResponse.from(job.get()));
        }

        return ResponseEntity.ok(JobResponse.from(job.get(), result.get()));
    }

    /**
     * Lists all jobs.
     *
     * @param status optional filter by status
     * @param mode   optional filter by execution mode
     * @return list of jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) ExecutionMode mode) {

        List<Job> jobs;
        if (status != null) {
            jobs = jobService.getJobsByStatus(status);
        } else if (mode != null) {
            jobs = jobService.getJobsByMode(mode);
        } else {
            jobs = List.copyOf(jobService.getAllJobs());
        }

        List<JobResponse> responses = jobs.stream()
                .map(job -> {
                    Optional<JobResult> result = jobService.getJobResult(job.getId());
                    return JobResponse.from(job, result.orElse(null));
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Submits a batch of jobs for load testing.
     *
     * @param request batch parameters
     * @return summary of created jobs
     */
    @PostMapping("/jobs/batch")
    public ResponseEntity<Map<String, Object>> submitBatch(@Valid @RequestBody BatchSubmitRequest request) {
        log.info("Submitting batch: count={}, mode={}", request.count(), request.executionMode());

        if (request.executionMode() != null) {
            List<Job> jobs = jobService.submitBatch(request.count(), request.executionMode());
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Batch submitted",
                            "count", jobs.size(),
                            "mode", request.executionMode(),
                            "jobIds", jobs.stream().map(Job::getId).toList()
                    ));
        } else {
            Map<ExecutionMode, List<Job>> jobs = jobService.submitBatchAllModes(request.count());
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Batch submitted for all modes",
                            "countPerMode", request.count(),
                            "totalJobs", request.count() * ExecutionMode.values().length,
                            "modes", jobs.keySet()
                    ));
        }
    }

    /**
     * Returns performance metrics comparison across execution modes.
     *
     * <p>Includes job execution statistics per mode and JVM system metrics
     * (memory, CPU, threads).</p>
     *
     * @return metrics for each mode plus system info
     */
    @GetMapping("/metrics/compare")
    public ResponseEntity<MetricsResponse> compareMetrics() {
        var stats = metricsService.getStats();
        var system = metricsService.getSystemMetrics();
        return ResponseEntity.ok(MetricsResponse.from(stats, system));
    }

    /**
     * Returns current active job counts per executor.
     *
     * @return map of mode to active count
     */
    @GetMapping("/metrics/active")
    public ResponseEntity<Map<ExecutionMode, Integer>> getActiveJobs() {
        return ResponseEntity.ok(jobService.getActiveJobCounts());
    }

    /**
     * Clears all stored jobs and results.
     *
     * @return confirmation message
     */
    @DeleteMapping("/jobs")
    public ResponseEntity<Map<String, String>> clearJobs() {
        jobService.clearAll();
        return ResponseEntity.ok(Map.of("message", "All jobs cleared"));
    }
}

