package com.devtrack.release.dto;

import com.devtrack.release.entity.DeploymentOutcome;
import com.devtrack.release.entity.ReleaseStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public final class ReleaseDtos {
    private ReleaseDtos() {}

    public record UserResponse(
        UUID id,
        String username,
        String email,
        String role
    ) {}

    public record ReleaseRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotBlank(message = "Version is required")
        @Size(max = 50)
        String version,

        String description
    ) {}

    public record ReleaseResponse(
        UUID id,
        String version,
        String description,
        UUID projectId,
        String projectName,
        ReleaseStage stage,
        String status,
        UserResponse creator,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record TransitionStageRequest(
        @NotNull(message = "Stage is required")
        ReleaseStage stage
    ) {}

    public record DeploymentRequest(
        @NotBlank(message = "Environment is required")
        @Size(max = 50)
        String environment,

        @NotNull(message = "Outcome is required")
        DeploymentOutcome outcome,

        String notes
    ) {}

    public record DeploymentHistoryResponse(
        UUID id,
        UUID releaseId,
        String releaseVersion,
        String environment,
        LocalDateTime deployedAt,
        DeploymentOutcome outcome,
        UserResponse deployedBy,
        String notes
    ) {}
}
