package com.devtrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 100, message = "Project name must be between 2 and 100 characters")
        String name,

        String description
    ) {}

    public record ProjectResponse(
        UUID id,
        String name,
        String description,
        AuthDtos.UserResponse owner,
        Set<AuthDtos.UserResponse> members,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
