package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvoiceSettlementServiceTest {
    @Test void approvalRechecksTheUtcCalendarBeforeAnyLockOrFinancialWrite() {
        var ledger = mock(InvoiceSettlementRepository.class); var titles = mock(ReceivableRepository.class); var settlements = mock(ReceivableSettlementRepository.class);
        var origins = mock(ReceivableOriginRepository.class); var changes = mock(ChangeRequestService.class); var requests = mock(ChangeRequestRepository.class); var audits = mock(AuditEventRecorder.class);
        var service = new InvoiceSettlementService(mock(TenantContextHolder.class), mock(AccessAuthorizationService.class), mock(ContractRepository.class), mock(InvoiceRepository.class),
                titles, settlements, origins, ledger, mock(MeasurementContractCompositionRepository.class), mock(CalculateReceivableBalanceUseCase.class), changes, requests, audits,
                Clock.fixed(Instant.parse("2026-10-27T23:59:00Z"), ZoneOffset.UTC), new ObjectMapper());
        var request = mock(ChangeRequest.class);
        when(request.getSnapshot()).thenReturn(new ChangeRequestSnapshot(null, new AuditPayload(Map.of(
                "operationId", UUID.randomUUID().toString(), "receivableId", UUID.randomUUID().toString(), "receivedOn", "2026-10-28", "amount", "1.00", "paymentMethod", "PIX",
                "items", List.of(Map.of("invoiceId", UUID.randomUUID().toString(), "invoiceItemId", UUID.randomUUID().toString(), "billingAllocationId", UUID.randomUUID().toString(), "amount", "1.00"))))));
        var error = assertThrows(ApiException.class, () -> service.apply(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus()); assertEquals("receivedOn", error.getViolations().getFirst().field());
        verifyNoInteractions(ledger, titles, settlements, origins, changes, requests, audits);
    }
}
