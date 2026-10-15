package com.renovar.canteiro.io.receivables.application;
import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
class ReceivableCreditApprovalHandler implements ChangeRequestApprovalHandler{
 private final ReceivableCreditService credits;
 public boolean supports(ChangeRequest r){return r.getModule()==AuditModule.RECEIVABLES&&r.getEntityType().equals("ReceivableCredit");}
 public void apply(ChangeRequest r){credits.applyApproved(r);}
}

