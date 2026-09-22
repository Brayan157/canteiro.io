package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FinalCustomerChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final FinalCustomerManagementService finalCustomerManagementService;
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.CUSTOMERS
                && switch (request.getEntityType()) {
                    case "FinalCustomer" -> request.getOperation() == ChangeRequestOperation.CREATE
                            || request.getOperation() == ChangeRequestOperation.UPDATE;
                    case "FinalCustomerContact", "FinalCustomerAddress" -> true;
                    default -> false;
                };
    }

    public void apply(ChangeRequest request) {
        switch (request.getEntityType()) {
            case "FinalCustomer" -> {
                if (request.getOperation() == ChangeRequestOperation.CREATE) {
                    finalCustomerManagementService.applyApprovedCreation(request);
                } else {
                    finalCustomerManagementService.applyApprovedUpdate(request);
                }
            }
            case "FinalCustomerContact" -> applyContact(request);
            case "FinalCustomerAddress" -> applyAddress(request);
            default -> throw new IllegalArgumentException("Unsupported final customer change request");
        }
    }

    private void applyContact(ChangeRequest request) {
        switch (request.getOperation()) {
            case CREATE -> finalCustomerManagementService.applyApprovedContactCreation(request);
            case UPDATE -> finalCustomerManagementService.applyApprovedContactUpdate(request);
            case CANCEL -> finalCustomerManagementService.applyApprovedContactCancellation(request);
        }
    }

    private void applyAddress(ChangeRequest request) {
        switch (request.getOperation()) {
            case CREATE -> finalCustomerManagementService.applyApprovedAddressCreation(request);
            case UPDATE -> finalCustomerManagementService.applyApprovedAddressUpdate(request);
            case CANCEL -> finalCustomerManagementService.applyApprovedAddressCancellation(request);
        }
    }
}
