package com.devtrack.dto;

import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record SuccessRateResponse(
        UUID projectId,
        String projectName,
        long totalDeployments,
        long successfulDeployments,
        double successRate // e.g. 85.5%
    ) {}

    public record ReleaseFrequencyResponse(
        UUID projectId,
        String projectName,
        long totalReleases,
        Map<String, Long> stageBreakdown
    ) {}

    public record DeveloperActivityResponse(
        UUID developerId,
        String username,
        String email,
        long projectsCount,
        long releasesCreatedCount,
        long deploymentsTriggeredCount
    ) {}
}
