package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PayableSettlementApprovalHandler implements ChangeRequestApprovalHandler {
    private final PayableSettlementService settlements;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.PAYABLES
                && PayableSettlementService.ENTITY_TYPE.equals(request.getEntityType())
                && (request.getOperation() == ChangeRequestOperation.CREATE || request.getOperation() == ChangeRequestOperation.CANCEL);
    }

    @Override
    public void apply(ChangeRequest request) {
        settlements.apply(request);
    }
}
