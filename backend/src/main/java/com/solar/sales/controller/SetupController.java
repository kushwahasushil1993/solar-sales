package com.solar.sales.controller;

import com.solar.sales.dto.AuthDtos.RegisterRequest;
import com.solar.sales.dto.AuthDtos.SetupStatus;
import com.solar.sales.dto.AuthDtos.UserResponse;
import com.solar.sales.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** First-run setup: create the administrator when the database has none. Public, but only works once. */
@Tag(name = "Setup", description = "Create the first administrator on a new install")
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final UserService userService;

    public SetupController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Whether the first administrator still needs to be created (public)")
    @GetMapping
    public SetupStatus status() {
        return new SetupStatus(userService.setupRequired());
    }

    @Operation(summary = "Create the first administrator (public; 403 once any admin exists)")
    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createAdmin(@Valid @RequestBody RegisterRequest request) {
        return userService.createFirstAdmin(request);
    }
}
