package com.devtrack.dto;

import com.devtrack.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role
    ) {}

    public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
    ) {}

    public record UserResponse(
        UUID id,
        String username,
        String email,
        Role role
    ) {}

    public record LoginResponse(
        String accessToken,
        String tokenType,
        UserResponse user
    ) {
        public LoginResponse(String accessToken, UserResponse user) {
            this(accessToken, "Bearer", user);
        }
    }
}
