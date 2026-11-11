package com.solar.sales.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An account, created by registering (email + password) or on first OAuth sign-in. The email is the identity
 * across sign-in methods; it counts as verified once Google/GitHub confirm it (or an operator configured it).
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Stored lower-cased; null for accounts registered with a mobile number. */
    @Column(unique = true, length = 150)
    private String email;

    /** E.164 (+919876543210); verified by OTP. Null for email/OAuth accounts. */
    @Column(unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.PENDING;

    /** What they asked for when signing up (USER or AGENT); the admin decides the actual role. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", length = 20)
    private Role requestedRole;

    @Column(nullable = false)
    private boolean enabled = true;

    /** BCrypt hash; null for accounts that only sign in with Google/GitHub. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified;

    /** Incremented on every password change; sessions started under an older version are signed out. */
    @Column(name = "password_version", nullable = false, columnDefinition = "integer default 0")
    private int passwordVersion;

    /** Method used for the most recent sign-in: "google", "github", "password" or "phone". */
    @Column(length = 20)
    private String provider;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String fullName, String email, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    /** A mobile-number account, created after the OTP was verified; waits for an admin to choose the role. */
    public static AppUser withPhone(String fullName, String phone) {
        AppUser user = new AppUser(fullName, null, Role.PENDING);
        user.phone = phone;
        return user;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void recordSignIn(String provider, String fullName, String avatarUrl) {
        this.provider = provider;
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName.trim();
        }
        this.avatarUrl = avatarUrl;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void recordPhoneSignIn() {
        this.provider = "phone";
        this.lastLoginAt = LocalDateTime.now();
    }

    public void recordPasswordSignIn() {
        this.provider = "password";
        this.lastLoginAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Role getRequestedRole() { return requestedRole; }
    /** Only USER or AGENT can be requested; anything else is ignored. */
    public void setRequestedRole(Role requested) {
        this.requestedRole = requested != null && requested.requestable() ? requested : null;
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPasswordHash() { return passwordHash; }
    /** Initial password of a new account (no sessions to end yet). */
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    /** Change or remove the password of an existing account; signs out all of its sessions. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordVersion++;
    }
    public int getPasswordVersion() { return passwordVersion; }
    public boolean hasPassword() { return passwordHash != null; }
    public boolean isEmailVerified() { return emailVerified; }
    public void markEmailVerified() { this.emailVerified = true; }
    public String getProvider() { return provider; }
    public String getAvatarUrl() { return avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
