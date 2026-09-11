package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvoiceChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final InvoiceRegistrationService invoiceRegistrationService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.INVOICES
                && changeRequest.getOperation() == ChangeRequestOperation.CREATE
                && "Invoice".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        invoiceRegistrationService.applyApprovedRegistration(changeRequest);
    }
}
