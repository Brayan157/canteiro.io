package com.renovar.canteiro.io.receivables.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateNetBillingCommand(UUID operationId, UUID contractId, String capacityVersion, String reference,
        String description, LocalDate issuedOn, LocalDate dueOn, UUID financialGroupId, List<Line> allocations, String justification,
        List<ServiceLine> serviceIntent) {
    public CreateNetBillingCommand(UUID operationId, UUID contractId, String capacityVersion, String reference,
            String description, LocalDate issuedOn, LocalDate dueOn, UUID financialGroupId, List<Line> allocations, String justification) {
        this(operationId, contractId, capacityVersion, reference, description, issuedOn, dueOn, financialGroupId, allocations, justification, null);
    }
    public record Line(UUID contractServiceId, UUID measurementContractCompositionId, BigDecimal netAmount) { }
    public enum ServiceAmountMode { ALL_AVAILABLE, PARTIAL }
    public record ServiceLine(UUID contractServiceId, ServiceAmountMode mode, BigDecimal netAmount) { }
    public CreateNetBillingCommand withAllocations(List<Line> resolved) {
        return new CreateNetBillingCommand(operationId, contractId, capacityVersion, reference, description, issuedOn, dueOn,
                financialGroupId, resolved, justification, serviceIntent);
    }
}
