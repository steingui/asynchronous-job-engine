package com.jobengine.controller.dto;

import com.jobengine.service.MetricsService.ModeStats;

import java.util.Map;

/**
 * Response DTO for metrics comparison.
 *
 * @param modeStats statistics for each execution mode
 * @param summary   overall summary
 * @param system    JVM and system metrics
 */
public record MetricsResponse(
        Map<String, ModeStats> modeStats,
        Summary summary,
        SystemMetrics system
) {

    /**
     * Creates a metrics response from mode statistics and system metrics.
     *
     * @param stats  the mode statistics
     * @param system the system metrics
     * @return metrics response
     */
    public static MetricsResponse from(Map<String, ModeStats> stats, SystemMetrics system) {
        var totalCompleted = stats.values().stream()
                .mapToLong(ModeStats::completedCount)
                .sum();
        var totalFailed = stats.values().stream()
                .mapToLong(ModeStats::failedCount)
                .sum();
        var totalActive = stats.values().stream()
                .mapToInt(ModeStats::activeCount)
                .sum();

        var fastestMode = stats.entrySet().stream()
                .filter(e -> e.getValue().totalExecutions() > 0)
                .min((a, b) -> Double.compare(
                        a.getValue().avgExecutionTimeMs(),
                        b.getValue().avgExecutionTimeMs()))
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return new MetricsResponse(
                stats,
                new Summary(totalCompleted, totalFailed, totalActive, fastestMode),
                system
        );
    }

    /**
     * Summary statistics across all modes.
     *
     * @param totalCompleted total jobs completed
     * @param totalFailed    total jobs failed
     * @param totalActive    total jobs currently active
     * @param fastestMode    mode with lowest avg execution time
     */
    public record Summary(
            long totalCompleted,
            long totalFailed,
            int totalActive,
            String fastestMode
    ) {}
}

