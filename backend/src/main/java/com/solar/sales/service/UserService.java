package com.solar.sales.service;

import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.exception.BadRequestException;
import com.solar.sales.exception.EmailTakenException;
import com.solar.sales.exception.ForbiddenOperationException;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.task.InboxService;
import com.solar.sales.task.InboxType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when the email is unknown, so a failed login takes the same time either way. */
    private final String dummyHash;

    private final InboxService inbox;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, InboxService inbox) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.inbox = inbox;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-password");
    }

    /** What an OAuth provider tells us about the person signing in. */
    public record OAuthProfile(String provider, String email, boolean emailVerified, String name, String avatarUrl) {
    }

    /** Self-registration with email + password: waits for an admin to choose the role. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        AppUser user = new AppUser(request.fullName().trim(), email, Role.PENDING);
        user.setRequestedRole(request.requestedRole());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        AppUser saved = users.save(user);
        announcePending(saved);
        return UserResponse.from(saved);
    }

    /** Email + password sign-in; used by the password AuthenticationProvider. */
    @Transactional
    public AppUser passwordSignIn(String rawEmail, String password) {
        AppUser user = users.findByEmail(normalize(rawEmail == null ? "" : rawEmail)).orElse(null);
        if (user == null || !user.hasPassword()) {
            passwordEncoder.matches(password, dummyHash);
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("This account is disabled. Contact an administrator.");
        }
        user.recordPasswordSignIn();
        return user;
    }

    /** Finds or creates the account for a verified email. New accounts wait for an admin to choose the role. */
    @Transactional
    public AppUser signIn(OAuthProfile profile) {
        if (profile.email() == null || profile.email().isBlank() || !profile.emailVerified()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified",
                    "Your " + profile.provider() + " account has no verified email address", null));
        }
        String email = normalize(profile.email());
        String name = profile.name() == null || profile.name().isBlank() ? email.substring(0, email.indexOf('@')) : profile.name();
        AppUser user = users.findByEmail(email).orElseGet(() -> {
            AppUser created = users.save(new AppUser(name, email, Role.PENDING));
            announcePending(created);
            return created;
        });
        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled",
                    "This account is disabled", null));
        }
        if (!user.isEmailVerified()) {
            // The provider just proved who owns this email. A password set before that could have been chosen by
            // someone else who registered the address first, so it is removed (account pre-hijacking defence).
            if (user.hasPassword()) {
                user.changePassword(null);
                log.info("Removed unverified password from {} after verified {} sign-in", email, profile.provider());
            }
            user.markEmailVerified();
        }
        user.recordSignIn(profile.provider(), name, profile.avatarUrl());
        return user;
    }


    /** Tells admins (bell) that a new account needs a role. */
    public void announcePending(AppUser user) {
        String wants = user.getRequestedRole() == Role.AGENT ? "an installation agent"
                : user.getRequestedRole() == Role.USER ? "the sales team" : "the team";
        String contact = user.getEmail() != null ? user.getEmail() : user.getPhone();
        inbox.notifyAdmins(InboxType.ACCOUNT_PENDING, "New account waiting for approval",
                user.getFullName() + " (" + contact + ") signed up and wants to join " + wants
                        + ". Choose their role on the Users page.", null, user.getFullName());
    }

    // ---------------------------------------------------------------- first admin (new installs)

    /** True only while the database has no admin at all (a fresh install). */
    @Transactional(readOnly = true)
    public boolean setupRequired() {
        return users.countByRole(Role.ADMIN) == 0;
    }

    /** Creates the first administrator. Refused as soon as any admin exists. */
    @Transactional
    public synchronized UserResponse createFirstAdmin(RegisterRequest request) {
        if (!setupRequired()) {
            throw new ForbiddenOperationException("Setup is already complete. Sign in instead.");
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        AppUser admin = new AppUser(request.fullName().trim(), email, Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        log.info("Created the first administrator account {}", email);
        return UserResponse.from(users.save(admin));
    }

    // ---------------------------------------------------------------- passwords

    /** Any signed-in user: set or change their own password (current password required if one exists). */
    @Transactional
    public void changeOwnPassword(AuthUser current, String currentPassword, String newPassword) {
        AppUser user = find(current.id());
        requireEmailForPassword(user);
        if (user.hasPassword() && (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new BadRequestException("currentPassword", "Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(newPassword)); // signs out every session, including this one
    }

    /** Admin: set a new password for another user (e.g. they forgot it). */
    @Transactional
    public UserResponse resetPassword(Long id, String newPassword, AuthUser current) {
        if (id.equals(current.id())) {
            throw new ForbiddenOperationException("Change your own password under My account");
        }
        AppUser user = find(id);
        requireEmailForPassword(user);
        user.changePassword(passwordEncoder.encode(newPassword)); // the user's sessions end
        log.info("{} reset the password of user {}", current.email(), user.getId());
        return UserResponse.from(user);
    }

    private static void requireEmailForPassword(AppUser user) {
        if (user.getEmail() == null) {
            throw new BadRequestException("newPassword",
                    "This account signs in with a mobile number. Add an email address before setting a password.");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser current) {
        return UserResponse.from(find(current.id()));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse updateRole(Long id, Role role, AuthUser current) {
        AppUser user = find(id);
        if (user.getId().equals(current.id()) && role != Role.ADMIN) {
            throw new ForbiddenOperationException("You cannot remove your own admin role");
        }
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN) {
            ensureAnotherAdmin();
        }
        user.setRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled, AuthUser current) {
        AppUser user = find(id);
        if (user.getId().equals(current.id()) && !enabled) {
            throw new ForbiddenOperationException("You cannot disable your own account");
        }
        if (!enabled && user.getRole() == Role.ADMIN && user.isEnabled()) {
            ensureAnotherAdmin();
        }
        user.setEnabled(enabled);
        return UserResponse.from(user);
    }

    private void ensureAnotherAdmin() {
        if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ForbiddenOperationException("At least one active admin is required");
        }
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
