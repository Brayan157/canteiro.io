package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementDiscountChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementDiscountService measurementDiscountService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.MEASUREMENTS
                && changeRequest.getOperation() == ChangeRequestOperation.CREATE
                && "MeasurementDiscount".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        measurementDiscountService.applyApprovedCreation(changeRequest);
    }
}
