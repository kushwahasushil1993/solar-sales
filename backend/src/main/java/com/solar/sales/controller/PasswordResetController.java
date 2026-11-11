package com.solar.sales.controller;

import com.solar.sales.passwordreset.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@Tag(name = "Auth", description = "Registration, sign-in options and the current user")
@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {

    private final PasswordResetService resetService;
    private final String publicUrl;

    public PasswordResetController(PasswordResetService resetService, @Value("${app.public-url:}") String publicUrl) {
        this.resetService = resetService;
        this.publicUrl = publicUrl;
    }

    public record ForgotRequest(@NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email) {
    }

    public record ResetRequest(
            @NotBlank(message = "The reset link is incomplete") String token,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
            String newPassword
    ) {
    }

    @Operation(summary = "Email a password reset link (public)",
            description = "Always answers 202 with the same message, whether or not the email has an account.")
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest request) {
        resetService.requestReset(request.email(), appBaseUrl());
        return Map.of("message", "If an account exists for that email, we've sent a link to reset the password.");
    }

    @Operation(summary = "Check a reset link before showing the form (public)")
    @GetMapping("/reset")
    public Map<String, Object> check(@RequestParam String token) {
        return resetService.check(token)
                .<Map<String, Object>>map(email -> Map.of("valid", true, "email", email))
                .orElseGet(() -> Map.of("valid", false));
    }

    @Operation(summary = "Set a new password with a reset link (public); signs out all sessions of the account")
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) {
        resetService.resetPassword(request.token(), request.newPassword());
    }

    /** Where the React app lives: app.public-url, or the origin of this request (Vite proxy / Nginx Host header). */
    private String appBaseUrl() {
        if (!publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
