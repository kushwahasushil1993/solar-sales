package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.AuthResponse;
import com.solar.sales.dto.AuthDtos.LoginRequest;
import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user);
    }
}
