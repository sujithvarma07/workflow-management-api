package com.relic.backend.service.impl;

import com.relic.backend.audit.AuditService;
import com.relic.backend.dto.*;
import com.relic.backend.entity.User;
import com.relic.backend.entity.UserRole;
import com.relic.backend.entity.UserStatus;
import com.relic.backend.exception.DuplicateResourceException;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.security.AppUserPrincipal;
import com.relic.backend.security.InvalidTokenException;
import com.relic.backend.security.JwtService;
import com.relic.backend.security.RefreshTokenService;
import com.relic.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long PASSWORD_RESET_TOKEN_TTL_MINUTES = 60;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email '" + request.email() + "' already exists");
        }

        // Self-registration always creates a DEVELOPER account - a client
        // cannot request ADMIN or MANAGER for themselves through this
        // endpoint. Granting an elevated role is something only an
        // existing ADMIN can do afterwards (PUT /api/v1/users/{id}), which
        // is what actually prevents privilege escalation at sign-up time.
        String activationToken = generateToken();

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .role(UserRole.DEVELOPER)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.PENDING_ACTIVATION)
                .activationToken(activationToken)
                .build();

        User saved = userRepository.save(user);
        auditService.success(saved.getUsername(), "USER_REGISTERED", "User", String.valueOf(saved.getId()), null);

        return new RegisterResponse(
                saved.getId(), saved.getUsername(), saved.getEmail(), saved.getFullName(),
                saved.getStatus().name(), activationToken);
    }

    @Override
    @Transactional
    public void activate(ActivateAccountRequest request) {
        User user = userRepository.findByActivationToken(request.token())
                .orElseThrow(() -> new InvalidTokenException("Activation token is not recognized"));

        user.setStatus(UserStatus.ACTIVE);
        user.setActivationToken(null);
        userRepository.save(user);

        auditService.success(user.getUsername(), "USER_ACTIVATED", "User", String.valueOf(user.getId()), null);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // Deliberately the same generic message regardless of whether
            // the username doesn't exist, the password is wrong, or the
            // account isn't active yet - distinguishing those in the
            // response would let a client enumerate valid usernames.
            auditService.failure(request.username(), "LOGIN", "User", null, ex.getClass().getSimpleName());
            throw new BadCredentialsException("Invalid username or password");
        }

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getUserId()).orElseThrow();
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user);

        auditService.success(user.getUsername(), "LOGIN", "User", String.valueOf(user.getId()), null);

        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        User user = rotation.user();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidTokenException("Account is no longer active");
        }

        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());
        auditService.success(user.getUsername(), "TOKEN_REFRESHED", "User", String.valueOf(user.getId()), null);

        return new TokenResponse(
                accessToken, rotation.rawRefreshToken(), "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        auditService.success(com.relic.backend.security.SecurityUtils.currentUsername(),
                "LOGOUT", "User", null, null);
    }

    @Override
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new InvalidTokenException("No account found for that username"));

        String resetToken = generateToken();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpiresAt(Instant.now().plusSeconds(PASSWORD_RESET_TOKEN_TTL_MINUTES * 60));
        userRepository.save(user);

        auditService.success(user.getUsername(), "PASSWORD_RESET_REQUESTED", "User", String.valueOf(user.getId()), null);

        return new ForgotPasswordResponse(resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> new InvalidTokenException("Password reset token is not recognized"));

        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Password reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);

        // Resetting the password invalidates every existing session - if
        // the reset was triggered because a credential leaked, a refresh
        // token issued before the reset should not keep working afterward.
        refreshTokenService.revokeAllForUser(user.getId());

        auditService.success(user.getUsername(), "PASSWORD_RESET", "User", String.valueOf(user.getId()), null);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
