package com.devtrack.controller;

import com.devtrack.aspect.Auditable;
import com.devtrack.dto.ReleaseDtos.*;
import com.devtrack.service.ReleaseService;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'RELEASE_MANAGER')")
    @Auditable(action = "CREATE_RELEASE")
    public ResponseEntity<ReleaseResponse> createRelease(
            @Valid @RequestBody ReleaseRequest request, 
            Authentication authentication) {
        ReleaseResponse response = releaseService.createRelease(request, authentication.getName());
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
    @PreAuthorize("hasAnyRole('ADMIN', 'RELEASE_MANAGER')")
    @Auditable(action = "TRANSITION_RELEASE_STAGE")
    public ResponseEntity<ReleaseResponse> transitionStage(
            @PathVariable UUID id, 
            @Valid @RequestBody TransitionStageRequest request, 
            Authentication authentication) {
        ReleaseResponse response = releaseService.transitionStage(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAnyRole('ADMIN', 'RELEASE_MANAGER')")
    @Auditable(action = "DEPLOY_RELEASE")
    public ResponseEntity<DeploymentHistoryResponse> deployRelease(
            @PathVariable UUID id, 
            @Valid @RequestBody DeploymentRequest request, 
            Authentication authentication) {
        DeploymentHistoryResponse response = releaseService.deployRelease(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<DeploymentHistoryResponse>> getDeploymentHistory(@PathVariable UUID id) {
        List<DeploymentHistoryResponse> response = releaseService.getDeploymentHistory(id);
        return ResponseEntity.ok(response);
    }
}
