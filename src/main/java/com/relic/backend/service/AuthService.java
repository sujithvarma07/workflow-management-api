package com.relic.backend.service;

import com.relic.backend.dto.*;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    void activate(ActivateAccountRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(LogoutRequest request);

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
