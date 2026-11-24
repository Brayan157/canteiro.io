package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.application.AppliedChangeRequestEntity;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PayablePaymentInstructionApprovalHandler implements ChangeRequestApprovalHandler {
    private final PayablePaymentInstructionService service;
    @Override public boolean supports(ChangeRequest request){return request.getModule()==AuditModule.PAYABLES&&"PayablePaymentInstruction".equals(request.getEntityType());}
    @Override public void apply(ChangeRequest request){service.applyApproved(request);}
    @Override public AppliedChangeRequestEntity applyAndDescribe(ChangeRequest request){
        apply(request);return new AppliedChangeRequestEntity("PayablePaymentInstruction",request.getEntityId(),"Instruções para pagamento");
    }
}
