package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResendAccountActivationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void replacesOutstandingInvitationForPendingAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        AccountActivationTokenRepository tokenRepository = mock(AccountActivationTokenRepository.class);
        ActivationTokenGenerator tokenGenerator = mock(ActivationTokenGenerator.class);
        ActivationTokenHasher tokenHasher = mock(ActivationTokenHasher.class);
        AccountActivationEmailSender emailSender = mock(AccountActivationEmailSender.class);
        User user = User.rehydrate(
                java.util.UUID.randomUUID(), "pending@example.com", UserType.COMPANY,
                com.renovar.canteiro.io.identity.domain.UserStatus.PENDING_ACTIVATION,
                null, null, null, NOW.minusSeconds(120), NOW.minusSeconds(120)
        );
        AccountActivationToken oldToken = AccountActivationToken.rehydrate(
                java.util.UUID.randomUUID(), user.getId(), "old-token-hash", NOW.minusSeconds(60), null, NOW.minusSeconds(60), NOW.minusSeconds(60)
        );
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(user.getId())).thenReturn(Optional.of(oldToken));
        when(tokenGenerator.generate()).thenReturn("new-activation-token");
        when(tokenHasher.hash("new-activation-token")).thenReturn("new-token-hash");
        ResendAccountActivationService service = service(
                userRepository, tokenRepository, tokenGenerator, tokenHasher, emailSender
        );

        service.resend(new ResendAccountActivationCommand(" pending@example.com "));

        assertEquals("new-token-hash", oldToken.getTokenHash());
        assertEquals(NOW.plus(Duration.ofHours(24)), oldToken.getExpiresAt());
        assertNull(oldToken.getConsumedAt());
        verify(tokenRepository).save(oldToken);
        verify(emailSender).send(eq("pending@example.com"), eq("new-activation-token"), eq(NOW.plus(Duration.ofHours(24))));
    }

    @Test
    void doesNothingForUnknownEmail() {
        UserRepository userRepository = mock(UserRepository.class);
        AccountActivationTokenRepository tokenRepository = mock(AccountActivationTokenRepository.class);
        AccountActivationEmailSender emailSender = mock(AccountActivationEmailSender.class);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        service(userRepository, tokenRepository, mock(ActivationTokenGenerator.class), mock(ActivationTokenHasher.class), emailSender)
                .resend(new ResendAccountActivationCommand("unknown@example.com"));

        verify(tokenRepository, never()).findByUserId(any());
        verify(emailSender, never()).send(any(), any(), any());
    }

    private ResendAccountActivationService service(
            UserRepository userRepository,
            AccountActivationTokenRepository tokenRepository,
            ActivationTokenGenerator tokenGenerator,
            ActivationTokenHasher tokenHasher,
            AccountActivationEmailSender emailSender
    ) {
        return new ResendAccountActivationService(
                userRepository,
                tokenRepository,
                tokenGenerator,
                tokenHasher,
                emailSender,
                new AccountActivationProperties(Duration.ofHours(24), "no-reply@canteiro.local", "http://localhost:5173/ativar-conta"),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
