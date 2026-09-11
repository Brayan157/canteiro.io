package com.renovar.canteiro.io.bootstrap;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Local-only fixture for exercising the platform trust-unlock screen.
 * It deliberately cannot be enabled outside the {@code local} profile.
 */
@Profile("local")
@Configuration
@ConditionalOnProperty(prefix = "bootstrap.trust-unlock-demo", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalTrustUnlockDemoProperties.class)
@RequiredArgsConstructor
public class LocalTrustUnlockDemoBootstrap implements ApplicationRunner {

    private static final String COMPANY_DOCUMENT = "LOCALTRUSTDEMO01";
    private static final String COMPANY_EMAIL = "trust-unlock-demo@canteiro.local";
    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("LOCAL_FIXTURE");
    private static final String IDEMPOTENCY_KEY = "local-trust-unlock-demo-v1";
    private static final BigDecimal AMOUNT = new BigDecimal("199.00");

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlatformChargeRepository platformChargeRepository;
    private final CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    private final LocalTrustUnlockDemoProperties properties;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Company company = companyRepository.findByDocument(COMPANY_DOCUMENT)
                .orElseGet(() -> companyRepository.save(Company.create(
                        "Empresa Demonstração Desbloqueio Ltda.",
                        "Demo Desbloqueio",
                        COMPANY_DOCUMENT,
                        COMPANY_EMAIL,
                        null,
                        null,
                        null
                )));

        Subscription subscription = subscriptionRepository.findByCompanyId(company.getId()).stream()
                .findFirst()
                .orElseGet(() -> subscriptionRepository.save(Subscription.create(
                        company.getId(),
                        new CatalogPriceQuote(
                                Set.of(UUID.randomUUID()),
                                AMOUNT,
                                LocalDate.now(clock),
                                CatalogPricingSource.INDIVIDUAL_PLANS,
                                null
                        )
                )));

        PlatformCharge charge = platformChargeRepository.findByProviderAndIdempotencyKey(PROVIDER, IDEMPOTENCY_KEY)
                .orElseGet(() -> platformChargeRepository.save(PlatformCharge.create(
                        company.getId(),
                        subscription.getId(),
                        PROVIDER,
                        IDEMPOTENCY_KEY,
                        "local-trust-unlock-demo-customer",
                        "local-trust-unlock-demo-charge",
                        PaymentGatewayBillingMethod.PIX,
                        AMOUNT,
                        LocalDate.now(clock).minusDays(11),
                        PlatformChargeStatus.OVERDUE
                )));

        companySubscriptionAccessRepository.findByCompanyId(company.getId()).orElseGet(() ->
                companySubscriptionAccessRepository.save(CompanySubscriptionAccess.create(
                        company.getId(),
                        SubscriptionAccessLevel.BLOCKED,
                        charge.getId(),
                        LocalDate.now(clock)
                ))
        );

        createCompanyUserIfAbsent(company);
    }

    private void createCompanyUserIfAbsent(Company company) {
        String email = requireValue(properties.companyUserEmail(), "bootstrap.trust-unlock-demo.company-user-email");
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        String password = requireValue(
                properties.companyUserPassword(),
                "bootstrap.trust-unlock-demo.company-user-password"
        );
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(password), clock.instant());
        User savedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(savedUser.getId(), company.getId()));
    }

    private String requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be set when local trust-unlock demo is enabled");
        }
        return value.trim();
    }
}
