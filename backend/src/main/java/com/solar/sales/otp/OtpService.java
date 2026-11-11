package com.solar.sales.otp;

import com.solar.sales.dto.AuthDtos.OtpSent;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.notification.CompanyProperties;
import com.solar.sales.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mobile-number sign-in with a 6-digit SMS code. One flow for everyone: a correct code signs in an existing
 * account; for a new number it only proves ownership, and {@link #completeRegistration} then creates the account.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OtpChallengeRepository challenges;
    private final AppUserRepository users;
    private final OtpChannel channel;
    private final com.solar.sales.service.UserService userService;
    private final String defaultCountryCode;
    private final Duration ttl;
    private final Duration cooldown;
    private final int maxPerHour;
    private final int maxGlobalPerHour;
    private final int maxAttempts;

    public OtpService(OtpChallengeRepository challenges, AppUserRepository users, OtpChannel channel,
                      com.solar.sales.service.UserService userService,
                      @Value("${app.sms.default-country-code:91}") String defaultCountryCode,
                      @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.otp.resend-cooldown-seconds:30}") long cooldownSeconds,
                      @Value("${app.otp.max-per-hour:5}") int maxPerHour,
                      @Value("${app.otp.max-global-per-hour:200}") int maxGlobalPerHour,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts) {
        this.challenges = challenges;
        this.users = users;
        this.channel = channel;
        this.userService = userService;
        this.defaultCountryCode = defaultCountryCode;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.maxPerHour = maxPerHour;
        this.maxGlobalPerHour = maxGlobalPerHour;
        this.maxAttempts = maxAttempts;
    }

    public String normalize(String rawPhone) {
        return PhoneNumbers.normalize(rawPhone, defaultCountryCode);
    }

    /** Sends a code to any valid number (registered or not), within the rate limits. */
    @Transactional
    public OtpSent requestCode(String rawPhone) {
        String phone = normalize(rawPhone);
        if (users.findByPhone(phone).filter(u -> !u.isEnabled()).isPresent()) {
            throw new OtpException(HttpStatus.FORBIDDEN, "This account is disabled. Contact an administrator.");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<OtpChallenge> latest = challenges.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone);
        if (latest.isPresent() && latest.get().getCreatedAt().plus(cooldown).isAfter(now)) {
            long wait = Math.max(1, Duration.between(now, latest.get().getCreatedAt().plus(cooldown)).toSeconds());
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait + " seconds before requesting another code.");
        }
        if (challenges.countByPhoneAndCreatedAtAfter(phone, now.minusHours(1)) >= maxPerHour) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested for this number. Try again in an hour.");
        }
        if (challenges.countByCreatedAtAfter(now.minusHours(1)) >= maxGlobalPerHour) {
            log.warn("Global OTP limit of {} per hour reached", maxGlobalPerHour);
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS, "Mobile sign-in is busy right now. Try again later.");
        }

        challenges.findByPhoneAndConsumedFalse(phone).forEach(OtpChallenge::consume); // only the newest code works
        String stored;
        try {
            stored = channel.send(phone);
        } catch (Exception e) {
            log.warn("Sending OTP SMS to {} failed: {}", PhoneNumbers.mask(phone), e.getMessage());
            throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, "We couldn't send the SMS. Please try again shortly.");
        }
        challenges.save(new OtpChallenge(phone, stored, now.plus(ttl)));
        return new OtpSent(phone, PhoneNumbers.mask(phone), ttl.toSeconds(), cooldown.toSeconds());
    }

    /** Outcome of a correct code: the account to sign in, or none yet for a new number. */
    public record Verification(String phone, AppUser user) {
        public boolean isNewNumber() {
            return user == null;
        }
    }

    /**
     * Checks the code. Failed attempts are committed even though an exception is thrown, so the attempt limit
     * cannot be bypassed.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public Verification verify(String rawPhone, String code) {
        String phone;
        try {
            phone = normalize(rawPhone);
        } catch (InvalidPhoneException e) {
            throw new BadCredentialsException(e.getMessage());
        }
        OtpChallenge challenge = challenges.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone)
                .filter(c -> !c.isConsumed())
                .orElseThrow(() -> new BadCredentialsException("No active code for this number. Request a new code."));
        if (challenge.isExpired(LocalDateTime.now())) {
            challenge.consume();
            throw new BadCredentialsException("This code has expired. Request a new one.");
        }
        String entered = code == null ? "" : code.replaceAll("\\s", "");
        if (!entered.matches("\\d{6}") || !codeMatches(phone, entered, challenge)) {
            challenge.recordFailedAttempt();
            int left = maxAttempts - challenge.getAttempts();
            if (left <= 0) {
                challenge.consume();
                throw new BadCredentialsException("Too many incorrect attempts. Request a new code.");
            }
            throw new BadCredentialsException("Incorrect code. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }
        challenge.consume();

        Optional<AppUser> account = users.findByPhone(phone);
        if (account.isEmpty()) {
            return new Verification(phone, null);
        }
        AppUser user = account.get();
        if (!user.isEnabled()) {
            throw new DisabledException("This account is disabled. Contact an administrator.");
        }
        user.recordPhoneSignIn();
        return new Verification(phone, user);
    }

    private boolean codeMatches(String phone, String code, OtpChallenge challenge) {
        try {
            return channel.check(phone, code, challenge.getCodeHash());
        } catch (Exception e) {
            // provider unreachable: not the user's fault, so it doesn't count as an attempt
            log.warn("Checking OTP for {} failed: {}", PhoneNumbers.mask(phone), e.getMessage());
            throw new AuthenticationServiceException("We couldn't check the code right now. Please try again.");
        }
    }

    /**
     * Creates the account for a number that was just verified (see {@link #verify}). Email is optional; the account
     * waits for an admin to choose its role ({@code requestedRole} is only a request).
     */
    @Transactional
    public AppUser completeRegistration(String verifiedPhone, String fullName, String rawEmail, Role requestedRole) {
        if (fullName == null || fullName.isBlank()) {
            throw new RegistrationException(HttpStatus.BAD_REQUEST, "fullName", "Full name is required");
        }
        if (fullName.trim().length() > 100) {
            throw new RegistrationException(HttpStatus.BAD_REQUEST, "fullName", "Full name must be at most 100 characters");
        }
        String email = rawEmail == null || rawEmail.isBlank() ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email != null && (email.length() > 150 || !EMAIL.matcher(email).matches())) {
            throw new RegistrationException(HttpStatus.BAD_REQUEST, "email", "Email is not valid");
        }
        if (users.existsByPhone(verifiedPhone)) {
            throw new RegistrationException(HttpStatus.CONFLICT, "phone", "This mobile number is already registered. Sign in instead.");
        }
        if (email != null && users.existsByEmail(email)) {
            throw new RegistrationException(HttpStatus.CONFLICT, "email", "An account with this email already exists.");
        }
        // the email is typed in, not verified
        AppUser user = AppUser.withPhone(fullName.trim(), verifiedPhone);
        user.setEmail(email);
        user.setRequestedRole(requestedRole);
        user.recordPhoneSignIn();
        AppUser saved = users.save(user);
        userService.announcePending(saved);
        return saved;
    }
}
