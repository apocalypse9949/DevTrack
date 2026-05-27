package com.devtrack.release.service;

import com.devtrack.release.dto.ReleaseDtos.*;
import com.devtrack.release.entity.*;
import com.devtrack.release.exception.BadRequestException;
import com.devtrack.release.exception.ResourceNotFoundException;
import com.devtrack.release.exception.UnauthorizedException;
import com.devtrack.release.repository.DeploymentHistoryRepository;
import com.devtrack.release.repository.ProjectRepository;
import com.devtrack.release.repository.ReleaseRepository;
import com.devtrack.release.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private WebClient.Builder webClientBuilder;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalProjectResponse(UUID id, String name) {}

    private User getOrCreateUserReplicaByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    try {
                        UserResponse response = webClientBuilder.build()
                                .get()
                                .uri("http://auth-service/api/v1/auth/users/username/" + username)
                                .retrieve()
                                .bodyToMono(UserResponse.class)
                                .block();

                        if (response == null) {
                            throw new ResourceNotFoundException("User not found in auth service: " + username);
                        }

                        User replica = User.builder()
                                .id(response.id())
                                .username(response.username())
                                .email(response.email())
                                .role(response.role())
                                .build();

                        return userRepository.save(replica);
                    } catch (Exception e) {
                        throw new ResourceNotFoundException("Failed to fetch user profile from identity service: " + username);
                    }
                });
    }

    private User getOrCreateUserReplicaById(UUID userId) {
        return userRepository.findById(userId)
                .orElseGet(() -> {
                    try {
                        UserResponse response = webClientBuilder.build()
                                .get()
                                .uri("http://auth-service/api/v1/auth/users/" + userId)
                                .retrieve()
                                .bodyToMono(UserResponse.class)
                                .block();

                        if (response == null) {
                            throw new ResourceNotFoundException("User not found in auth service with id: " + userId);
                        }

                        User replica = User.builder()
                                .id(response.id())
                                .username(response.username())
                                .email(response.email())
                                .role(response.role())
                                .build();

                        return userRepository.save(replica);
                    } catch (Exception e) {
                        throw new ResourceNotFoundException("Failed to fetch user profile from identity service with id: " + userId);
                    }
                });
    }

    private Project getOrCreateProjectReplica(UUID projectId, String currentUsername, String role) {
        // Enforce membership / ownership authorization by fetching the project details from project-service
        try {
            ExternalProjectResponse response = webClientBuilder.build()
                    .get()
                    .uri("http://project-service/api/v1/projects/" + projectId)
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(ExternalProjectResponse.class)
                    .block();

            if (response == null) {
                throw new ResourceNotFoundException("Project not found in project service with id: " + projectId);
            }

            // Sync the replica locally in release service database
            Project project = projectRepository.findById(projectId)
                    .orElseGet(() -> {
                        Project replica = Project.builder()
                                .id(response.id())
                                .name(response.name())
                                .build();
                        return projectRepository.save(replica);
                    });

            if (!response.name().equals(project.getName())) {
                project.setName(response.name());
                project = projectRepository.save(project);
            }

            return project;
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden ex) {
            throw new UnauthorizedException("You are not authorized to access this project");
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        } catch (Exception e) {
            // Local fallback if the remote service call fails but a local replica exists
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found and project service is unavailable: " + projectId));
        }
    }

    public ReleaseResponse createRelease(ReleaseRequest request, String currentUsername, String role) {
        Project project = getOrCreateProjectReplica(request.projectId(), currentUsername, role);
        User creator = getOrCreateUserReplicaByUsername(currentUsername);

        if (releaseRepository.existsByProjectIdAndVersion(request.projectId(), request.version())) {
            throw new BadRequestException("Release version '" + request.version() + "' already exists for this project");
        }

        Release release = Release.builder()
                .version(request.version())
                .description(request.description())
                .project(project)
                .stage(ReleaseStage.CODE_REVIEW)
                .status("IN_PROGRESS")
                .creator(creator)
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

    public ReleaseResponse transitionStage(UUID releaseId, TransitionStageRequest request, String currentUsername, String role) {
        Release release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with id: " + releaseId));

        Project project = getOrCreateProjectReplica(release.getProject().getId(), currentUsername, role);
        User user = getOrCreateUserReplicaByUsername(currentUsername);

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

    public DeploymentHistoryResponse deployRelease(UUID releaseId, DeploymentRequest request, String currentUsername, String role) {
        Release release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found with id: " + releaseId));

        Project project = getOrCreateProjectReplica(release.getProject().getId(), currentUsername, role);
        User user = getOrCreateUserReplicaByUsername(currentUsername);

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

    @Transactional(readOnly = true)
    public long countDeploymentsByProjectId(UUID projectId) {
        return deploymentHistoryRepository.countDeploymentsByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public long countSuccessfulDeploymentsByProjectId(UUID projectId) {
        return deploymentHistoryRepository.countSuccessfulDeploymentsByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> getReleasesList(UUID projectId) {
        return releaseRepository.findByProjectId(projectId, Pageable.unpaged()).getContent().stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countReleasesByCreator(UUID developerId) {
        User creator = getOrCreateUserReplicaById(developerId);
        return releaseRepository.countByCreator(creator);
    }

    @Transactional(readOnly = true)
    public long countDeploymentsByDeployedBy(UUID developerId) {
        User deployedBy = getOrCreateUserReplicaById(developerId);
        return deploymentHistoryRepository.countByDeployedBy(deployedBy);
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
                convertToUserResponse(release.getCreator()),
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
                convertToUserResponse(history.getDeployedBy()),
                history.getNotes()
        );
    }

    private UserResponse convertToUserResponse(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
