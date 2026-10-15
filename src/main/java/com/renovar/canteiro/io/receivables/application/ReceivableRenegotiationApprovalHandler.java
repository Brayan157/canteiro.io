package com.renovar.canteiro.io.receivables.application;
import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
class ReceivableRenegotiationApprovalHandler implements ChangeRequestApprovalHandler {
    private final ReceivableRenegotiationService service;
    public boolean supports(ChangeRequest r){return r.getModule()==AuditModule.RECEIVABLES&&r.getEntityType().equals("ReceivableRenegotiation");}
    public void apply(ChangeRequest r){service.applyApproved(r);}
}
