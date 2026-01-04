# Asynchronous Job Engine

A Spring Boot application for processing jobs asynchronously with multiple execution modes.

## Overview

This project provides a job processing engine designed to strengthen understanding of:
- JVM internals (GC, heap vs stack)
- Concurrency (thread pools, async)
- Performance profiling with Java Flight Recorder

## Features

- **Three Execution Modes:**
  - **Sequential** - Simple executor for single-threaded processing
  - **Thread-based** - ThreadPoolExecutor for concurrent processing
  - **Async** - CompletableFuture / Virtual Threads for non-blocking I/O

- **REST API** for job submission, status tracking, and result retrieval
- **Performance Metrics** for each execution mode
- **Configurable** thread pools, timeouts, and I/O simulation

## Tech Stack

- Java 21 (LTS)
- Spring Boot 3.4.1
- Gradle 8.12
- Micrometer 1.14.x (metrics)
- Logback with Logstash Encoder 8.0 (structured logging)

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle 8.x (or use the wrapper)

### Install Java 21

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Or using SDKMAN
curl -s "https://get.sdkman.io" | bash
sdk install java 21-tem
```

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/jobs` | Submit a new job |
| GET | `/api/jobs/{id}` | Get job details |
| GET | `/api/jobs/{id}/status` | Get job status |
| GET | `/api/jobs/{id}/results` | Get job results |
| GET | `/api/jobs` | List all jobs |
| POST | `/api/jobs/batch` | Submit batch for load testing |
| GET | `/api/metrics/compare` | Compare mode performance |
| GET | `/api/metrics/active` | Get active job counts |
| DELETE | `/api/jobs` | Clear all jobs |

### Example: Submit a Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-job",
    "payload": "sample data to process",
    "executionMode": "ASYNC"
  }'
```

### Example: Run Load Test

```bash
# Submit 100 jobs for each mode
curl -X POST http://localhost:8080/api/jobs/batch \
  -H "Content-Type: application/json" \
  -d '{"count": 100}'

# Wait for completion, then compare
curl http://localhost:8080/api/metrics/compare
```

## Execution Modes Explained

### SEQUENTIAL
- Jobs run one at a time in the caller's thread
- **Best for:** Simple workloads, debugging, guaranteed ordering
- **JVM Impact:** Minimal - single stack frame, low GC pressure
- **Trade-off:** No parallelism, blocks caller

### THREAD_POOL
- Jobs run in parallel using a ThreadPoolExecutor
- **Best for:** CPU-bound tasks, controlled parallelism
- **JVM Impact:** Each thread uses ~1MB stack, context switching overhead
- **Trade-off:** Limited by pool size, blocked threads waste resources

### ASYNC (Virtual Threads)
- Jobs run using Java 21 Virtual Threads
- **Best for:** I/O-bound tasks, high concurrency
- **JVM Impact:** Lightweight (~few KB per thread), heap-based stacks
- **Trade-off:** Not faster for CPU-bound work

## Metrics

Access metrics at:
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### Key Metrics

| Metric | Description |
|--------|-------------|
| `job.execution.time` | Execution time histogram by mode |
| `job.completed.total` | Counter of completed jobs by mode |
| `job.failed.total` | Counter of failed jobs by mode |
| `job.active` | Gauge of active jobs by mode |
| `job.thread_pool.active` | Active threads in the thread pool |
| `job.thread_pool.queue_size` | Tasks waiting in queue |

## Performance Profiling

### Java Flight Recorder

Run with JFR enabled:

```bash
./gradlew bootRun --args='--spring.profiles.active=default' \
  -Dorg.gradle.jvmargs='-XX:StartFlightRecording=duration=60s,filename=recording.jfr'
```

Or attach to running process:

```bash
jcmd <pid> JFR.start duration=60s filename=recording.jfr
```

Analyze with JDK Mission Control:

```bash
jmc recording.jfr
```

### Monitor Virtual Thread Pinning

```bash
java -Djdk.tracePinnedThreads=full -jar build/libs/asynchronous-job-engine.jar
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
job-engine:
  thread-pool:
    core-size: 4      # Minimum threads
    max-size: 16      # Maximum threads
    queue-capacity: 100
    keep-alive-seconds: 60
  
  async:
    timeout-seconds: 300
    use-virtual-threads: true  # Set false to use cached thread pool
  
  io-simulation:
    min-latency-ms: 50   # Simulate I/O delay
    max-latency-ms: 500
```

## Performance Report Guide

After running load tests, the `/api/metrics/compare` endpoint returns:

```json
{
  "modeStats": {
    "SEQUENTIAL": {
      "completedCount": 100,
      "failedCount": 0,
      "activeCount": 0,
      "avgExecutionTimeMs": 275.5,
      "maxExecutionTimeMs": 498.0,
      "totalExecutions": 100
    },
    "THREAD_POOL": {
      "completedCount": 100,
      "failedCount": 0,
      "activeCount": 0,
      "avgExecutionTimeMs": 273.2,
      "maxExecutionTimeMs": 495.0,
      "totalExecutions": 100
    },
    "ASYNC": {
      "completedCount": 100,
      "failedCount": 0,
      "activeCount": 0,
      "avgExecutionTimeMs": 271.8,
      "maxExecutionTimeMs": 492.0,
      "totalExecutions": 100
    }
  },
  "summary": {
    "totalCompleted": 300,
    "totalFailed": 0,
    "totalActive": 0,
    "fastestMode": "ASYNC"
  }
}
```

### Interpreting Results

| Metric | What it tells you |
|--------|-------------------|
| `avgExecutionTimeMs` | Average per-job time. Lower is better. |
| `maxExecutionTimeMs` | Worst-case latency. Important for SLAs. |
| `totalExecutions` | Sample size. More = more reliable stats. |
| `fastestMode` | Which mode had lowest avg time. |

### Expected Behavior

For **I/O-bound workloads** (like this simulation):
- **SEQUENTIAL:** Total time ≈ N × avg_latency (serial execution)
- **THREAD_POOL:** Total time ≈ N × avg_latency / pool_size
- **ASYNC:** Total time ≈ avg_latency (all run concurrently)

**Virtual threads shine when:**
- Many concurrent blocking operations
- I/O-heavy workloads (HTTP, database, file)
- You need >1000 concurrent tasks

**Thread pool is better when:**
- CPU-bound calculations
- You need predictable resource usage
- Legacy libraries with thread-local state

## Project Structure

```
src/main/java/com/jobengine/
├── Application.java          # Entry point
├── config/
│   ├── ExecutorConfig.java   # Thread pool beans
│   └── JobEngineProperties.java
├── model/
│   ├── Job.java
│   ├── JobStatus.java
│   ├── JobResult.java
│   └── ExecutionMode.java
├── executor/
│   ├── JobExecutor.java      # Interface
│   ├── SequentialJobExecutor.java
│   ├── ThreadPoolJobExecutor.java
│   └── AsyncJobExecutor.java
├── service/
│   ├── JobService.java
│   ├── IOSimulator.java
│   └── MetricsService.java
├── controller/
│   ├── JobController.java
│   └── dto/
└── exception/
    └── GlobalExceptionHandler.java
```

## License

MIT
