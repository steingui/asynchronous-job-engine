package com.jobengine.controller.dto;

/**
 * System metrics DTO containing JVM and runtime information.
 *
 * <p>These metrics are collected from the Micrometer registry,
 * which automatically gathers JVM metrics via Spring Boot Actuator.</p>
 *
 * @param heapUsedMb          current heap memory usage in megabytes
 * @param heapMaxMb           maximum heap memory in megabytes
 * @param heapUsagePercent    heap usage as a percentage (0-100)
 * @param cpuUsagePercent     process CPU usage as a percentage (0-100)
 * @param availableProcessors number of available CPU cores
 * @param liveThreads         current number of live threads
 * @param peakThreads         peak number of threads since JVM start
 *
 * @author gsk
 */
public record SystemMetrics(
        long heapUsedMb,
        long heapMaxMb,
        double heapUsagePercent,
        double cpuUsagePercent,
        int availableProcessors,
        int liveThreads,
        int peakThreads
) {}

