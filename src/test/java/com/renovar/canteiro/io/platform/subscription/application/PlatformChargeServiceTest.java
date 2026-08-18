package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformChargeServiceTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlatformChargeRepository platformChargeRepository;

    @Test
    void createsAndPersistsAChargeWithTheSubscriptionCompany() {
        UUID subscriptionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PaymentGatewayChargeRequest request = request(subscriptionId, "charge-key");
        Subscription subscription = subscription(subscriptionId, companyId);
        when(platformChargeRepository.findByProviderAndIdempotencyKey(
                PROVIDER, "charge-key"
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(paymentGateway.createCharge(request)).thenReturn(
                new PaymentGatewayChargeResult("pay_123", PaymentGatewayChargeStatus.PENDING)
        );
        when(platformChargeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformChargeService service = service();

        PlatformCharge charge = service.createCharge(request);

        assertEquals(companyId, charge.getCompanyId());
        assertEquals("pay_123", charge.getExternalChargeId());
        assertEquals(PlatformChargeStatus.PENDING, charge.getStatus());
        verify(platformChargeRepository).lockIdempotencyKey(PROVIDER, "charge-key");
        verify(paymentGateway).createCharge(request);
        verify(platformChargeRepository).save(any());
    }

    @Test
    void returnsTheExistingChargeWithoutCallingTheGatewayAgain() {
        UUID subscriptionId = UUID.randomUUID();
        PlatformCharge existing = charge(subscriptionId, "charge-key");
        PaymentGatewayChargeRequest request = request(subscriptionId, "charge-key");
        when(platformChargeRepository.findByProviderAndIdempotencyKey(
                PROVIDER, "charge-key"
        )).thenReturn(Optional.of(existing));

        PlatformCharge result = service().createCharge(request);

        assertSame(existing, result);
        verify(paymentGateway, never()).createCharge(any());
        verify(platformChargeRepository, never()).save(any());
    }

    @Test
    void rejectsReusingAKeyForDifferentChargeData() {
        UUID subscriptionId = UUID.randomUUID();
        PlatformCharge existing = charge(subscriptionId, "charge-key");
        PaymentGatewayChargeRequest changedRequest = new PaymentGatewayChargeRequest(
                subscriptionId, "cus_123", new BigDecimal("199.90"), LocalDate.of(2026, 9, 11),
                PaymentGatewayBillingMethod.PIX, "charge-key"
        );
        when(platformChargeRepository.findByProviderAndIdempotencyKey(
                PROVIDER, "charge-key"
        )).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service().createCharge(changedRequest));
        verify(paymentGateway, never()).createCharge(any());
    }

    private PlatformChargeService service() {
        when(paymentGateway.providerCode()).thenReturn(PROVIDER);
        return new PlatformChargeService(paymentGateway, subscriptionRepository, platformChargeRepository);
    }

    private PaymentGatewayChargeRequest request(UUID subscriptionId, String key) {
        return new PaymentGatewayChargeRequest(
                subscriptionId, "cus_123", new BigDecimal("99.90"), LocalDate.of(2026, 9, 11),
                PaymentGatewayBillingMethod.PIX, key
        );
    }

    private PlatformCharge charge(UUID subscriptionId, String key) {
        return PlatformCharge.create(
                UUID.randomUUID(), subscriptionId, PROVIDER, key, "cus_123", "pay_123",
                PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"), LocalDate.of(2026, 9, 11),
                PlatformChargeStatus.PENDING
        );
    }

    private Subscription subscription(UUID id, UUID companyId) {
        return Subscription.rehydrate(
                id, companyId, com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus.TRIAL,
                new BigDecimal("99.90"), CatalogPricingSource.INDIVIDUAL_PLANS, null,
                LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 11), null, null
        );
    }
}
