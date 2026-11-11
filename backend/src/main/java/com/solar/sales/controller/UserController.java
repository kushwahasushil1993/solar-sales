package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.EnabledUpdate;
import com.solar.sales.dto.AuthDtos.PasswordReset;
import com.solar.sales.dto.AuthDtos.RoleUpdate;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only (enforced in SecurityConfig). */
@Tag(name = "Users", description = "Admin only: roles and account status")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List all accounts")
    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @Operation(summary = "Change a user's role (you cannot demote yourself or the last admin)")
    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdate body,
                                   @AuthenticationPrincipal AuthUser current) {
        return userService.updateRole(id, body.role(), current);
    }

    @Operation(summary = "Set a new password for another user (e.g. they forgot it)")
    @PutMapping("/{id}/password")
    public UserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReset body,
                                      @AuthenticationPrincipal AuthUser current) {
        return userService.resetPassword(id, body.newPassword(), current);
    }

    @Operation(summary = "Enable or disable an account (takes effect on the user's next request)")
    @PutMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable Long id, @RequestBody EnabledUpdate body,
                                   @AuthenticationPrincipal AuthUser current) {
        return userService.setEnabled(id, body.enabled(), current);
    }
}
