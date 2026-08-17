package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionDunningPolicy;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySubscriptionAccessServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CHARGE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock
    private PlatformChargeRepository platformChargeRepository;
    @Mock
    private TrustUnlockRepository trustUnlockRepository;

    @Test
    void restoresRestrictionAsSoonAsNoTrustUnlockIsActive() {
        PlatformCharge charge = PlatformCharge.rehydrate(
                CHARGE_ID, COMPANY_ID, UUID.randomUUID(), new PaymentGatewayProviderCode("TEST_GATEWAY"),
                "charge-key", "cus_123", "pay_123", PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.of(2026, 8, 12), PlatformChargeStatus.PENDING, null, null
        );
        when(platformChargeRepository.findOutstandingByCompanyId(COMPANY_ID)).thenReturn(List.of(charge));
        when(trustUnlockRepository.findActiveChargeIdsByCompanyId(COMPANY_ID, NOW)).thenReturn(Set.of(CHARGE_ID));

        assertEquals(SubscriptionAccessLevel.FULL, service().resolveAccessLevel(COMPANY_ID));

        when(trustUnlockRepository.findActiveChargeIdsByCompanyId(COMPANY_ID, NOW)).thenReturn(Set.of());

        assertEquals(SubscriptionAccessLevel.BLOCKED, service().resolveAccessLevel(COMPANY_ID));
    }

    private CompanySubscriptionAccessService service() {
        return new CompanySubscriptionAccessService(
                platformChargeRepository,
                trustUnlockRepository,
                new SubscriptionDunningPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
