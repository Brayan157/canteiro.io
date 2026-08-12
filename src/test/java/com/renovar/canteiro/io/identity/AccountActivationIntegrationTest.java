package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.ActivateAccountCommand;
import com.renovar.canteiro.io.identity.application.ActivateAccountService;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountActivationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String RAW_TOKEN = "activation-token-used-only-once";
    private static final String VALID_PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private ActivateAccountService activateAccountService;

    @Autowired
    private ActivationTokenHasher activationTokenHasher;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountActivationTokenRepository accountActivationTokenRepository;

    @Test
    void activatesAccountWithBcryptPasswordAndConsumesToken() {
        User user = userRepository.save(User.create("activation@example.com", UserType.COMPANY));
        AccountActivationToken token = accountActivationTokenRepository.save(
                AccountActivationToken.create(
                        user.getId(),
                        activationTokenHasher.hash(RAW_TOKEN),
                        Instant.now().plusSeconds(3600)
                )
        );

        activateAccountService.activate(new ActivateAccountCommand(RAW_TOKEN, VALID_PASSWORD));

        User activatedUser = userRepository.findById(user.getId()).orElseThrow();
        AccountActivationToken consumedToken = accountActivationTokenRepository.findByTokenHash(token.getTokenHash())
                .orElseThrow();

        assertEquals(UserStatus.ACTIVE, activatedUser.getStatus());
        assertNotNull(activatedUser.getActivatedAt());
        assertNotNull(activatedUser.getPasswordChangedAt());
        assertFalse(activatedUser.getPasswordHash().contains(VALID_PASSWORD));
        assertTrue(passwordHasher.matches(VALID_PASSWORD, activatedUser.getPasswordHash()));
        assertNotNull(consumedToken.getConsumedAt());
    }

    @Test
    void keepsAccountPendingWhenPasswordViolatesPolicy() {
        User user = userRepository.save(User.create("pending@example.com", UserType.COMPANY));
        AccountActivationToken token = accountActivationTokenRepository.save(
                AccountActivationToken.create(
                        user.getId(),
                        activationTokenHasher.hash("invalid-password-token"),
                        Instant.now().plusSeconds(3600)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> activateAccountService.activate(new ActivateAccountCommand("invalid-password-token", "senha-fraca"))
        );

        User pendingUser = userRepository.findById(user.getId()).orElseThrow();
        AccountActivationToken unusedToken = accountActivationTokenRepository.findByTokenHash(token.getTokenHash())
                .orElseThrow();

        assertEquals(UserStatus.PENDING_ACTIVATION, pendingUser.getStatus());
        assertNull(unusedToken.getConsumedAt());
    }
}
