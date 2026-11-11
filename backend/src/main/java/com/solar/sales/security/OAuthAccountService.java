package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.service.UserService;
import com.solar.sales.service.UserService.OAuthProfile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Turns a Google or GitHub profile into an {@link AppUser} and the session's {@link AuthUser}. */
@Service
public class OAuthAccountService {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final UserService userService;
    private final OidcUserService oidcDelegate = new OidcUserService();
    private final DefaultOAuth2UserService oauth2Delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.create();

    public OAuthAccountService(UserService userService) {
        this.userService = userService;
    }

    /** Google (OpenID Connect). */
    public OidcUser loadOidcUser(OidcUserRequest request) {
        OidcUser google = oidcDelegate.loadUser(request);
        AppUser user = userService.signIn(new OAuthProfile("google", google.getEmail(),
                Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName(), google.getPicture()));
        return AuthUser.of(user, google.getAttributes(), google.getIdToken(), google.getUserInfo());
    }

    /** GitHub (plain OAuth2). */
    public OAuth2User loadOAuth2User(OAuth2UserRequest request) {
        OAuth2User github = oauth2Delegate.loadUser(request);
        String email = primaryVerifiedGithubEmail(request.getAccessToken().getTokenValue());
        String name = github.getAttribute("name") != null ? github.getAttribute("name") : github.getAttribute("login");
        AppUser user = userService.signIn(new OAuthProfile("github", email, email != null, name,
                github.getAttribute("avatar_url")));
        return AuthUser.of(user, github.getAttributes(), null, null);
    }

    private String primaryVerifiedGithubEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get().uri(GITHUB_EMAILS_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (emails == null) {
                return null;
            }
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("provider_error",
                    "Could not read your email address from GitHub", null), e);
        }
    }
}
