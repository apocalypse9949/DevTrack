package com.devtrack.release.controller;

import com.devtrack.release.dto.ReleaseDtos.*;
import com.devtrack.release.service.ReleaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/releases")
public class ReleaseController {

    @Autowired
    private ReleaseService releaseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<ReleaseResponse> createRelease(
            @Valid @RequestBody ReleaseRequest request, 
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        ReleaseResponse response = releaseService.createRelease(request, currentUsername, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ReleaseResponse>> getReleases(
            @RequestParam UUID projectId, 
            Pageable pageable) {
        Page<ReleaseResponse> response = releaseService.getReleases(projectId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseResponse> getReleaseById(@PathVariable UUID id) {
        ReleaseResponse response = releaseService.getReleaseById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<ReleaseResponse> transitionStage(
            @PathVariable UUID id, 
            @Valid @RequestBody TransitionStageRequest request, 
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        ReleaseResponse response = releaseService.transitionStage(id, request, currentUsername, role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<DeploymentHistoryResponse> deployRelease(
            @PathVariable UUID id, 
            @Valid @RequestBody DeploymentRequest request, 
            Authentication authentication) {
        String currentUsername = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        DeploymentHistoryResponse response = releaseService.deployRelease(id, request, currentUsername, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<DeploymentHistoryResponse>> getDeploymentHistory(@PathVariable UUID id) {
        List<DeploymentHistoryResponse> response = releaseService.getDeploymentHistory(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/project/{projectId}/deployments-count")
    public ResponseEntity<Long> countDeploymentsByProjectId(@PathVariable UUID projectId) {
        long count = releaseService.countDeploymentsByProjectId(projectId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/analytics/project/{projectId}/deployments-success-count")
    public ResponseEntity<Long> countSuccessfulDeploymentsByProjectId(@PathVariable UUID projectId) {
        long count = releaseService.countSuccessfulDeploymentsByProjectId(projectId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/analytics/project/{projectId}/list")
    public ResponseEntity<List<ReleaseResponse>> getReleasesList(@PathVariable UUID projectId) {
        List<ReleaseResponse> response = releaseService.getReleasesList(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/developer/{developerId}/releases-count")
    public ResponseEntity<Long> countReleasesByCreator(@PathVariable UUID developerId) {
        long count = releaseService.countReleasesByCreator(developerId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/analytics/developer/{developerId}/deployments-count")
    public ResponseEntity<Long> countDeploymentsByDeployedBy(@PathVariable UUID developerId) {
        long count = releaseService.countDeploymentsByDeployedBy(developerId);
        return ResponseEntity.ok(count);
    }
}
