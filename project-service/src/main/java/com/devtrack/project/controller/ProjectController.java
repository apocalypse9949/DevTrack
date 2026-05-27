package com.devtrack.project.controller;

import com.devtrack.project.dto.ProjectDtos.*;
import com.devtrack.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request, 
            Authentication authentication) {
        ProjectResponse response = projectService.createProject(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> getProjects(Pageable pageable, Authentication authentication) {
        Page<ProjectResponse> response = projectService.getProjects(pageable, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id, Authentication authentication) {
        ProjectResponse response = projectService.getProjectById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<ProjectResponse> assignMember(
            @PathVariable UUID id, 
            @PathVariable UUID userId, 
            Authentication authentication) {
        ProjectResponse response = projectService.assignMember(id, userId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<ProjectResponse> removeMember(
            @PathVariable UUID id, 
            @PathVariable UUID userId, 
            Authentication authentication) {
        ProjectResponse response = projectService.removeMember(id, userId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id, Authentication authentication) {
        projectService.deleteProject(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analytics/user/{userId}/count")
    public ResponseEntity<Long> getProjectCountByUser(@PathVariable UUID userId) {
        long count = projectService.getProjectCountByUser(userId);
        return ResponseEntity.ok(count);
    }
}
