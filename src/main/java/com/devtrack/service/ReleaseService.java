package com.devtrack.service;

import com.devtrack.dto.ReleaseDtos.*;
import com.devtrack.entity.*;
import com.devtrack.exception.BadRequestException;
import com.devtrack.exception.ResourceNotFoundException;
import com.devtrack.exception.UnauthorizedException;
import com.devtrack.repository.DeploymentHistoryRepository;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.ReleaseRepository;
import com.devtrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReleaseService {

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeploymentHistoryRepository deploymentHistoryRepository;

    @Autowired
    private UserService userService;

    public ReleaseResponse createRelease(ReleaseRequest request, String currentUsername) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.projectId()));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        if (user.getRole() != Role.ROLE_ADMIN && 
            !project.getOwner().equals(user) && 
            !project.getMembers().contains(user)) {
            throw new UnauthorizedException("You are not authorized to create releases for this project");
        }

        if (releaseRepository.existsByProjectIdAndVersion(request.projectId(), request.version())) {
            throw new BadRequestException("Release version '" + request.version() + "' already exists for this project");
        }

        Release release = Release.builder()
                .version(request.version())
                .description(request.description())
                .project(project)
                .stage(ReleaseStage.CODE_REVIEW)
                .status("IN_PROGRESS")
                .creator(user)
                .build();

        Release savedRelease = releaseRepository.save(release);
        return convertToResponse(savedRelease);
    }

    @Transactional(readOnly = true)
    public Page<ReleaseResponse> getReleases(UUID projectId, Pageable pageable) {
        return releaseRepository.findByProjectId(projectId, pageable).map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public ReleaseResponse getReleaseById(UUID id) {
        Release release = releaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with id: " + id));
        return convertToResponse(release);
    }

    public ReleaseResponse transitionStage(UUID releaseId, TransitionStageRequest request, String currentUsername) {
        Release release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with id: " + releaseId));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Project project = release.getProject();
        if (user.getRole() != Role.ROLE_ADMIN && 
            !project.getOwner().equals(user) && 
            !project.getMembers().contains(user)) {
            throw new UnauthorizedException("You are not authorized to transition this release");
        }

        ReleaseStage currentStage = release.getStage();
        ReleaseStage newStage = request.stage();

        if (currentStage == ReleaseStage.DEPLOYED || currentStage == ReleaseStage.FAILED) {
            throw new BadRequestException("Cannot transition from final stage: " + currentStage);
        }

        release.setStage(newStage);

        if (newStage == ReleaseStage.DEPLOYED) {
            release.setStatus("COMPLETED");
            DeploymentHistory history = DeploymentHistory.builder()
                    .release(release)
                    .environment("PRODUCTION")
                    .outcome(DeploymentOutcome.SUCCESS)
                    .deployedBy(user)
                    .deployedAt(LocalDateTime.now())
                    .notes("Automated transition to DEPLOYED stage")
                    .build();
            deploymentHistoryRepository.save(history);
        } else if (newStage == ReleaseStage.FAILED) {
            release.setStatus("ABORTED");
            DeploymentHistory history = DeploymentHistory.builder()
                    .release(release)
                    .environment("PRODUCTION")
                    .outcome(DeploymentOutcome.FAILURE)
                    .deployedBy(user)
                    .deployedAt(LocalDateTime.now())
                    .notes("Automated transition to FAILED stage")
                    .build();
            deploymentHistoryRepository.save(history);
        }

        Release savedRelease = releaseRepository.save(release);
        return convertToResponse(savedRelease);
    }

    public DeploymentHistoryResponse deployRelease(UUID releaseId, DeploymentRequest request, String currentUsername) {
        Release release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with id: " + releaseId));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Project project = release.getProject();
        if (user.getRole() != Role.ROLE_ADMIN && 
            !project.getOwner().equals(user) && 
            !project.getMembers().contains(user)) {
            throw new UnauthorizedException("You are not authorized to deploy this release");
        }

        if (release.getStage() == ReleaseStage.DEPLOYED) {
            throw new BadRequestException("Release is already successfully deployed");
        }

        DeploymentOutcome outcome = request.outcome();
        if (outcome == DeploymentOutcome.SUCCESS) {
            release.setStage(ReleaseStage.DEPLOYED);
            release.setStatus("COMPLETED");
        } else {
            release.setStage(ReleaseStage.FAILED);
            release.setStatus("ABORTED");
        }
        releaseRepository.save(release);

        DeploymentHistory history = DeploymentHistory.builder()
                .release(release)
                .environment(request.environment())
                .outcome(outcome)
                .deployedBy(user)
                .deployedAt(LocalDateTime.now())
                .notes(request.notes())
                .build();

        DeploymentHistory savedHistory = deploymentHistoryRepository.save(history);
        return convertToDeploymentResponse(savedHistory);
    }

    @Transactional(readOnly = true)
    public List<DeploymentHistoryResponse> getDeploymentHistory(UUID releaseId) {
        if (!releaseRepository.existsById(releaseId)) {
            throw new ResourceNotFoundException("Release not found with id: " + releaseId);
        }
        return deploymentHistoryRepository.findByReleaseIdOrderByDeployedAtDesc(releaseId).stream()
                .map(this::convertToDeploymentResponse)
                .toList();
    }

    public ReleaseResponse convertToResponse(Release release) {
        return new ReleaseResponse(
                release.getId(),
                release.getVersion(),
                release.getDescription(),
                release.getProject().getId(),
                release.getProject().getName(),
                release.getStage(),
                release.getStatus(),
                userService.convertToResponse(release.getCreator()),
                release.getCreatedAt(),
                release.getUpdatedAt()
        );
    }

    public DeploymentHistoryResponse convertToDeploymentResponse(DeploymentHistory history) {
        return new DeploymentHistoryResponse(
                history.getId(),
                history.getRelease().getId(),
                history.getRelease().getVersion(),
                history.getEnvironment(),
                history.getDeployedAt(),
                history.getOutcome(),
                userService.convertToResponse(history.getDeployedBy()),
                history.getNotes()
        );
    }
}
