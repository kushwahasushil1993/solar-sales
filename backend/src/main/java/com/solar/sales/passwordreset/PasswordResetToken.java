package com.solar.sales.passwordreset;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A "forgot password" link. Only the SHA-256 of the secret in the link is stored. */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_reset_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_reset_user", columnList = "user_id")
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Set when the link was used or replaced by a newer one. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PasswordResetToken() {
    }

    PasswordResetToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    void markUsed() {
        if (usedAt == null) {
            usedAt = LocalDateTime.now();
        }
    }

    Long getUserId() { return userId; }
    LocalDateTime getCreatedAt() { return createdAt; }
}
