package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal decisions only: never exposes cash, tax or invoice data through service permissions. */
public interface ContractClosureFinancialProvider {
    void lockInputs(UUID companyId, UUID contractId);
    boolean hasContractCommitments(UUID companyId, UUID contractId);
    Map<UUID, Position> positions(UUID companyId, UUID contractId, List<ContractService> projectedServices);

    record Position(String completionReason, String cancellationReason) { }
}
