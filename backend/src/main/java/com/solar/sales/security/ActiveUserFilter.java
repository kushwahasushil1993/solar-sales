package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-reads the signed-in account on every request, so an admin's role change applies immediately, and a
 * disabled account or one whose password changed loses its sessions at once. Not a bean on purpose: it must only run inside the security chain.
 */
class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserRepository users;

    ActiveUserFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser current) {
            // gone, disabled, or password changed since this session started → signed out
            Optional<AppUser> account = users.findById(current.id())
                    .filter(AppUser::isEnabled)
                    .filter(u -> u.getPasswordVersion() == current.passwordVersion());
            if (account.isEmpty()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            } else {
                AuthUser refreshed = current.refreshedFrom(account.get());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth instanceof OAuth2AuthenticationToken token
                        ? new OAuth2AuthenticationToken(refreshed, refreshed.getAuthorities(),
                                token.getAuthorizedClientRegistrationId())
                        : UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
