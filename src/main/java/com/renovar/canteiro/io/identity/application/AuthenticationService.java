package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.RefreshToken;
import com.renovar.canteiro.io.identity.domain.RefreshTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthenticationProperties authenticationProperties;
    private final Clock clock;

    @Transactional
    public AuthenticationTokens login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email().trim())
                .filter(this::isActiveWithPassword)
                .filter(currentUser -> passwordHasher.matches(command.password(), currentUser.getPasswordHash()))
                .orElseThrow(() -> new InvalidAuthenticationException("Invalid email or password"));

        return issueTokens(user);
    }

    @Transactional
    public AuthenticationTokens refresh(RefreshSessionCommand command) {
        Instant currentInstant = clock.instant();
        RefreshToken currentRefreshToken = refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(command.refreshToken()))
                .filter(refreshToken -> refreshToken.isUsableAt(currentInstant))
                .orElseThrow(() -> new InvalidAuthenticationException("Refresh token is invalid or expired"));
        User user = userRepository.findById(currentRefreshToken.getUserId())
                .filter(this::isActiveWithPassword)
                .orElseThrow(() -> new InvalidAuthenticationException("Refresh token is invalid or expired"));

        AuthenticationTokens authenticationTokens = issueTokens(user);
        RefreshToken replacement = refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(authenticationTokens.refreshToken()))
                .orElseThrow(() -> new IllegalStateException("Replacement refresh token must exist"));
        currentRefreshToken.replaceWith(replacement.getId(), currentInstant);
        refreshTokenRepository.save(currentRefreshToken);
        return authenticationTokens;
    }

    @Transactional
    public void logout(LogoutCommand command) {
        refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(command.refreshToken()))
                .ifPresent(refreshToken -> {
                    refreshToken.revoke(clock.instant());
                    refreshTokenRepository.save(refreshToken);
                });
    }

    private AuthenticationTokens issueTokens(User user) {
        String rawRefreshToken = refreshTokenGenerator.generate();
        Instant currentInstant = clock.instant();
        refreshTokenRepository.save(RefreshToken.create(
                user.getId(),
                refreshTokenHasher.hash(rawRefreshToken),
                currentInstant.plus(authenticationProperties.refreshTokenTtl())
        ));
        return new AuthenticationTokens(
                accessTokenIssuer.issue(user),
                rawRefreshToken,
                authenticationProperties.accessTokenTtl().toSeconds()
        );
    }

    private boolean isActiveWithPassword(User user) {
        return user.getStatus() == UserStatus.ACTIVE && user.getPasswordHash() != null;
    }
}
