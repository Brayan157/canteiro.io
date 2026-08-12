package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.AuthenticationService;
import com.renovar.canteiro.io.identity.application.AuthenticationTokens;
import com.renovar.canteiro.io.identity.application.LoginCommand;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.application.PasswordResetEmailSender;
import com.renovar.canteiro.io.identity.application.PasswordResetService;
import com.renovar.canteiro.io.identity.application.PasswordResetTokenHasher;
import com.renovar.canteiro.io.identity.application.RequestPasswordResetCommand;
import com.renovar.canteiro.io.identity.application.ResetPasswordCommand;
import com.renovar.canteiro.io.identity.application.RefreshTokenHasher;
import com.renovar.canteiro.io.identity.domain.PasswordResetToken;
import com.renovar.canteiro.io.identity.domain.PasswordResetTokenRepository;
import com.renovar.canteiro.io.identity.domain.RefreshToken;
import com.renovar.canteiro.io.identity.domain.RefreshTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(PasswordResetIntegrationTest.PasswordResetEmailTestConfiguration.class)
class PasswordResetIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String OLD_PASSWORD = "Canteiro#2026Seguro";
    private static final String NEW_PASSWORD = "NovaSenha#2026Segura";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordResetTokenHasher passwordResetTokenHasher;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private CapturingPasswordResetEmailSender passwordResetEmailSender;

    @Test
    void resetsPasswordConsumesTokenAndRevokesExistingSessions() {
        User user = createActiveUser("reset@example.com");
        AuthenticationTokens session = authenticationService.login(new LoginCommand("reset@example.com", OLD_PASSWORD));

        passwordResetService.requestReset(new RequestPasswordResetCommand("reset@example.com"));
        String rawPasswordResetToken = passwordResetEmailSender.rawPasswordResetToken();
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenHash(
                passwordResetTokenHasher.hash(rawPasswordResetToken)
        ).orElseThrow();

        passwordResetService.resetPassword(new ResetPasswordCommand(rawPasswordResetToken, NEW_PASSWORD));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        RefreshToken revokedSession = refreshTokenRepository.findByTokenHash(
                refreshTokenHasher.hash(session.refreshToken())
        ).orElseThrow();
        PasswordResetToken consumedToken = passwordResetTokenRepository.findByTokenHash(
                passwordResetTokenHasher.hash(rawPasswordResetToken)
        ).orElseThrow();

        assertTrue(passwordHasher.matches(NEW_PASSWORD, updatedUser.getPasswordHash()));
        assertFalse(passwordHasher.matches(OLD_PASSWORD, updatedUser.getPasswordHash()));
        assertNotNull(updatedUser.getPasswordChangedAt());
        assertNotNull(revokedSession.getRevokedAt());
        assertNotNull(consumedToken.getConsumedAt());
    }

    @Test
    void doesNotSendEmailForUnknownAccount() {
        passwordResetEmailSender.clear();
        passwordResetService.requestReset(new RequestPasswordResetCommand("unknown@example.com"));

        assertNull(passwordResetEmailSender.rawPasswordResetToken());
    }

    private User createActiveUser(String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(OLD_PASSWORD), Instant.now());
        return userRepository.save(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordResetEmailTestConfiguration {

        @Bean
        @Primary
        CapturingPasswordResetEmailSender passwordResetEmailSender() {
            return new CapturingPasswordResetEmailSender();
        }
    }

    static class CapturingPasswordResetEmailSender implements PasswordResetEmailSender {

        private String rawPasswordResetToken;

        @Override
        public void send(String email, String rawPasswordResetToken, Instant expiresAt) {
            this.rawPasswordResetToken = rawPasswordResetToken;
        }

        String rawPasswordResetToken() {
            return rawPasswordResetToken;
        }

        void clear() {
            rawPasswordResetToken = null;
        }
    }
}
