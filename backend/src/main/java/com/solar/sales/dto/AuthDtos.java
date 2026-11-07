package com.solar.sales.dto;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be at most 100 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Email is not valid")
            @Size(max = 150)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    public record UserResponse(Long id, String fullName, String email, Role role, boolean enabled,
                               LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record AuthResponse(String token, LocalDateTime expiresAt, UserResponse user) {
    }

    public record RoleUpdate(@NotNull(message = "Role is required") Role role) {
    }

    public record EnabledUpdate(boolean enabled) {
    }
}
