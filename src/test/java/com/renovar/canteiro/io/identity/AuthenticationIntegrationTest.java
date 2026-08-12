package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.AuthenticationService;
import com.renovar.canteiro.io.identity.application.AuthenticationTokens;
import com.renovar.canteiro.io.identity.application.InvalidAuthenticationException;
import com.renovar.canteiro.io.identity.application.LoginCommand;
import com.renovar.canteiro.io.identity.application.LogoutCommand;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.application.RefreshSessionCommand;
import com.renovar.canteiro.io.identity.application.RefreshTokenHasher;
import com.renovar.canteiro.io.identity.domain.RefreshToken;
import com.renovar.canteiro.io.identity.domain.RefreshTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void logsInWithActiveCredentialsAndStoresOnlyRefreshTokenHash() {
        User user = createActiveUser("login@example.com");

        AuthenticationTokens tokens = authenticationService.login(new LoginCommand("LOGIN@example.com", PASSWORD));
        Jwt accessToken = jwtDecoder.decode(tokens.accessToken());
        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(tokens.refreshToken()))
                .orElseThrow();

        assertEquals(user.getId().toString(), accessToken.getSubject());
        assertEquals("login@example.com", accessToken.getClaimAsString("email"));
        assertEquals(UserType.COMPANY.name(), accessToken.getClaimAsString("user_type"));
        assertEquals(900, tokens.accessTokenExpiresInSeconds());
        assertFalse(storedRefreshToken.getTokenHash().contains(tokens.refreshToken()));
        assertTrue(storedRefreshToken.isUsableAt(Instant.now()));
    }

    @Test
    void rejectsLoginWithIncorrectPasswordWithoutCreatingSession() {
        createActiveUser("wrong-password@example.com");

        assertThrows(
                InvalidAuthenticationException.class,
                () -> authenticationService.login(new LoginCommand("wrong-password@example.com", "Senha#Errada2026"))
        );
    }

    @Test
    void rotatesRefreshTokenAndRejectsReuseOfThePreviousToken() {
        createActiveUser("rotation@example.com");
        AuthenticationTokens firstSession = authenticationService.login(new LoginCommand("rotation@example.com", PASSWORD));

        AuthenticationTokens rotatedSession = authenticationService.refresh(new RefreshSessionCommand(firstSession.refreshToken()));
        RefreshToken previousRefreshToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHasher.hash(firstSession.refreshToken())
        ).orElseThrow();

        assertNotEquals(firstSession.refreshToken(), rotatedSession.refreshToken());
        assertNotNull(previousRefreshToken.getRevokedAt());
        assertNotNull(previousRefreshToken.getReplacedByTokenId());
        assertThrows(
                InvalidAuthenticationException.class,
                () -> authenticationService.refresh(new RefreshSessionCommand(firstSession.refreshToken()))
        );
    }

    @Test
    void revokesRefreshTokenOnLogout() {
        createActiveUser("logout@example.com");
        AuthenticationTokens session = authenticationService.login(new LoginCommand("logout@example.com", PASSWORD));

        authenticationService.logout(new LogoutCommand(session.refreshToken()));

        RefreshToken revokedRefreshToken = refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(session.refreshToken()))
                .orElseThrow();
        assertNotNull(revokedRefreshToken.getRevokedAt());
        assertThrows(
                InvalidAuthenticationException.class,
                () -> authenticationService.refresh(new RefreshSessionCommand(session.refreshToken()))
        );
    }

    private User createActiveUser(String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        return userRepository.save(user);
    }
}
