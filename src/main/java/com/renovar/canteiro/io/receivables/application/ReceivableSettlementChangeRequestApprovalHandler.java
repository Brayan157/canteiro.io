package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReceivableSettlementChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final ReceivableSettlementService receivableSettlementService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.RECEIVABLES
                && changeRequest.getOperation() == ChangeRequestOperation.CREATE
                && "ReceivableSettlement".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        receivableSettlementService.applyApprovedSettlement(changeRequest);
    }
}
