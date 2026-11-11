package com.solar.sales.service;

import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.service.UserService.OAuthProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solar.sales.support.TestSenders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestSenders.class)
class OAuthSignInTest {

    @Autowired UserService userService;
    @Autowired AppUserRepository users;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void firstSignInCreatesAccountWaitingForApproval() {
        AppUser user = userService.signIn(new OAuthProfile("google", "Priya@Example.com", true, "Priya Patel", "https://img/p.png"));

        assertThat(user.getEmail()).isEqualTo("priya@example.com");
        assertThat(user.getRole()).isEqualTo(Role.PENDING);
        assertThat(user.getProvider()).isEqualTo("google");
        assertThat(user.getAvatarUrl()).isEqualTo("https://img/p.png");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void sameVerifiedEmailOnAnotherProviderIsTheSameAccount() {
        AppUser viaGoogle = userService.signIn(new OAuthProfile("google", "priya@example.com", true, "Priya", null));
        AppUser viaGithub = userService.signIn(new OAuthProfile("github", "PRIYA@example.com", true, "priya-gh", null));

        assertThat(viaGithub.getId()).isEqualTo(viaGoogle.getId());
        assertThat(viaGithub.getProvider()).isEqualTo("github");
        assertThat(users.count()).isEqualTo(1);
    }


    @Test
    void unverifiedOrMissingEmailIsRejected() {
        assertThatThrownBy(() -> userService.signIn(new OAuthProfile("google", "x@example.com", false, "X", null)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo("email_not_verified");
        assertThatThrownBy(() -> userService.signIn(new OAuthProfile("github", null, false, "X", null)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(users.count()).isZero();
    }

    @Test
    void verifiedOAuthSignInRemovesAPasswordSetBeforeVerification() {
        // someone registers the victim's email first, then the real owner signs in with Google
        userService.register(new RegisterRequest("Attacker", "victim@example.com", "attacker123"));

        AppUser owner = userService.signIn(new OAuthProfile("google", "victim@example.com", true, "Victim", null));

        assertThat(owner.isEmailVerified()).isTrue();
        assertThat(owner.hasPassword()).isFalse();
        assertThatThrownBy(() -> userService.passwordSignIn("victim@example.com", "attacker123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifiedPasswordAccountKeepsPasswordOnOAuthSignIn() {
        AppUser boss = userService.signIn(new OAuthProfile("github", "boss@test.local", true, "Boss", null)); // verified
        userService.changeOwnPassword(new com.solar.sales.security.AuthUser(boss.getId(), boss.getEmail(), "Boss",
                boss.getRole()), null, "Admin1234"); // password set after verification
        userService.signIn(new OAuthProfile("google", "boss@test.local", true, "Boss", null));

        assertThat(userService.passwordSignIn("boss@test.local", "Admin1234").getEmail()).isEqualTo("boss@test.local");
    }

    @Test
    void oauthSignInNeverGrantsAdminByItself() {
        assertThat(userService.signIn(new OAuthProfile("github", "boss@test.local", true, null, null)).getRole())
                .isEqualTo(Role.PENDING);
    }


    @Test
    void disabledAccountCannotSignIn() {
        AppUser user = userService.signIn(new OAuthProfile("google", "rep@example.com", true, "Rep", null));
        user.setEnabled(false);
        users.save(user);

        assertThatThrownBy(() -> userService.signIn(new OAuthProfile("google", "rep@example.com", true, "Rep", null)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo("account_disabled");
    }
}
