package com.solar.sales.passwordreset;

import com.solar.sales.entity.AppUser;
import com.solar.sales.exception.BadRequestException;
import com.solar.sales.notification.CompanyProperties;
import com.solar.sales.notification.EmailMessage;
import com.solar.sales.notification.EmailSender;
import com.solar.sales.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * "Forgot password": emails a single-use link (30 minutes by default). Requesting a link never changes or blocks
 * the current password; setting the new one does, and signs out every session of the account.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PER_HOUR = 3;
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final PasswordResetTokenRepository tokens;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final EmailSender emailSender;
    private final ITemplateEngine templates;
    private final CompanyProperties company;
    private final Duration ttl;

    public PasswordResetService(PasswordResetTokenRepository tokens, AppUserRepository users, PasswordEncoder encoder,
                                EmailSender emailSender, ITemplateEngine templates, CompanyProperties company,
                                @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.tokens = tokens;
        this.users = users;
        this.encoder = encoder;
        this.emailSender = emailSender;
        this.templates = templates;
        this.company = company;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Sends a reset link if an enabled account has this email. Says nothing about whether it does, and silently
     * ignores requests over the rate limit, so the response is always the same.
     */
    @Transactional
    public void requestReset(String rawEmail, String appBaseUrl) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> account = users.findByEmail(email).filter(AppUser::isEnabled);
        if (account.isEmpty()) {
            log.info("Password reset requested for an unknown or disabled email");
            return;
        }
        AppUser user = account.get();
        LocalDateTime now = LocalDateTime.now();
        boolean tooSoon = tokens.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .map(t -> t.getCreatedAt().plus(COOLDOWN).isAfter(now)).orElse(false);
        if (tooSoon || tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_PER_HOUR) {
            log.info("Password reset for user {} skipped (rate limit)", user.getId());
            return;
        }

        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetToken::markUsed); // newest link only
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        tokens.save(new PasswordResetToken(user.getId(), sha256(rawToken), now.plus(ttl)));

        String link = appBaseUrl + "/reset-password?token=" + rawToken;
        try {
            emailSender.send(render(user, link));
        } catch (Exception e) {
            log.warn("Sending the password reset email to user {} failed: {}", user.getId(), e.getMessage());
        }
    }

    /** The masked email the link belongs to, if the link is still usable. */
    @Transactional(readOnly = true)
    public Optional<String> check(String rawToken) {
        return usableToken(rawToken)
                .flatMap(t -> users.findById(t.getUserId()))
                .filter(AppUser::isEnabled)
                .map(u -> mask(u.getEmail()));
    }

    /** Sets the new password, uses up the link, and signs out all sessions of the account. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = usableToken(rawToken).orElseThrow(PasswordResetService::invalidLink);
        AppUser user = users.findById(token.getUserId()).filter(AppUser::isEnabled)
                .orElseThrow(PasswordResetService::invalidLink);
        user.changePassword(encoder.encode(newPassword));
        user.markEmailVerified(); // they received the email, so they own the address
        token.markUsed();
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetToken::markUsed);
        log.info("Password reset completed for user {}", user.getId());
    }

    private Optional<PasswordResetToken> usableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(sha256(rawToken.trim())).filter(t -> t.isUsable(LocalDateTime.now()));
    }

    private static BadRequestException invalidLink() {
        return new BadRequestException("token", "This reset link is invalid or has expired. Request a new one.");
    }

    private EmailMessage render(AppUser user, String link) {
        String firstName = user.getFullName().trim().split("\\s+")[0];
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("company", company);
        ctx.setVariable("greeting", "Hi " + firstName + ",");
        ctx.setVariable("link", link);
        ctx.setVariable("minutes", ttl.toMinutes());
        String subject = "Reset your " + company.name() + " password";
        String text = String.join("\n",
                "Hi " + firstName + ",", "",
                "We received a request to reset your " + company.name() + " password.",
                "Open this link to choose a new one (valid for " + ttl.toMinutes() + " minutes, single use):", "",
                link, "",
                "If you didn't ask for this, ignore this email. Your password stays the same.");
        return new EmailMessage(user.getEmail(), subject, templates.process("email/password-reset", ctx), text);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "priya.sharma@example.com" → "p••••••••••a@example.com" */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return "•••" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "•".repeat(at - 2) + email.charAt(at - 1) + email.substring(at);
    }
}
