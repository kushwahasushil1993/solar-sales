package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.service.UserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Email + password sign-in; the session principal is the same {@link AuthUser} that OAuth sign-in produces. */
@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

    private final UserService userService;

    public PasswordAuthenticationProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        AppUser user = userService.passwordSignIn(authentication.getName(), String.valueOf(authentication.getCredentials()));
        AuthUser principal = AuthUser.of(user, Map.of(), null, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
