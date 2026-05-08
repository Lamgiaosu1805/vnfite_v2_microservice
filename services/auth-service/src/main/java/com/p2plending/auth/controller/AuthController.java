package com.p2plending.auth.controller;

import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Creates a new user account and returns a JWT token pair.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /api/auth/login
     * Authenticates credentials and returns a JWT token pair.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/refresh
     * Rotates refresh token and issues a new access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * POST /api/auth/kyc/submit
     * Submits a KYC document for the authenticated user.
     * Publishes a "kyc.submitted" Kafka event on success.
     */
    @PostMapping("/kyc/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KycDocumentResponse> submitKyc(
            @Valid @RequestBody KycSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Long userId = authService.getUserIdByEmail(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.submitKyc(userId, request));
    }
}
