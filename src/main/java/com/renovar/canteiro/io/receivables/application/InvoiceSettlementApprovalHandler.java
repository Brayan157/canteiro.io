package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceSettlementApprovalHandler implements ChangeRequestApprovalHandler {
    private final InvoiceSettlementService service;
    @Override public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.RECEIVABLES && request.getOperation() == ChangeRequestOperation.CREATE
                && "InvoiceSettlement".equals(request.getEntityType());
    }
    @Override public void apply(ChangeRequest request) { service.apply(request); }
}
