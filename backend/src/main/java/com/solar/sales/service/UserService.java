package com.solar.sales.service;

import com.solar.sales.dto.AuthDtos.AuthResponse;
import com.solar.sales.dto.AuthDtos.LoginRequest;
import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.exception.EmailTakenException;
import com.solar.sales.exception.ForbiddenOperationException;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Self-registration always creates a USER; only an admin can promote someone. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        AppUser user = new AppUser(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), Role.USER);
        user.setLastLoginAt(LocalDateTime.now());
        return tokenFor(users.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isEnabled()) {
            throw new DisabledException("Account disabled");
        }
        user.setLastLoginAt(LocalDateTime.now());
        return tokenFor(user);
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

    /** Creates the configured admin on startup if that email is not registered yet. */
    @Transactional
    public boolean ensureAdmin(String fullName, String email, String rawPassword) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            return false;
        }
        users.save(new AppUser(fullName, normalized, passwordEncoder.encode(rawPassword), Role.ADMIN));
        return true;
    }

    private void ensureAnotherAdmin() {
        if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ForbiddenOperationException("At least one active admin is required");
        }
    }

    private AuthResponse tokenFor(AppUser user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(),
                LocalDateTime.ofInstant(token.expiresAt(), ZoneId.systemDefault()), UserResponse.from(user));
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
