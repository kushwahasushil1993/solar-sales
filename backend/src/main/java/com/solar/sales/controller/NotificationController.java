package com.solar.sales.controller;

import com.solar.sales.dto.NotificationResponse;
import com.solar.sales.dto.PageResponse;
import com.solar.sales.notification.NotificationService;
import com.solar.sales.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin-only (enforced in SecurityConfig). */
@Tag(name = "Customer emails", description = "Admin only: invoice and installation workflow emails")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Recent customer emails, newest first")
    @GetMapping
    public PageResponse<NotificationResponse> recent(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return notificationService.recent(page, size);
    }

    @Operation(summary = "Send the same email again (uses the sale's current data)")
    @PostMapping("/{id}/resend")
    public NotificationResponse resend(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return notificationService.resend(id, user.fullName());
    }
}
