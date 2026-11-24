package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractLifecycleServiceTest {
    @Test void separatesMissingPaymentFromAProvenSettledService() {
        var service = completed();
        assertEquals("CONTRACT_HAS_UNSETTLED_SERVICES", reason(List.of(service),
                Map.of(service.getId(), new ContractClosureFinancialProvider.Position("SERVICE_NOT_FULLY_SETTLED", null))));
        assertNull(reason(List.of(service), Map.of(service.getId(), new ContractClosureFinancialProvider.Position(null, null))));
    }

    @Test void reconciliationTakesPrecedenceRegardlessOfTheOrderOfServices() {
        var unpaid = completed();
        var historical = completed();
        var positions = Map.of(unpaid.getId(), new ContractClosureFinancialProvider.Position("SERVICE_NOT_FULLY_SETTLED", null),
                historical.getId(), new ContractClosureFinancialProvider.Position("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED", null));
        for (var order : List.of(List.of(unpaid, historical), List.of(historical, unpaid)))
            assertEquals("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED", reason(order, positions));
    }

    @Test void creditAndUnavailablePositionsRequireReconciliationInsteadOfInventingMissingCash() {
        var service = completed();
        assertEquals("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED", reason(List.of(service),
                Map.of(service.getId(), new ContractClosureFinancialProvider.Position("SERVICE_CREDIT_REQUIRES_RECONCILIATION", null))));
        assertEquals("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED", reason(List.of(service), Map.of()));
    }

    private String reason(List<ContractService> services, Map<UUID, ContractClosureFinancialProvider.Position> positions) {
        return ContractLifecycleService.contractFinancialReason(ContractStatus.COMPLETED, services, positions);
    }

    private ContractService completed() {
        var service = mock(ContractService.class);
        when(service.getId()).thenReturn(UUID.randomUUID());
        when(service.getStatus()).thenReturn(ContractServiceStatus.COMPLETED);
        return service;
    }
}
