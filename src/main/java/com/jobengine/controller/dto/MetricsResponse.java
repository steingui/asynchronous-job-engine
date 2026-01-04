package com.jobengine.controller.dto;

import com.jobengine.service.MetricsService.ModeStats;

import java.util.Map;

/**
 * Response DTO for metrics comparison.
 *
 * @param modeStats statistics for each execution mode
 * @param summary   overall summary
 */
public record MetricsResponse(
        Map<String, ModeStats> modeStats,
        Summary summary
) {

    /**
     * Creates a metrics response from mode statistics.
     *
     * @param stats the mode statistics
     * @return metrics response
     */
    public static MetricsResponse from(Map<String, ModeStats> stats) {
        long totalCompleted = stats.values().stream()
                .mapToLong(ModeStats::completedCount)
                .sum();
        long totalFailed = stats.values().stream()
                .mapToLong(ModeStats::failedCount)
                .sum();
        int totalActive = stats.values().stream()
                .mapToInt(ModeStats::activeCount)
                .sum();

        String fastestMode = stats.entrySet().stream()
                .filter(e -> e.getValue().totalExecutions() > 0)
                .min((a, b) -> Double.compare(
                        a.getValue().avgExecutionTimeMs(),
                        b.getValue().avgExecutionTimeMs()))
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return new MetricsResponse(
                stats,
                new Summary(totalCompleted, totalFailed, totalActive, fastestMode)
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

