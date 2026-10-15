package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ContractServiceChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final ContractServiceManagementService service;

    @Override
    public boolean supports(ChangeRequest request) {
        if (request.getOperation() != ChangeRequestOperation.CREATE && request.getOperation() != ChangeRequestOperation.UPDATE
                && request.getOperation() != ChangeRequestOperation.CANCEL) return false;
        return (request.getModule() == AuditModule.SERVICES && "ContractService".equals(request.getEntityType()))
                || (request.getModule() == AuditModule.DISCOUNTS && "ContractServiceDiscount".equals(request.getEntityType()))
                || (request.getModule() == AuditModule.DISCOUNTS && "ContractDiscount".equals(request.getEntityType()));
    }

    @Override
    public void apply(ChangeRequest request) {
        if (request.getModule() == AuditModule.SERVICES && request.getOperation() == ChangeRequestOperation.CREATE) {
            service.applyApprovedServiceCreation(request);
        } else if (request.getModule() == AuditModule.SERVICES) {
            service.applyApprovedServiceUpdate(request);
        } else if (request.getOperation() == ChangeRequestOperation.CANCEL && "ContractServiceDiscount".equals(request.getEntityType())) {
            service.applyApprovedServiceDiscountReversal(request);
        } else if (request.getOperation() == ChangeRequestOperation.CANCEL) {
            service.applyApprovedContractDiscountReversal(request);
        } else if ("ContractServiceDiscount".equals(request.getEntityType())) {
            service.applyApprovedServiceDiscount(request);
        } else {
            service.applyApprovedContractDiscount(request);
        }
    }
}
