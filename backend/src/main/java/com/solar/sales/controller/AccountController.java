package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.PasswordChange;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "My account", description = "The signed-in user's own settings")
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Set or change my password",
            description = "currentPassword is required when the account already has a password.")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChange body, @AuthenticationPrincipal AuthUser user) {
        userService.changeOwnPassword(user, body.currentPassword(), body.newPassword());
    }
}
