package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformChargeNoticeDeliveryPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private PlatformChargeRepository platformChargeRepository;
    @Autowired
    private PlatformChargeNoticeRepository platformChargeNoticeRepository;

    @Test
    @Transactional
    void claimsPendingDeliveryAndPersistsItsDeliveredState() {
        PlatformCharge charge = charge();
        PlatformChargeNotice notice = PlatformChargeNotice.create(
                charge.getCompanyId(), charge.getId(), PlatformChargeNoticeType.DUE_DATE,
                "billing-" + UUID.randomUUID() + "@example.com", LocalDate.of(2026, 8, 22)
        );
        assertTrue(platformChargeNoticeRepository.saveIfAbsent(notice));

        Instant attemptedAt = Instant.parse("2026-08-22T12:00:00Z");
        List<PlatformChargeNotice> claimed = platformChargeNoticeRepository.claimPendingDeliveries(
                attemptedAt, attemptedAt.minusSeconds(900), 10
        );

        assertEquals(1, claimed.size());
        assertEquals(PlatformChargeNoticeStatus.DELIVERING, claimed.getFirst().getStatus());
        PlatformChargeNotice deliveringNotice = platformChargeNoticeRepository.findByIdForUpdate(notice.getId()).orElseThrow();
        deliveringNotice.markDelivered(attemptedAt.plusSeconds(5));
        platformChargeNoticeRepository.save(deliveringNotice);

        PlatformChargeNotice persisted = platformChargeNoticeRepository.findByChargeId(charge.getId()).getFirst();
        assertEquals(PlatformChargeNoticeStatus.DELIVERED, persisted.getStatus());
        assertEquals(1, persisted.getDeliveryAttempts());
        assertEquals(attemptedAt.plusSeconds(5), persisted.getDeliveredAt());
    }

    @Test
    @Transactional
    void retriesAFailedDeliveryOnlyAfterTheConfiguredWindow() {
        PlatformCharge charge = charge();
        PlatformChargeNotice notice = PlatformChargeNotice.create(
                charge.getCompanyId(), charge.getId(), PlatformChargeNoticeType.DUE_DATE,
                "retry-" + UUID.randomUUID() + "@example.com", LocalDate.of(2026, 8, 22)
        );
        assertTrue(platformChargeNoticeRepository.saveIfAbsent(notice));
        Instant firstAttemptAt = Instant.parse("2026-08-22T12:00:00Z");
        PlatformChargeNotice firstAttempt = platformChargeNoticeRepository.claimPendingDeliveries(
                firstAttemptAt, firstAttemptAt.minusSeconds(900), 10
        ).getFirst();
        firstAttempt.markDeliveryFailed("SmtpException");
        platformChargeNoticeRepository.save(firstAttempt);

        List<PlatformChargeNotice> prematureRetry = platformChargeNoticeRepository.claimPendingDeliveries(
                firstAttemptAt.plusSeconds(300), firstAttemptAt.minusSeconds(600), 10
        );
        List<PlatformChargeNotice> eligibleRetry = platformChargeNoticeRepository.claimPendingDeliveries(
                firstAttemptAt.plusSeconds(960), firstAttemptAt.plusSeconds(60), 10
        );

        assertTrue(prematureRetry.isEmpty());
        assertEquals(1, eligibleRetry.size());
        assertEquals(2, eligibleRetry.getFirst().getDeliveryAttempts());
    }

    private PlatformCharge charge() {
        Company company = companyRepository.save(Company.create(
                "Construtora Notificacao Ltda.", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "notice-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                company.getId(),
                new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.of(2026, 8, 22),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
        return platformChargeRepository.save(PlatformCharge.create(
                company.getId(), subscription.getId(), PROVIDER, "notice-key-" + UUID.randomUUID(),
                "cus_123", "pay_" + UUID.randomUUID(), PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.of(2026, 8, 22), PlatformChargeStatus.PENDING
        ));
    }
}
