package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CalculatedTaxApprovalHandler implements ChangeRequestApprovalHandler {
    private final CalculatedTaxObligationService service;
    @Override public boolean supports(ChangeRequest r) { return r.getModule() == AuditModule.INVOICES && r.getOperation() == ChangeRequestOperation.CREATE && "CalculatedTaxObligation".equals(r.getEntityType()); }
    @Override public void apply(ChangeRequest r) { service.apply(r); }
}
