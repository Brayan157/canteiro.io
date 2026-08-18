package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionDunningRunResult;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionDunningService;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDunningPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlatformChargeRepository platformChargeRepository;

    @Autowired
    private CompanySubscriptionAccessRepository companySubscriptionAccessRepository;

    @Autowired
    private PlatformChargeNoticeRepository platformChargeNoticeRepository;

    @Autowired
    private SubscriptionDunningService subscriptionDunningService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void persistsBlockedAccessAndNoticeIntentsOnlyOnceForAnOverdueCharge() {
        Company company = companyRepository.save(Company.create(
                "Construtora Dunning Ltda.", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "dunning-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                company.getId(),
                new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
        PlatformCharge charge = platformChargeRepository.save(PlatformCharge.create(
                company.getId(), subscription.getId(), PROVIDER, "dunning-key-" + UUID.randomUUID(),
                "cus_123", "pay_" + UUID.randomUUID(), PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC).minusDays(12), PlatformChargeStatus.PENDING
        ));

        SubscriptionDunningRunResult firstRun = subscriptionDunningService.evaluateCompany(company.getId());
        SubscriptionDunningRunResult secondRun = subscriptionDunningService.evaluateCompany(company.getId());

        assertEquals(new SubscriptionDunningRunResult(1, 1, 4), firstRun);
        assertEquals(new SubscriptionDunningRunResult(1, 0, 0), secondRun);
        assertEquals(SubscriptionAccessLevel.BLOCKED, companySubscriptionAccessRepository.findByCompanyId(company.getId())
                .orElseThrow()
                .getAccessLevel());
        assertEquals(4, platformChargeNoticeRepository.findByChargeId(charge.getId()).size());
        assertEquals(5, auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 10))
                .getContent()
                .size());
        assertTrue(auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 10))
                .getContent()
                .stream()
                .allMatch(event -> event.getActorType() == AuditActorType.SYSTEM && event.getActorUserId() == null));
    }
}
