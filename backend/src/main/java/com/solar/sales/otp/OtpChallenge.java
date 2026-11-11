package com.solar.sales.otp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One code sent by SMS. Only a hash of the code is stored. */
@Entity
@Table(name = "otp_challenges", indexes = @Index(name = "idx_otp_phone", columnList = "phone"))
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(String phone, String codeHash, LocalDateTime expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void consume() {
        consumed = true;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
