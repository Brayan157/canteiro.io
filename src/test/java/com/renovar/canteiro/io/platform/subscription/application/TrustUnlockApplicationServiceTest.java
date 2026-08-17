package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustUnlockApplicationServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CHARGE_ID = UUID.randomUUID();
    private static final UUID OWNER_USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Mock
    private PlatformOperatorContextHolder platformOperatorContextHolder;
    @Mock
    private CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    @Mock
    private PlatformChargeRepository platformChargeRepository;
    @Mock
    private TrustUnlockRepository trustUnlockRepository;
    @Mock
    private SubscriptionDunningCompanyService subscriptionDunningCompanyService;
    @Mock
    private AuditEventRecorder auditEventRecorder;

    @Test
    void grantsAnUnlockAuditsItsAuthorAndReevaluatesCompanyAccess() {
        PlatformCharge charge = overdueCharge();
        when(platformOperatorContextHolder.currentOperator()).thenReturn(Optional.of(owner()));
        when(platformChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(platformChargeRepository.findByIdForUpdate(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(trustUnlockRepository.countByChargeId(CHARGE_ID)).thenReturn(0L);
        when(trustUnlockRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        TrustUnlock result = service().grant(new GrantTrustUnlockCommand(
                CHARGE_ID, "Customer committed to paying this week", NOW.plusSeconds(86_400)
        ));

        assertEquals(CHARGE_ID, result.getChargeId());
        assertEquals(OWNER_USER_ID, result.getGrantedByUserId());
        ArgumentCaptor<TrustUnlock> unlockCaptor = ArgumentCaptor.forClass(TrustUnlock.class);
        verify(trustUnlockRepository).save(unlockCaptor.capture());
        assertEquals(NOW, unlockCaptor.getValue().getStartsAt());
        verify(companySubscriptionAccessRepository).lockCompanyId(COMPANY_ID);
        verify(subscriptionDunningCompanyService).reevaluateCompany(COMPANY_ID);
        verify(auditEventRecorder).recordPlatformAction(
                eq(COMPANY_ID), eq(AuditModule.PLATFORM), eq(AuditAction.CREATE), eq("TrustUnlock"),
                eq(result.getId()), any(), any(), any()
        );
    }

    @Test
    void rejectsAThirdUnlockForTheSameCharge() {
        PlatformCharge charge = overdueCharge();
        when(platformOperatorContextHolder.currentOperator()).thenReturn(Optional.of(owner()));
        when(platformChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(platformChargeRepository.findByIdForUpdate(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(trustUnlockRepository.countByChargeId(CHARGE_ID)).thenReturn(2L);

        assertThrows(ApiException.class, () -> service().grant(new GrantTrustUnlockCommand(
                CHARGE_ID, "Third request", NOW.plusSeconds(86_400)
        )));

        verify(trustUnlockRepository, never()).save(any());
        verify(subscriptionDunningCompanyService, never()).reevaluateCompany(COMPANY_ID);
    }

    @Test
    void rejectsPlatformSupportFromGrantingAnUnlock() {
        when(platformOperatorContextHolder.currentOperator()).thenReturn(Optional.of(new PlatformOperatorContext(
                OWNER_USER_ID, UUID.randomUUID(), PlatformUserRole.PLATFORM_SUPPORT
        )));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service().grant(
                new GrantTrustUnlockCommand(CHARGE_ID, "Unauthorized", NOW.plusSeconds(86_400))
        ));

        verify(platformChargeRepository, never()).findById(any());
    }

    private TrustUnlockApplicationService service() {
        return new TrustUnlockApplicationService(
                platformOperatorContextHolder,
                companySubscriptionAccessRepository,
                platformChargeRepository,
                trustUnlockRepository,
                subscriptionDunningCompanyService,
                auditEventRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PlatformOperatorContext owner() {
        return new PlatformOperatorContext(OWNER_USER_ID, UUID.randomUUID(), PlatformUserRole.PLATFORM_OWNER);
    }

    private PlatformCharge overdueCharge() {
        return PlatformCharge.rehydrate(
                CHARGE_ID, COMPANY_ID, UUID.randomUUID(), PROVIDER, "charge-key", "cus_123", "pay_123",
                PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                PlatformChargeStatus.PENDING, null, null
        );
    }

    private TrustUnlock persisted(TrustUnlock candidate) {
        return TrustUnlock.rehydrate(
                UUID.randomUUID(), candidate.getCompanyId(), candidate.getChargeId(), candidate.getGrantedByUserId(),
                candidate.getReason(), candidate.getStartsAt(), candidate.getExpiresAt(), NOW, NOW
        );
    }
}
