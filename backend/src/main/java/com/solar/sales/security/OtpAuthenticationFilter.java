package com.solar.sales.security;

import com.solar.sales.dto.AuthDtos.RegistrationRequired;
import com.solar.sales.entity.AppUser;
import com.solar.sales.otp.OtpService;
import com.solar.sales.otp.OtpService.Verification;
import com.solar.sales.otp.PhoneNumbers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Mobile sign-in, two endpoints (form-encoded):
 * <ul>
 *   <li>POST /api/auth/otp/verify (phone, code): existing account → signed in (204). New number → 200
 *       {"status":"REGISTRATION_REQUIRED"}; the verified number is kept in the session for 10 minutes.</li>
 *   <li>POST /api/auth/otp/register (fullName, email optional): creates the account for that verified number
 *       and signs in (204).</li>
 * </ul>
 * Successful sign-ins rotate the session id and CSRF token like form login (see SecurityConfig).
 */
class OtpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    static final String VERIFIED_PHONE = OtpAuthenticationFilter.class.getName() + ".VERIFIED_PHONE";
    static final Duration REGISTRATION_WINDOW = Duration.ofMinutes(10);

    private static final RequestMatcher VERIFY =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/otp/verify");
    private static final RequestMatcher REGISTER =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/otp/register");

    /** A number proven by SMS code that has no account yet. */
    record VerifiedPhone(String phone, Instant verifiedAt) implements java.io.Serializable {
    }

    private final OtpService otpService;

    OtpAuthenticationFilter(OtpService otpService) {
        super(new OrRequestMatcher(VERIFY, REGISTER));
        this.otpService = otpService;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException {
        if (REGISTER.matches(request)) {
            return completeRegistration(request);
        }
        Verification result = otpService.verify(request.getParameter("phone"), request.getParameter("code"));
        if (!result.isNewNumber()) {
            return authenticated(result.user());
        }
        // New number: not signed in yet. Remember it in a fresh session id (no fixation) and ask for details.
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(VERIFIED_PHONE, new VerifiedPhone(result.phone(), Instant.now()));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8"); // the masked number contains "•"
        RegistrationRequired body = new RegistrationRequired(result.phone(), PhoneNumbers.mask(result.phone()));
        response.getWriter().write("{\"status\":\"%s\",\"phone\":\"%s\",\"maskedPhone\":\"%s\"}"
                .formatted(body.status(), body.phone(), body.maskedPhone()));
        return null; // authentication not complete; the response above is final
    }

    private Authentication completeRegistration(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        VerifiedPhone verified = session == null ? null : (VerifiedPhone) session.getAttribute(VERIFIED_PHONE);
        if (verified == null || verified.verifiedAt().plus(REGISTRATION_WINDOW).isBefore(Instant.now())) {
            throw new com.solar.sales.otp.RegistrationException(org.springframework.http.HttpStatus.UNAUTHORIZED, "phone",
                    "Please verify your mobile number first.");
        }
        AppUser user = otpService.completeRegistration(verified.phone(),
                request.getParameter("fullName"), request.getParameter("email"), requestedRole(request));
        session.removeAttribute(VERIFIED_PHONE); // single use
        return authenticated(user);
    }

    private static com.solar.sales.entity.Role requestedRole(HttpServletRequest request) {
        try {
            String value = request.getParameter("requestedRole");
            return value == null || value.isBlank() ? null : com.solar.sales.entity.Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Authentication authenticated(AppUser user) {
        AuthUser principal = AuthUser.of(user, Map.of(), null, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
