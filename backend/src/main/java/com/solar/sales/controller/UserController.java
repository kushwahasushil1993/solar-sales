package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.EnabledUpdate;
import com.solar.sales.dto.AuthDtos.RoleUpdate;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdate body,
                                   @AuthenticationPrincipal AuthUser current) {
        return userService.updateRole(id, body.role(), current);
    }

    @PutMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable Long id, @RequestBody EnabledUpdate body,
                                   @AuthenticationPrincipal AuthUser current) {
        return userService.setEnabled(id, body.enabled(), current);
    }
}
