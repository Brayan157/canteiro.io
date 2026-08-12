package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionTrialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ActivateAccountService {

    private final UserRepository userRepository;
    private final AccountActivationTokenRepository accountActivationTokenRepository;
    private final PasswordHasher passwordHasher;
    private final ActivationTokenHasher activationTokenHasher;
    private final SubscriptionTrialService subscriptionTrialService;
    private final Clock clock;

    @Transactional
    public void activate(ActivateAccountCommand command) {
        String tokenHash = activationTokenHasher.hash(command.activationToken());
        AccountActivationToken accountActivationToken = accountActivationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Activation token is invalid"));

        User user = userRepository.findById(accountActivationToken.getUserId())
                .orElseThrow(() -> new IllegalStateException("Activation token user must exist"));
        String passwordHash = passwordHasher.hash(command.password());
        accountActivationToken.consume(clock.instant());
        user.activate(passwordHash, clock.instant());

        userRepository.save(user);
        accountActivationTokenRepository.save(accountActivationToken);
        subscriptionTrialService.startTrialForCompanyUser(user.getId());
    }
}
