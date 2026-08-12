package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.PasswordResetToken;
import com.renovar.canteiro.io.identity.domain.PasswordResetTokenRepository;
import com.renovar.canteiro.io.identity.domain.RefreshTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;
    private final PasswordResetTokenHasher passwordResetTokenHasher;
    private final PasswordResetEmailSender passwordResetEmailSender;
    private final PasswordResetProperties passwordResetProperties;
    private final Clock clock;

    @Transactional
    public void requestReset(RequestPasswordResetCommand command) {
        userRepository.findByEmail(command.email().trim())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::createTokenAndSendEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        Instant currentInstant = clock.instant();
        PasswordResetToken passwordResetToken = passwordResetTokenRepository
                .findByTokenHash(passwordResetTokenHasher.hash(command.passwordResetToken()))
                .orElseThrow(() -> new InvalidAuthenticationException("Password reset token is invalid or expired"));
        User user = userRepository.findById(passwordResetToken.getUserId())
                .filter(currentUser -> currentUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidAuthenticationException("Password reset token is invalid or expired"));

        user.changePassword(passwordHasher.hash(command.newPassword()), currentInstant);
        passwordResetToken.consume(currentInstant);
        List<com.renovar.canteiro.io.identity.domain.RefreshToken> refreshTokens = refreshTokenRepository.findByUserId(
                user.getId()
        );
        refreshTokens.forEach(refreshToken -> refreshToken.revoke(currentInstant));

        userRepository.save(user);
        passwordResetTokenRepository.save(passwordResetToken);
        refreshTokens.forEach(refreshTokenRepository::save);
    }

    private void createTokenAndSendEmail(User user) {
        String rawPasswordResetToken = passwordResetTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(passwordResetProperties.tokenTtl());
        passwordResetTokenRepository.save(PasswordResetToken.create(
                user.getId(),
                passwordResetTokenHasher.hash(rawPasswordResetToken),
                expiresAt
        ));
        passwordResetEmailSender.send(user.getEmail(), rawPasswordResetToken, expiresAt);
    }
}
