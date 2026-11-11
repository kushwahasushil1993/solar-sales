package com.solar.sales.security;

import com.solar.sales.dto.AuthDtos.ProviderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OAuth client registrations from app.oauth.* (GOOGLE_CLIENT_ID, GITHUB_CLIENT_ID, ...).
 * A provider is enabled only when both its client id and secret are set, so the app still starts
 * (with no sign-in options) when nothing is configured.
 */
@Component
public class OAuthProviders {

    private static final Logger log = LoggerFactory.getLogger(OAuthProviders.class);

    private final List<ClientRegistration> registrations = new ArrayList<>();

    public OAuthProviders(@Value("${app.oauth.google.client-id:}") String googleId,
                          @Value("${app.oauth.google.client-secret:}") String googleSecret,
                          @Value("${app.oauth.github.client-id:}") String githubId,
                          @Value("${app.oauth.github.client-secret:}") String githubSecret) {
        if (configured("Google", googleId, googleSecret)) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId.trim()).clientSecret(googleSecret.trim()).build());
        }
        if (configured("GitHub", githubId, githubSecret)) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubId.trim()).clientSecret(githubSecret.trim())
                    // user:email lets us read a verified email even when the profile email is private
                    .scope("read:user", "user:email")
                    .build());
        }
        if (registrations.isEmpty()) {
            log.warn("No OAuth provider configured: set GOOGLE_CLIENT_ID/SECRET and/or GITHUB_CLIENT_ID/SECRET "
                    + "(see README). Nobody can sign in until one is set.");
        }
    }

    private static boolean configured(String name, String id, String secret) {
        boolean hasId = !id.isBlank();
        boolean hasSecret = !secret.isBlank();
        if (hasId != hasSecret) {
            log.warn("{} OAuth is half-configured (need both client id and secret); it stays disabled", name);
        }
        return hasId && hasSecret;
    }

    public List<ClientRegistration> registrations() {
        return List.copyOf(registrations);
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    public List<ProviderInfo> info() {
        return registrations.stream()
                .map(r -> new ProviderInfo(r.getRegistrationId(), r.getClientName(),
                        "/oauth2/authorization/" + r.getRegistrationId()))
                .toList();
    }
}
