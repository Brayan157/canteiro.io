package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementContractCompositionChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final MeasurementContractCompositionService service;
    @Override public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.SERVICES && "MeasurementContractComposition".equals(request.getEntityType());
    }
    @Override public void apply(ChangeRequest request) { service.applyApproved(request); }
}
