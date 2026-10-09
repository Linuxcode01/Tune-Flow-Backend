package com.tuneflow.auth.service;

import com.tuneflow.auth.dto.AuthResponse;
import com.tuneflow.auth.dto.ForgotPasswordRequest;
import com.tuneflow.auth.dto.LoginRequest;
import com.tuneflow.auth.dto.PasswordResetResponse;
import com.tuneflow.auth.dto.RefreshTokenRequest;
import com.tuneflow.auth.dto.RegisterRequest;
import com.tuneflow.auth.dto.ResetPasswordRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request, String userAgent);

    AuthResponse login(LoginRequest request, String userAgent);

    AuthResponse refresh(RefreshTokenRequest request, String userAgent);

    void logout(RefreshTokenRequest request);

    void logoutAllDevices();

    PasswordResetResponse forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
