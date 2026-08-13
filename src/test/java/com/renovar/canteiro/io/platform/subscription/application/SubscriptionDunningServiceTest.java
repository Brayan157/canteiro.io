package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionDunningPolicy;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionDunningServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CHARGE_ID = UUID.randomUUID();
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 22);
    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Mock
    private PlatformChargeRepository platformChargeRepository;
    @Mock
    private CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    @Mock
    private PlatformChargeNoticeRepository platformChargeNoticeRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AuditEventRecorder auditEventRecorder;

    @Test
    void blocksTheCompanyAndCreatesEachReachedNoticeExactlyOnce() {
        PlatformCharge charge = overdueCharge();
        when(platformChargeRepository.findOutstandingByCompanyIdForDunning(COMPANY_ID)).thenReturn(List.of(charge));
        when(companySubscriptionAccessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.empty());
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(companySubscriptionAccessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformChargeNoticeRepository.saveIfAbsent(any())).thenReturn(true);

        SubscriptionDunningRunResult result = service().evaluateCompany(COMPANY_ID);

        assertEquals(new SubscriptionDunningRunResult(1, 1, 4), result);
        ArgumentCaptor<CompanySubscriptionAccess> accessCaptor = ArgumentCaptor.forClass(CompanySubscriptionAccess.class);
        verify(companySubscriptionAccessRepository).save(accessCaptor.capture());
        assertEquals(SubscriptionAccessLevel.BLOCKED, accessCaptor.getValue().getAccessLevel());
        assertEquals(CHARGE_ID, accessCaptor.getValue().getRestrictionChargeId());
        ArgumentCaptor<PlatformChargeNotice> noticeCaptor = ArgumentCaptor.forClass(PlatformChargeNotice.class);
        verify(platformChargeNoticeRepository, org.mockito.Mockito.times(4)).saveIfAbsent(noticeCaptor.capture());
        assertEquals(
                List.of(
                        PlatformChargeNoticeType.DUE_DATE,
                        PlatformChargeNoticeType.READ_ONLY,
                        PlatformChargeNoticeType.DELINQUENT,
                        PlatformChargeNoticeType.BLOCKED
                ),
                noticeCaptor.getAllValues().stream().map(PlatformChargeNotice::getNoticeType).sorted().toList()
        );
        verify(auditEventRecorder, org.mockito.Mockito.times(5)).recordSystemAction(
                org.mockito.ArgumentMatchers.any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void isIdempotentWhenTheAccessAndNoticesAlreadyExist() {
        PlatformCharge charge = overdueCharge();
        CompanySubscriptionAccess existingAccess = CompanySubscriptionAccess.create(
                COMPANY_ID, SubscriptionAccessLevel.BLOCKED, CHARGE_ID, CURRENT_DATE
        );
        when(platformChargeRepository.findOutstandingByCompanyIdForDunning(COMPANY_ID)).thenReturn(List.of(charge));
        when(companySubscriptionAccessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(existingAccess));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(platformChargeNoticeRepository.saveIfAbsent(any())).thenReturn(false);

        SubscriptionDunningRunResult result = service().evaluateCompany(COMPANY_ID);

        assertEquals(new SubscriptionDunningRunResult(1, 0, 0), result);
        verify(companySubscriptionAccessRepository).lockCompanyId(COMPANY_ID);
        verify(companySubscriptionAccessRepository, never()).save(any());
        verify(auditEventRecorder, never()).recordSystemAction(
                org.mockito.ArgumentMatchers.any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void restoresFullAccessWhenNoChargeRemainsOpen() {
        CompanySubscriptionAccess existingAccess = CompanySubscriptionAccess.create(
                COMPANY_ID, SubscriptionAccessLevel.BLOCKED, CHARGE_ID, CURRENT_DATE.minusDays(1)
        );
        when(platformChargeRepository.findOutstandingByCompanyIdForDunning(COMPANY_ID)).thenReturn(List.of());
        when(companySubscriptionAccessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(existingAccess));
        when(companySubscriptionAccessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDunningRunResult result = service().evaluateCompany(COMPANY_ID);

        assertEquals(new SubscriptionDunningRunResult(1, 1, 0), result);
        ArgumentCaptor<CompanySubscriptionAccess> accessCaptor = ArgumentCaptor.forClass(CompanySubscriptionAccess.class);
        verify(companySubscriptionAccessRepository).save(accessCaptor.capture());
        assertEquals(SubscriptionAccessLevel.FULL, accessCaptor.getValue().getAccessLevel());
        assertNull(accessCaptor.getValue().getRestrictionChargeId());
        verify(companyRepository, never()).findById(COMPANY_ID);
    }

    private SubscriptionDunningCompanyService service() {
        return new SubscriptionDunningCompanyService(
                platformChargeRepository,
                companySubscriptionAccessRepository,
                platformChargeNoticeRepository,
                companyRepository,
                new SubscriptionDunningPolicy(),
                auditEventRecorder,
                Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private PlatformCharge overdueCharge() {
        return PlatformCharge.rehydrate(
                CHARGE_ID, COMPANY_ID, UUID.randomUUID(), PROVIDER, "charge-key", "cus_123", "pay_123",
                PaymentGatewayBillingMethod.PIX, new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                PlatformChargeStatus.PENDING, null, null
        );
    }

    private Company company() {
        return Company.rehydrate(
                COMPANY_ID, "Construtora Dunning", null, "DOC-DUNNING", "billing@example.com", null,
                null, null, true, null, null
        );
    }
}
