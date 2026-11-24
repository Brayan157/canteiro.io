package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.application.AppliedChangeRequestEntity;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PayableCatalogApprovalHandler implements ChangeRequestApprovalHandler {
    private final PayableCatalogService catalog;
    @Override public boolean supports(ChangeRequest r){return r.getModule()==AuditModule.PAYABLES&&r.getEntityType().equals("Supplier")
            ||r.getModule()==AuditModule.COSTS_EXPENSES&&r.getEntityType().equals("FinancialCategory");}
    @Override public void apply(ChangeRequest request){catalog.apply(request);}
    @Override public AppliedChangeRequestEntity applyAndDescribe(ChangeRequest request){
        var result=catalog.apply(request);
        return result.supplier()!=null?new AppliedChangeRequestEntity("Supplier",result.supplier().id(),result.supplier().name()):
                new AppliedChangeRequestEntity("FinancialCategory",result.category().id(),result.category().name());
    }
}
