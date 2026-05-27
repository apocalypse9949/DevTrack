package com.devtrack.analytics.controller;

import com.devtrack.analytics.dto.AnalyticsDtos.*;
import com.devtrack.analytics.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/projects/{projectId}/success-rate")
    public ResponseEntity<SuccessRateResponse> getSuccessRate(
            @PathVariable UUID projectId,
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        SuccessRateResponse response = analyticsService.getDeploymentSuccessRate(projectId, currentUsername, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{projectId}/release-frequency")
    public ResponseEntity<ReleaseFrequencyResponse> getReleaseFrequency(
            @PathVariable UUID projectId,
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        ReleaseFrequencyResponse response = analyticsService.getReleaseFrequency(projectId, currentUsername, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/developers/activity")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<List<DeveloperActivityResponse>> getDeveloperActivity(
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        List<DeveloperActivityResponse> response = analyticsService.getDeveloperActivityTrends(currentUsername, role);
        return ResponseEntity.ok(response);
    }
}
