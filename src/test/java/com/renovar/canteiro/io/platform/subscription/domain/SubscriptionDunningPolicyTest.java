package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDunningPolicyTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");
    private final SubscriptionDunningPolicy policy = new SubscriptionDunningPolicy();

    @Test
    void createsTheDueDateNoticeButKeepsFullAccessOnTheDueDate() {
        PlatformCharge charge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 12));

        SubscriptionDunningAssessment assessment = policy.assess(List.of(charge), LocalDate.of(2026, 8, 12));

        assertEquals(SubscriptionAccessLevel.FULL, assessment.accessLevel());
        assertEquals(null, assessment.restrictionChargeId());
        assertEquals(List.of(PlatformChargeNoticeType.DUE_DATE), policy.noticesFor(
                charge, LocalDate.of(2026, 8, 12)
        ).stream().sorted().toList());
    }

    @Test
    void escalatesAccessOnDPlusOneDPlusFiveAndDPlusTen() {
        PlatformCharge charge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 12));

        assertEquals(SubscriptionAccessLevel.READ_ONLY, policy.assess(
                List.of(charge), LocalDate.of(2026, 8, 13)
        ).accessLevel());
        assertEquals(SubscriptionAccessLevel.READ_ONLY, policy.assess(
                List.of(charge), LocalDate.of(2026, 8, 16)
        ).accessLevel());
        assertEquals(SubscriptionAccessLevel.DELINQUENT_READ_ONLY, policy.assess(
                List.of(charge), LocalDate.of(2026, 8, 17)
        ).accessLevel());
        assertEquals(SubscriptionAccessLevel.BLOCKED, policy.assess(
                List.of(charge), LocalDate.of(2026, 8, 22)
        ).accessLevel());
        assertTrue(policy.noticesFor(charge, LocalDate.of(2026, 8, 22))
                .contains(PlatformChargeNoticeType.BLOCKED));
    }

    @Test
    void usesTheOldestOpenChargeAndIgnoresClosedCharges() {
        PlatformCharge oldestCharge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 12));
        PlatformCharge newestCharge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 21));
        PlatformCharge confirmedCharge = charge(PlatformChargeStatus.CONFIRMED, LocalDate.of(2026, 8, 1));

        SubscriptionDunningAssessment assessment = policy.assess(
                List.of(newestCharge, confirmedCharge, oldestCharge), LocalDate.of(2026, 8, 22)
        );

        assertEquals(SubscriptionAccessLevel.BLOCKED, assessment.accessLevel());
        assertEquals(oldestCharge.getId(), assessment.restrictionChargeId());
        assertFalse(policy.noticesFor(confirmedCharge, LocalDate.of(2026, 8, 22)).contains(
                PlatformChargeNoticeType.DUE_DATE
        ));
    }

    @Test
    void skipsTrustedChargesButKeepsTheNextOverdueChargeRestrictingAccess() {
        PlatformCharge trustedCharge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 12));
        PlatformCharge nextCharge = charge(PlatformChargeStatus.PENDING, LocalDate.of(2026, 8, 21));

        SubscriptionDunningAssessment assessment = policy.assess(
                List.of(trustedCharge, nextCharge), LocalDate.of(2026, 8, 22), Set.of(trustedCharge.getId())
        );

        assertEquals(SubscriptionAccessLevel.READ_ONLY, assessment.accessLevel());
        assertEquals(nextCharge.getId(), assessment.restrictionChargeId());
    }

    private PlatformCharge charge(PlatformChargeStatus status, LocalDate dueDate) {
        return PlatformCharge.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROVIDER,
                UUID.randomUUID().toString(), "cus_123", "pay_" + UUID.randomUUID(),
                PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"), dueDate, status, null, null
        );
    }
}
