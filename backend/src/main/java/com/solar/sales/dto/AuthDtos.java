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

    public record UserResponse(Long id, String fullName, String email, String phone, Role role, Role requestedRole,
                               boolean enabled, String provider, boolean passwordLogin, boolean emailVerified,
                               String avatarUrl, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                    u.getRequestedRole(), u.isEnabled(), u.getProvider(), u.hasPassword(), u.isEmailVerified(),
                    u.getAvatarUrl(), u.getCreatedAt(), u.getLastLoginAt());
        }
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
            String password,

            /** Optional: USER (sales team) or AGENT (installer). Only a request; an admin approves the role. */
            Role requestedRole
    ) {
        public RegisterRequest(String fullName, String email, String password) {
            this(fullName, email, password, null);
        }
    }

    /** A configured sign-in option; the browser navigates to {@code loginUrl} to start the OAuth flow. */
    public record ProviderInfo(String id, String name, String loginUrl) {
    }

    public record OtpRequest(@NotBlank(message = "Mobile number is required") String phone) {
    }

    /** Reply to a correct code for a number with no account yet: finish with POST /api/auth/otp/register. */
    public record RegistrationRequired(String status, String phone, String maskedPhone) {
        public RegistrationRequired(String phone, String maskedPhone) {
            this("REGISTRATION_REQUIRED", phone, maskedPhone);
        }
    }

    /** {@code phone} is the normalised number to send back with the code. */
    public record OtpSent(String phone, String maskedPhone, long expiresInSeconds, long resendInSeconds) {
    }

    public record SetupStatus(boolean setupRequired) {
    }

    public record PasswordChange(
            String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
            String newPassword
    ) {
    }

    public record PasswordReset(
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
            String newPassword
    ) {
    }

    public record RoleUpdate(@NotNull(message = "Role is required") Role role) {
    }

    public record EnabledUpdate(boolean enabled) {
    }
}
