package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.application.AppliedChangeRequestEntity;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PayableCreationApprovalHandler implements ChangeRequestApprovalHandler {
    private final PayableService service;
    @Override public boolean supports(ChangeRequest r){return r.getModule()==AuditModule.PAYABLES&&r.getOperation()==ChangeRequestOperation.CREATE&&"PayableCreation".equals(r.getEntityType());}
    @Override public void apply(ChangeRequest r){service.apply(r);}
    @Override public AppliedChangeRequestEntity applyAndDescribe(ChangeRequest r){
        var result=service.apply(r);return new AppliedChangeRequestEntity("PayableFinancialGroup",result.financialGroupId(),"Grupo de contas a pagar");
    }
}
