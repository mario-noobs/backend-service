package com.mario.backend.auth.service;

import com.mario.backend.auth.dto.*;
import com.mario.backend.auth.entity.Auth;
import com.mario.backend.logging.annotation.Traceable;
import com.mario.backend.auth.repository.AuthRepository;
import com.mario.backend.auth.security.JwtTokenProvider;
import com.mario.backend.common.exception.ApiException;
import com.mario.backend.common.exception.ErrorCode;
import com.mario.backend.rbac.entity.Permission;
import com.mario.backend.rbac.entity.Role;
import com.mario.backend.rbac.repository.RoleRepository;
import com.mario.backend.users.entity.User;
import com.mario.backend.users.repository.UserRepository;
import com.mario.email.EmailRequest;
import com.mario.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes:30}")
    private int resetExpiryMinutes;

    @Value("${app.invitation.expiry-hours:72}")
    private int invitationExpiryHours;

    @Traceable("auth.register")
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (authRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ErrorCode.EMAIL_EXISTS);
        }

        Role defaultRole = roleRepository.findByIsDefaultTrue()
                .orElseGet(() -> roleRepository.findByName("BASIC_USER").orElse(null));

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .status(User.UserStatus.activated)
                .role(defaultRole)
                .build();
        user = userRepository.save(user);

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(request.getPassword(), salt);

        Auth auth = Auth.builder()
                .userId(user.getId())
                .email(request.getEmail())
                .salt(salt)
                .password(hashedPassword)
                .authType(Auth.AuthType.email_password)
                .build();
        authRepository.save(auth);

        return generateTokenResponse(user);
    }

    @Traceable("auth.login")
    public TokenResponse login(LoginRequest request) {
        Auth auth = authRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!BCrypt.checkpw(request.getPassword(), auth.getPassword())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findById(auth.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "User not found"));

        checkUserStatus(user);

        return generateTokenResponse(user);
    }

    @Traceable("auth.refreshToken")
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException(ErrorCode.INVALID_TOKEN, "Invalid or expired refresh token");
        }

        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new ApiException(ErrorCode.TOKEN_BLACKLISTED, "Refresh token has been revoked");
        }

        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new ApiException(ErrorCode.INVALID_TOKEN_TYPE, "Expected refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        tokenBlacklistService.blacklistToken(refreshToken, jwtTokenProvider.getExpirationFromToken(refreshToken));

        // Load user from DB to get current role (picks up role changes)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "User not found"));

        checkUserStatus(user);

        return generateTokenResponse(user);
    }

    @Traceable("auth.changePassword")
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }

        Auth auth = authRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!BCrypt.checkpw(request.getCurrentPassword(), auth.getPassword())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD);
        }

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(request.getNewPassword(), salt);

        auth.setSalt(salt);
        auth.setPassword(hashedPassword);
        authRepository.save(auth);
    }

    @Traceable("auth.logout")
    public void logout(LogoutRequest request) {
        String accessToken = request.getAccessToken();

        if (jwtTokenProvider.validateToken(accessToken)) {
            tokenBlacklistService.blacklistToken(accessToken, jwtTokenProvider.getExpirationFromToken(accessToken));
        }
    }

    @Traceable("auth.forgotPassword")
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        authRepository.findByEmail(request.getEmail()).ifPresent(auth -> {
            String token = UUID.randomUUID().toString();
            auth.setResetToken(token);
            auth.setResetTokenExpiry(LocalDateTime.now().plusMinutes(resetExpiryMinutes));
            authRepository.save(auth);

            User user = userRepository.findById(auth.getUserId()).orElse(null);
            String firstName = user != null ? user.getFirstName() : "User";

            String resetUrl = frontendUrl + "/reset-password?token=" + token;

            emailService.send(EmailRequest.builder()
                    .to(auth.getEmail())
                    .subject("Password Reset Request")
                    .templateName("password-reset")
                    .model("firstName", firstName)
                    .model("resetUrl", resetUrl)
                    .model("expiryMinutes", resetExpiryMinutes)
                    .build());
        });
        // Silently succeed even if email not found (prevent enumeration)
    }

    @Traceable("auth.resetPassword")
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }

        Auth auth = authRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));

        if (auth.getResetTokenExpiry() == null || auth.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.RESET_TOKEN_INVALID);
        }

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(request.getNewPassword(), salt);

        auth.setSalt(salt);
        auth.setPassword(hashedPassword);
        auth.setResetToken(null);
        auth.setResetTokenExpiry(null);
        authRepository.save(auth);
    }

    @Traceable("auth.acceptInvitation")
    @Transactional
    public TokenResponse acceptInvitation(AcceptInvitationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }

        Auth auth = authRepository.findByInvitationToken(request.getToken())
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_TOKEN_INVALID));

        if (auth.getInvitationTokenExpiry() == null || auth.getInvitationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.INVITATION_TOKEN_INVALID);
        }

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(request.getPassword(), salt);

        auth.setSalt(salt);
        auth.setPassword(hashedPassword);
        auth.setInvitationToken(null);
        auth.setInvitationTokenExpiry(null);
        authRepository.save(auth);

        User user = userRepository.findById(auth.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(User.UserStatus.activated);
        userRepository.save(user);

        return generateTokenResponse(user);
    }

    private void checkUserStatus(User user) {
        switch (user.getStatus()) {
            case deactivated -> throw new ApiException(ErrorCode.ACCOUNT_DEACTIVATED);
            case banned -> throw new ApiException(ErrorCode.ACCOUNT_BANNED);
            case invited -> throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
            case activated -> { /* OK */ }
        }
    }

    public void blacklistUser(Long userId) {
        tokenBlacklistService.blacklistUser(userId);
    }

    private TokenResponse generateTokenResponse(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : null;
        List<String> permissions = user.getRole() != null
                ? user.getRole().getPermissions().stream().map(Permission::getName).toList()
                : Collections.emptyList();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleName, permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        return TokenResponse.builder()
                .accessToken(TokenResponse.TokenInfo.builder()
                        .token(accessToken)
                        .expiredIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                        .build())
                .refreshToken(TokenResponse.TokenInfo.builder()
                        .token(refreshToken)
                        .expiredIn(jwtTokenProvider.getRefreshTokenExpiration() / 1000)
                        .build())
                .build();
    }
}
