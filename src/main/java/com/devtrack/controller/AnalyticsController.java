package com.devtrack.controller;

import com.devtrack.dto.AnalyticsDtos.*;
import com.devtrack.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/projects/{projectId}/success-rate")
    public ResponseEntity<SuccessRateResponse> getSuccessRate(@PathVariable UUID projectId) {
        SuccessRateResponse response = analyticsService.getDeploymentSuccessRate(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{projectId}/release-frequency")
    public ResponseEntity<ReleaseFrequencyResponse> getReleaseFrequency(@PathVariable UUID projectId) {
        ReleaseFrequencyResponse response = analyticsService.getReleaseFrequency(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/developers/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'RELEASE_MANAGER')")
    public ResponseEntity<List<DeveloperActivityResponse>> getDeveloperActivity() {
        List<DeveloperActivityResponse> response = analyticsService.getDeveloperActivityTrends();
        return ResponseEntity.ok(response);
    }
}
