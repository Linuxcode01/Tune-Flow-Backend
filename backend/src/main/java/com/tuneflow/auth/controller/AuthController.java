package com.tuneflow.auth.controller;

import com.tuneflow.auth.dto.AuthResponse;
import com.tuneflow.auth.dto.ForgotPasswordRequest;
import com.tuneflow.auth.dto.LoginRequest;
import com.tuneflow.auth.dto.PasswordResetResponse;
import com.tuneflow.auth.dto.RefreshTokenRequest;
import com.tuneflow.auth.dto.RegisterRequest;
import com.tuneflow.auth.dto.ResetPasswordRequest;
import com.tuneflow.auth.service.AuthService;
import com.tuneflow.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/auth} - registration, login and token lifecycle (docs sections 13-15). */
@Tag(name = "Authentication", description = "Register, login, refresh, logout and password reset")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Create an account", description = "Returns an access/refresh token pair immediately")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                             HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    @Operation(summary = "Authenticate", description = "Rate limited to 5 attempts/minute per IP")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                          HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Logged in successfully", response));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "The presented refresh token is rotated (single use)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                            HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(request, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @Operation(summary = "Revoke a refresh token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @Operation(summary = "Revoke every session for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        authService.logoutAllDevices();
        return ResponseEntity.ok(ApiResponse.success("All sessions revoked", null));
    }

    @Operation(summary = "Request a password reset link",
            description = "Always returns the same response so accounts cannot be enumerated")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Password reset requested", authService.forgotPassword(request)));
    }

    @Operation(summary = "Complete a password reset with a reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset", null));
    }
}
