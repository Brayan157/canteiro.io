package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivableSettlementServiceTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private ReceivableRepository receivableRepository;
    @Mock private ReceivableSettlementRepository receivableSettlementRepository;
    @Mock private TaxObligationRepository taxObligationRepository;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void recordsPartialSettlementAndMarksTheReceivableAsPartial() {
        Fixture fixture = fixture(new BigDecimal("0.00"), LocalDate.of(2026, 8, 15));

        ReceivableSettlementResult result = fixture.service().settle(command(fixture.receivableId(), "40.00"));

        assertEquals(ReceivableStatus.PARTIALLY_RECEIVED, result.receivableStatus());
        assertEquals(new BigDecimal("60.00"), result.balance().outstandingAmount());
        assertEquals(ReceivableSettlementStatus.CONFIRMED, result.settlement().getStatus());
    }

    @Test
    void recordsFinalSettlementAndMarksTheReceivableAsReceived() {
        Fixture fixture = fixture(new BigDecimal("40.00"), LocalDate.of(2026, 8, 16));

        ReceivableSettlementResult result = fixture.service().settle(command(fixture.receivableId(), "60.00"));

        assertEquals(ReceivableStatus.RECEIVED, result.receivableStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), result.balance().outstandingAmount());
    }

    @Test
    void retainsOverdueStatusWhenAPartialSettlementOccursAfterTheDueDate() {
        Fixture fixture = fixture(new BigDecimal("0.00"), LocalDate.of(2026, 8, 20));

        ReceivableSettlementResult result = fixture.service().settle(command(fixture.receivableId(), "10.00"));

        assertEquals(ReceivableStatus.OVERDUE, result.receivableStatus());
        assertEquals(new BigDecimal("90.00"), result.balance().outstandingAmount());
    }

    @Test
    void rejectsSettlementAboveTheOutstandingAmount() {
        Fixture fixture = fixture(new BigDecimal("90.00"), LocalDate.of(2026, 8, 15));

        assertThrows(IllegalStateException.class, () -> fixture.service().settle(command(fixture.receivableId(), "20.00")));
    }

    private Fixture fixture(BigDecimal confirmedAmount, LocalDate currentDate) {
        UUID companyId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        Receivable receivable = Receivable.rehydrate(
                receivableId, companyId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "REC-001", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00"), ReceivableStatus.OPEN,
                Instant.now(), Instant.now()
        );
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(receivableRepository.findByIdAndCompanyId(receivableId, companyId)).thenReturn(Optional.of(receivable));
        when(receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivableId, companyId))
                .thenReturn(Optional.of(receivable));
        when(receivableSettlementRepository.confirmedAmountByReceivableIdAndCompanyId(receivableId, companyId))
                .thenReturn(confirmedAmount);
        when(taxObligationRepository.registeredWithheldAmountByReceivableIdAndCompanyId(receivableId, companyId))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(receivableSettlementRepository.save(any())).thenAnswer(invocation -> {
            ReceivableSettlement settlement = invocation.getArgument(0);
            return ReceivableSettlement.rehydrate(UUID.randomUUID(), settlement.getCompanyId(), settlement.getReceivableId(),
                    settlement.getContractId(), settlement.getReceivedOn(), settlement.getAmount(), settlement.getEntryType(),
                    settlement.getReversalOfSettlementId(), settlement.getPaymentMethod(), settlement.getNote(),
                    settlement.getStatus(), Instant.now(), Instant.now());
        });
        lenient().when(receivableRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Clock clock = Clock.fixed(currentDate.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new Fixture(receivableId, new ReceivableSettlementService(
                tenantContextHolder, accessAuthorizationService, receivableRepository, receivableSettlementRepository,
                new CalculateReceivableBalanceUseCase(receivableRepository, receivableSettlementRepository, taxObligationRepository),
                changeRequestService, auditEventRecorder, clock
        ));
    }

    private CreateReceivableSettlementCommand command(UUID receivableId, String amount) {
        return new CreateReceivableSettlementCommand(
                receivableId, LocalDate.of(2026, 8, 10), new BigDecimal(amount),
                ReceivableSettlementPaymentMethod.PIX, null, null
        );
    }

    private record Fixture(UUID receivableId, ReceivableSettlementService service) {
    }
}
