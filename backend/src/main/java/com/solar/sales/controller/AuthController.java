package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.OtpRequest;
import com.solar.sales.dto.AuthDtos.OtpSent;
import com.solar.sales.dto.AuthDtos.ProviderInfo;
import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.otp.OtpService;
import com.solar.sales.security.AuthUser;
import com.solar.sales.security.OAuthProviders;
import com.solar.sales.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Auth", description = "Registration, sign-in options and the current user")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final OAuthProviders providers;
    private final OtpService otpService;

    public AuthController(UserService userService, OAuthProviders providers, OtpService otpService) {
        this.userService = userService;
        this.providers = providers;
        this.otpService = otpService;
    }

    @Operation(summary = "Configured sign-in providers (public)")
    @GetMapping("/providers")
    public List<ProviderInfo> providers() {
        return providers.info();
    }

    @Operation(summary = "Create an email + password account (public)",
            description = "Creates a sales user and does not sign in: follow with POST /api/auth/login "
                    + "(form fields email, password). Registration never grants admin.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @Operation(summary = "Send a 6-digit sign-in code by SMS (public)",
            description = "Works for any mobile number. Then POST /api/auth/otp/verify (form fields phone, code): "
                    + "204 + session for an existing account, or 200 {\"status\":\"REGISTRATION_REQUIRED\"} for a "
                    + "new number, followed by POST /api/auth/otp/register (form fields fullName, email optional) → "
                    + "204 + session. Codes expire after 5 minutes; resends are rate limited (429).")
    @PostMapping("/otp/request")
    public OtpSent requestOtp(@Valid @RequestBody OtpRequest request) {
        return otpService.requestCode(request.phone());
    }

    @Operation(summary = "The signed-in user (401 when not signed in)")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user);
    }
}
