package com.p2plending.notification.controller;

import com.p2plending.notification.dto.NotificationResponse;
import com.p2plending.notification.dto.PagedResponse;
import com.p2plending.notification.security.AuthenticatedUser;
import com.p2plending.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(notificationService.getUserNotifications(principal.userId(), page, size));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(id, principal.userId()));
    }
}
