package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The signed-in principal stored in the session; inject with {@code @AuthenticationPrincipal AuthUser user}.
 * Implements {@link OidcUser} so it can be returned from both the Google (OIDC) and GitHub (OAuth2) user services.
 */
public final class AuthUser implements OidcUser, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String fullName;
    private final Role role;
    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;
    /** The account's password version when this session started (see AppUser#changePassword). */
    private final int passwordVersion;

    public AuthUser(Long id, String email, String fullName, Role role) {
        this(id, email, fullName, role, 0);
    }

    public AuthUser(Long id, String email, String fullName, Role role, int passwordVersion) {
        this(id, email, fullName, role, Map.of(), null, null, passwordVersion);
    }

    private AuthUser(Long id, String email, String fullName, Role role, Map<String, Object> attributes,
                     OidcIdToken idToken, OidcUserInfo userInfo, int passwordVersion) {
        this.passwordVersion = passwordVersion;
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.attributes = attributes;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    public static AuthUser of(AppUser user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                Map.copyOf(attributes), idToken, userInfo, user.getPasswordVersion());
    }

    /** Same session principal with the account's current role and name. */
    public AuthUser refreshedFrom(AppUser user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), attributes, idToken,
                userInfo, passwordVersion);
    }

    public Long id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public Role role() { return role; }
    public int passwordVersion() { return passwordVersion; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    @Override
    public Map<String, Object> getClaims() {
        return idToken != null ? idToken.getClaims() : attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }
}
