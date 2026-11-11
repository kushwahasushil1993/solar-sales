package com.solar.sales.security;

import com.solar.sales.otp.OtpService;
import com.solar.sales.otp.RegistrationException;
import com.solar.sales.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Three ways to sign in, all ending in the same server-side session: email + password (form POST to
 * /api/auth/login), mobile number + SMS code (/api/auth/otp/verify, plus /api/auth/otp/register for new numbers)
 * and OAuth2 (Google / GitHub). The React app is served from the same origin (Vite proxy in dev,
 * Nginx in production), so no tokens are handled in the browser. State-changing requests, including login,
 * need the XSRF-TOKEN cookie echoed back in the X-XSRF-TOKEN header.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OAuthProviders providers, OAuthAccountService accounts,
                                            PasswordAuthenticationProvider passwordProvider,
                                            OtpService otpService,
                                            AppUserRepository users) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .authenticationProvider(passwordProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/providers", "/api/setup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/setup/admin").permitAll() // refused once an admin exists
                        .requestMatchers("/api/auth/password/forgot", "/api/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/otp/request", "/api/auth/otp/verify", "/api/auth/otp/register").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // admin only: Excel purchase upload, template, export, history, edits and user management
                        .requestMatchers("/api/users/**", "/api/uploads/**", "/api/notifications/**",
                                "/api/sales/*/notifications/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/sales/upload").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/sales/template", "/api/sales/export").hasRole("ADMIN")
                        // sales users may update installation progress (only), admins everything
                        .requestMatchers(HttpMethod.PATCH, "/api/sales/*/installation").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.PUT, "/api/sales/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/sales/**").hasRole("ADMIN")
                        // installation tasks: admins assign and see agents' work; agents handle their own tasks
                        .requestMatchers(HttpMethod.POST, "/api/tasks/*/assign", "/api/tasks/*/unassign", "/api/tasks/assign").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks/board").hasRole("ADMIN")
                        .requestMatchers("/api/agents/**", "/api/activity/**").hasRole("ADMIN")
                        .requestMatchers("/api/tasks/**").hasRole("AGENT")
                        // sales records and figures: admins and sales users, not agents
                        .requestMatchers("/api/sales/**", "/api/dashboard").hasAnyRole("ADMIN", "USER")
                        // notifications (bell): admins and agents; accounts still waiting for approval get nothing here
                        .requestMatchers("/api/inbox/**").hasAnyRole("ADMIN", "AGENT")
                        // any signed-in user (incl. waiting for approval): own profile and password
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, HttpStatus.UNAUTHORIZED, "Please sign in"))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, HttpStatus.FORBIDDEN,
                                ex instanceof CsrfException
                                        ? "Your session token is missing or expired. Refresh the page and try again."
                                        : "Only an admin can do this")))
                // email + password: form-encoded POST (email, password); 204 on success, then GET /api/auth/me
                .formLogin(form -> form
                        .loginPage("/login") // the React login page, not Spring's generated one
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((req, res, ex) -> {
                            if (ex instanceof DisabledException) {
                                writeProblem(res, HttpStatus.FORBIDDEN, "This account is disabled. Contact an administrator.");
                            } else {
                                writeProblem(res, HttpStatus.UNAUTHORIZED, "Invalid email or password");
                            }
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value())))
                .addFilterBefore(otpFilter(otpService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ActiveUserFilter(users), AuthorizationFilter.class);

        if (!providers.isEmpty()) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(providers.registrations()))
                    .loginPage("/login") // the React login page, not Spring's generated one
                    .userInfoEndpoint(info -> info
                            .oidcUserService(accounts::loadOidcUser)
                            .userService(accounts::loadOAuth2User))
                    .defaultSuccessUrl("/", true)
                    .failureHandler((req, res, ex) -> res.sendRedirect("/login?error=" + URLEncoder.encode(
                            ex instanceof OAuth2AuthenticationException failure
                                    ? failure.getError().getErrorCode() : "login_failed",
                            StandardCharsets.UTF_8))));
        }
        return http.build();
    }

    /** Mobile sign-in, set up like form login: session saved, session id rotated, CSRF token renewed. */
    private static OtpAuthenticationFilter otpFilter(OtpService otpService) {
        OtpAuthenticationFilter filter = new OtpAuthenticationFilter(otpService);
        filter.setAuthenticationManager(authentication -> {
            throw new IllegalStateException("not used: the filter authenticates via OtpService");
        });
        filter.setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository()));
        filter.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(CookieCsrfTokenRepository.withHttpOnlyFalse()))));
        filter.setAuthenticationSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()));
        filter.setAuthenticationFailureHandler((req, res, ex) -> {
            if (ex instanceof RegistrationException reg) {
                writeProblem(res, reg.status(), reg.getMessage(), reg.field());
            } else {
                writeProblem(res, ex instanceof DisabledException ? HttpStatus.FORBIDDEN
                        : ex instanceof AuthenticationServiceException ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.UNAUTHORIZED, ex.getMessage());
            }
        });
        return filter;
    }

    private static void writeProblem(HttpServletResponse res, HttpStatus status, String detail) throws IOException {
        writeProblem(res, status, detail, null);
    }

    /** Problem-details JSON; {@code field} adds {"errors":{field: detail}} so forms can highlight the input. */
    private static void writeProblem(HttpServletResponse res, HttpStatus status, String detail, String field)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String errors = field == null ? "" : ",\"errors\":{\"%s\":\"%s\"}".formatted(field, detail);
        res.getWriter().write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"%s}"
                .formatted(status.value(), status.getReasonPhrase(), detail, errors));
    }
}
