package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Reissues a pending account invitation without disclosing whether its email exists. */
@Service
@RequiredArgsConstructor
public class ResendAccountActivationService {

    private final UserRepository userRepository;
    private final AccountActivationTokenRepository accountActivationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final AccountActivationEmailSender accountActivationEmailSender;
    private final AccountActivationProperties accountActivationProperties;
    private final Clock clock;

    @Transactional
    public void resend(ResendAccountActivationCommand command) {
        userRepository.findByEmail(command.email().trim())
                .filter(user -> user.getStatus() == UserStatus.PENDING_ACTIVATION)
                .ifPresent(this::replaceOutstandingInvitation);
    }

    private void replaceOutstandingInvitation(User user) {
        Instant now = clock.instant();
        String rawToken = activationTokenGenerator.generate();
        Instant expiresAt = now.plus(accountActivationProperties.tokenTtl());
        String tokenHash = activationTokenHasher.hash(rawToken);

        accountActivationTokenRepository.findByUserId(user.getId()).ifPresentOrElse(
                existingToken -> {
                    existingToken.replace(tokenHash, expiresAt);
                    accountActivationTokenRepository.save(existingToken);
                },
                () -> accountActivationTokenRepository.save(AccountActivationToken.create(
                        user.getId(), tokenHash, expiresAt
                ))
        );
        accountActivationEmailSender.send(user.getEmail(), rawToken, expiresAt);
    }
}
