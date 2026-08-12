package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhook;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookEventType;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private PlatformChargeRepository platformChargeRepository;
    @Autowired
    private PaymentGatewayEventRepository paymentGatewayEventRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistsChargeAndGatewayEventAndRejectsDuplicateExternalKeys() {
        transactionTemplate.executeWithoutResult(ignored -> {
            platformChargeRepository.lockIdempotencyKey(PROVIDER, "charge-key");
            paymentGatewayEventRepository.lockExternalEventId(PROVIDER, "evt_123");
        });
        Company company = company();
        Subscription subscription = subscription(company.getId());
        PlatformCharge charge = platformChargeRepository.save(charge(
                company.getId(), subscription.getId(), "charge-key", "pay_123"
        ));
        PaymentGatewayEvent event = paymentGatewayEventRepository.save(PaymentGatewayEvent.receive(
                PROVIDER,
                webhook("evt_123", "pay_123"),
                Instant.parse("2026-08-12T20:00:00Z")
        ));

        assertEquals(company.getId(), charge.getCompanyId());
        assertEquals(PlatformChargeStatus.PENDING, charge.getStatus());
        assertEquals(PaymentGatewayEventStatus.RECEIVED, event.getStatus());
        assertEquals("RECEIVED", event.getAttributes().get("status"));
        assertThrows(DataIntegrityViolationException.class, () -> platformChargeRepository.save(charge(
                company.getId(), subscription.getId(), "charge-key", "pay_other"
        )));
        assertThrows(DataIntegrityViolationException.class, () -> paymentGatewayEventRepository.save(
                PaymentGatewayEvent.receive(
                        PROVIDER,
                        webhook("evt_123", "pay_other"),
                        Instant.parse("2026-08-12T20:01:00Z")
                )
        ));
    }

    @Test
    void rejectsChargeWhoseCompanyDoesNotOwnTheSubscription() {
        Company subscriptionCompany = company();
        Company otherCompany = company();
        Subscription subscription = subscription(subscriptionCompany.getId());

        assertThrows(DataIntegrityViolationException.class, () -> platformChargeRepository.save(charge(
                otherCompany.getId(), subscription.getId(), "cross-company-key", "pay_cross_company"
        )));
    }

    private Company company() {
        return companyRepository.save(Company.create(
                "Construtora Cobrança", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "charge-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
    }

    private Subscription subscription(UUID companyId) {
        return subscriptionRepository.save(Subscription.create(
                companyId,
                new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
    }

    private PlatformCharge charge(UUID companyId, UUID subscriptionId, String key, String externalId) {
        return PlatformCharge.create(
                companyId, subscriptionId, PROVIDER, key, "cus_123", externalId,
                PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"), LocalDate.of(2026, 9, 11),
                PlatformChargeStatus.PENDING
        );
    }

    private PaymentGatewayWebhook webhook(String eventId, String chargeId) {
        return new PaymentGatewayWebhook(
                eventId, chargeId, PaymentGatewayWebhookEventType.CHARGE_CONFIRMED,
                Instant.parse("2026-08-12T19:45:03Z"), Map.of("status", "RECEIVED")
        );
    }
}
