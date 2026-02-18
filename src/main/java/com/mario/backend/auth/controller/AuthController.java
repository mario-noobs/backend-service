package com.mario.backend.auth.controller;

import com.mario.backend.auth.dto.*;
import com.mario.backend.auth.security.AuthenticatedUser;
import com.mario.backend.auth.service.AuthService;
import com.mario.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse tokenResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PutMapping("/change-password")
    @PreAuthorize("hasAuthority('user:update_self')")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If your email is registered, you will receive a password reset link"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully"));
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<ApiResponse<TokenResponse>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        TokenResponse tokenResponse = authService.acceptInvitation(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }
}
