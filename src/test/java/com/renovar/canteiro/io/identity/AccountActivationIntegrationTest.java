package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.ActivateAccountCommand;
import com.renovar.canteiro.io.identity.application.ActivateAccountService;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

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

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

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

    @Test
    void startsTheCompanyTrialWhenItsInitialOwnerActivatesTheAccount() {
        Company company = companyRepository.save(Company.create(
                "Construtora Trial", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "trial-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
        User owner = userRepository.save(User.create("owner-trial-" + UUID.randomUUID() + "@example.com", UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(owner.getId(), company.getId()));
        Subscription pendingSubscription = subscriptionRepository.save(Subscription.create(
                company.getId(), new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.now(),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
        accountActivationTokenRepository.save(AccountActivationToken.create(
                owner.getId(), activationTokenHasher.hash("company-trial-token"), Instant.now().plusSeconds(3600)
        ));

        activateAccountService.activate(new ActivateAccountCommand("company-trial-token", VALID_PASSWORD));

        Subscription trialSubscription = subscriptionRepository.findById(pendingSubscription.getId()).orElseThrow();
        assertEquals(SubscriptionStatus.TRIAL, trialSubscription.getStatus());
        assertEquals(LocalDate.now(), trialSubscription.getTrialStartedOn());
        assertEquals(LocalDate.now().plusDays(30), trialSubscription.getTrialEndsOn());
    }
}
