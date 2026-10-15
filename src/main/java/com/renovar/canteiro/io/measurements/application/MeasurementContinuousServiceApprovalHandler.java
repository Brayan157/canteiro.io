package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
class MeasurementContinuousServiceApprovalHandler implements ChangeRequestApprovalHandler {
 private final MeasurementContinuousServiceManagementService services;
 public boolean supports(ChangeRequest r){return r.getModule()==AuditModule.MEASUREMENTS&&"MeasurementContinuousService".equals(r.getEntityType());}
 public void apply(ChangeRequest r){services.applyApproved(r);}
}

