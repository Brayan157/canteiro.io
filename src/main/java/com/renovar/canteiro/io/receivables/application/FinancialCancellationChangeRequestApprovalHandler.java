package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
class FinancialCancellationChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private static final Set<String> SUPPORTED_TYPES = Set.of("BillingInstallment", "Invoice", "ReceivableSettlement");
    private final FinancialCancellationService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getOperation() == ChangeRequestOperation.CANCEL && SUPPORTED_TYPES.contains(request.getEntityType());
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedCancellation(request);
    }
}
