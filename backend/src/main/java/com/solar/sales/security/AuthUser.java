package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;

/** The authenticated principal; inject with {@code @AuthenticationPrincipal AuthUser user}. */
public record AuthUser(Long id, String email, String fullName, Role role) {

    public static AuthUser from(AppUser u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
