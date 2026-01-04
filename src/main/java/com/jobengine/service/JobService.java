package com.jobengine.service;

import com.jobengine.executor.AsyncJobExecutor;
import com.jobengine.executor.JobExecutor;
import com.jobengine.executor.SequentialJobExecutor;
import com.jobengine.executor.ThreadPoolJobExecutor;
import com.jobengine.model.ExecutionMode;
import com.jobengine.model.Job;
import com.jobengine.model.JobResult;
import com.jobengine.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Central service for job management and execution orchestration.
 *
 * <p>This service handles:</p>
 * <ul>
 *   <li>Job submission and storage</li>
 *   <li>Routing jobs to the appropriate executor based on mode</li>
 *   <li>Tracking job status and results</li>
 *   <li>Batch job submission for load testing</li>
 * </ul>
 *
 * @author gsk
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final Map<String, Job> jobStorage;
    private final Map<String, JobResult> resultStorage;
    private final Map<ExecutionMode, JobExecutor> executors;

    public JobService(SequentialJobExecutor sequentialExecutor,
                      ThreadPoolJobExecutor threadPoolExecutor,
                      AsyncJobExecutor asyncExecutor) {
        this.jobStorage = new ConcurrentHashMap<>();
        this.resultStorage = new ConcurrentHashMap<>();
        this.executors = Map.of(
                ExecutionMode.SEQUENTIAL, sequentialExecutor,
                ExecutionMode.THREAD_POOL, threadPoolExecutor,
                ExecutionMode.ASYNC, asyncExecutor
        );

        log.info("JobService initialized with {} execution modes", executors.size());
    }

    /**
     * Submits a job for execution.
     *
     * @param name          job name for identification
     * @param payload       data to process
     * @param executionMode how to execute the job
     * @return the created job
     */
    public Job submitJob(String name, String payload, ExecutionMode executionMode) {
        Job job = new Job(name, payload, executionMode);
        jobStorage.put(job.getId(), job);

        log.info("Job submitted: id={}, name={}, mode={}", job.getId(), name, executionMode);

        JobExecutor executor = executors.get(executionMode);
        CompletableFuture<JobResult> future = executor.execute(job);

        future.thenAccept(result -> {
            resultStorage.put(job.getId(), result);
            log.debug("Job result stored: id={}, success={}", job.getId(), result.success());
        });

        return job;
    }

    /**
     * Submits a batch of jobs for load testing.
     *
     * @param count         number of jobs to create
     * @param executionMode mode to use for all jobs
     * @return list of created jobs
     */
    public List<Job> submitBatch(int count, ExecutionMode executionMode) {
        log.info("Submitting batch: count={}, mode={}", count, executionMode);

        List<Job> jobs = IntStream.range(0, count)
                .mapToObj(i -> submitJob("batch-job-" + i, "payload-" + i, executionMode))
                .toList();

        log.info("Batch submitted: {} jobs created", jobs.size());
        return jobs;
    }

    /**
     * Submits a batch of jobs across all execution modes.
     *
     * @param countPerMode number of jobs per mode
     * @return map of mode to list of jobs
     */
    public Map<ExecutionMode, List<Job>> submitBatchAllModes(int countPerMode) {
        log.info("Submitting batch for all modes: {} jobs per mode", countPerMode);

        Map<ExecutionMode, List<Job>> result = new java.util.EnumMap<>(ExecutionMode.class);

        for (ExecutionMode mode : ExecutionMode.values()) {
            result.put(mode, submitBatch(countPerMode, mode));
        }

        return result;
    }

    /**
     * Retrieves a job by its ID.
     *
     * @param jobId the job identifier
     * @return the job if found
     */
    public Optional<Job> getJob(String jobId) {
        return Optional.ofNullable(jobStorage.get(jobId));
    }

    /**
     * Retrieves the status of a job.
     *
     * @param jobId the job identifier
     * @return the job status if found
     */
    public Optional<JobStatus> getJobStatus(String jobId) {
        return getJob(jobId).map(Job::getStatus);
    }

    /**
     * Retrieves the result of a completed job.
     *
     * @param jobId the job identifier
     * @return the job result if available
     */
    public Optional<JobResult> getJobResult(String jobId) {
        return Optional.ofNullable(resultStorage.get(jobId));
    }

    /**
     * Returns all stored jobs.
     *
     * @return collection of all jobs
     */
    public Collection<Job> getAllJobs() {
        return new ArrayList<>(jobStorage.values());
    }

    /**
     * Returns jobs filtered by status.
     *
     * @param status the status to filter by
     * @return list of matching jobs
     */
    public List<Job> getJobsByStatus(JobStatus status) {
        return jobStorage.values().stream()
                .filter(job -> job.getStatus() == status)
                .toList();
    }

    /**
     * Returns jobs filtered by execution mode.
     *
     * @param mode the execution mode to filter by
     * @return list of matching jobs
     */
    public List<Job> getJobsByMode(ExecutionMode mode) {
        return jobStorage.values().stream()
                .filter(job -> job.getExecutionMode() == mode)
                .toList();
    }

    /**
     * Returns the number of active jobs for each executor.
     *
     * @return map of mode to active count
     */
    public Map<ExecutionMode, Integer> getActiveJobCounts() {
        Map<ExecutionMode, Integer> counts = new java.util.EnumMap<>(ExecutionMode.class);
        executors.forEach((mode, executor) -> counts.put(mode, executor.getActiveCount()));
        return counts;
    }

    /**
     * Clears all stored jobs and results.
     * Useful for testing or resetting state.
     */
    public void clearAll() {
        int jobCount = jobStorage.size();
        int resultCount = resultStorage.size();
        jobStorage.clear();
        resultStorage.clear();
        log.info("Cleared storage: {} jobs, {} results", jobCount, resultCount);
    }
}

