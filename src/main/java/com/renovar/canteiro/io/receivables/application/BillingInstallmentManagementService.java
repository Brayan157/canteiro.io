package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingInstallmentManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final BillingInstallmentRepository billingInstallmentRepository;
    private final BillingAllocationRepository billingAllocationRepository;
    private final ReceivableRepository receivableRepository;
    private final ValidateContractBillingLimitUseCase validateContractBillingLimitUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public BillingInstallmentApprovalResult createApproved(CreateApprovedBillingInstallmentCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validate(command, companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.BILLING, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.BILLING,
                    ChangeRequestOperation.CREATE,
                    "BillingInstallment",
                    null,
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new BillingInstallmentApprovalResult(null, List.of(), null, changeRequest, mode);
        }
        Creation creation = persistApproved(companyId, command);
        recordCreation(creation, "direct", null);
        return new BillingInstallmentApprovalResult(
                creation.billingInstallment(), creation.allocations(), creation.receivable(), null, mode
        );
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateApprovedBillingInstallmentCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        validate(command, changeRequest.getCompanyId());
        Creation creation = persistApproved(changeRequest.getCompanyId(), command);
        recordCreation(creation, "approved-change-request", changeRequest.getId());
    }

    private Creation persistApproved(UUID companyId, CreateApprovedBillingInstallmentCommand command) {
        BillingInstallment installment = billingInstallmentRepository.save(BillingInstallment.createApproved(
                companyId, command.contractId(), command.reference(), command.description(), command.issuedOn(), command.dueOn()
        ));
        List<BillingAllocation> allocations = command.allocations().stream()
                .map(commandAllocation -> createActiveAllocation(companyId, installment.getId(), command.contractId(), commandAllocation))
                .toList();
        validateContractBillingLimitUseCase.requireAvailableForActivation(companyId, command.contractId(), allocations);
        List<BillingAllocation> savedAllocations = allocations.stream().map(billingAllocationRepository::save).toList();
        BigDecimal originalAmount = sum(savedAllocations);
        if (originalAmount.signum() <= 0) {
            throw new IllegalArgumentException("An approved billing installment must have a positive net amount");
        }
        Receivable receivable = receivableRepository.save(Receivable.create(
                companyId,
                command.contractId(),
                installment.getId(),
                command.financialGroupId() == null ? UUID.randomUUID() : command.financialGroupId(),
                command.reference(),
                command.description(),
                command.issuedOn(),
                command.dueOn(),
                originalAmount
        ));
        return new Creation(installment, savedAllocations, receivable);
    }

    private BillingAllocation createActiveAllocation(UUID companyId, UUID billingInstallmentId, UUID contractId,
                                                      BillingAllocationCommand command) {
        return command.type() == BillingAllocationType.SERVICE
                ? BillingAllocation.createService(companyId, billingInstallmentId, contractId, command.contractServiceId(),
                        command.allocatedAmount()).activate()
                : BillingAllocation.createContractDiscount(companyId, billingInstallmentId, contractId,
                        command.allocatedAmount()).activate();
    }

    private void validate(CreateApprovedBillingInstallmentCommand command, UUID companyId) {
        if (command.contractId() == null || command.dueOn() == null || command.allocations() == null || command.allocations().isEmpty()) {
            throw new IllegalArgumentException("Contract, due date and at least one billing allocation are required");
        }
        if (contractRepository.findByIdAndCompanyId(command.contractId(), companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Contract");
        }
        for (BillingAllocationCommand allocation : command.allocations()) {
            if (allocation == null || allocation.type() == null || allocation.allocatedAmount() == null) {
                throw new IllegalArgumentException("Billing allocation type and amount are required");
            }
            if (allocation.type() == BillingAllocationType.SERVICE) {
                if (allocation.contractServiceId() == null || contractServiceRepository
                        .findByIdAndCompanyId(allocation.contractServiceId(), companyId)
                        .filter(service -> service.getContractId().equals(command.contractId()))
                        .isEmpty()) {
                    throw new TenantResourceNotFoundException("Contract service");
                }
                BillingAllocation.createService(companyId, UUID.randomUUID(), command.contractId(),
                        allocation.contractServiceId(), allocation.allocatedAmount());
            } else {
                if (allocation.contractServiceId() != null) {
                    throw new IllegalArgumentException("Contract discount allocation must not reference a contract service");
                }
                BillingAllocation.createContractDiscount(companyId, UUID.randomUUID(), command.contractId(),
                        allocation.allocatedAmount());
            }
        }
        BillingInstallment.createApproved(companyId, command.contractId(), command.reference(), command.description(),
                command.issuedOn(), command.dueOn());
    }

    private void recordCreation(Creation creation, String origin, UUID changeRequestId) {
        Map<String, Object> installmentData = new LinkedHashMap<>();
        installmentData.put("contractId", creation.billingInstallment().getContractId().toString());
        installmentData.put("status", creation.billingInstallment().getStatus().name());
        installmentData.put("allocationCount", creation.allocations().size());
        installmentData.put("netAmount", creation.receivable().getOriginalAmount());
        auditEventRecorder.recordDirectAction(
                AuditModule.BILLING, AuditAction.CREATE, "BillingInstallment", creation.billingInstallment().getId(),
                null, installmentData, auditMetadata(origin, changeRequestId)
        );
        auditEventRecorder.recordDirectAction(
                AuditModule.RECEIVABLES, AuditAction.CREATE, "Receivable", creation.receivable().getId(), null,
                Map.of(
                        "contractId", creation.receivable().getContractId().toString(),
                        "billingInstallmentId", creation.billingInstallment().getId().toString(),
                        "financialGroupId", creation.receivable().getFinancialGroupId().toString(),
                        "originalAmount", creation.receivable().getOriginalAmount(),
                        "status", creation.receivable().getStatus().name()
                ), auditMetadata(origin, changeRequestId)
        );
    }

    private Map<String, Object> proposal(CreateApprovedBillingInstallmentCommand command) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", command.contractId().toString());
        result.put("reference", command.reference());
        result.put("description", command.description());
        result.put("issuedOn", dateValue(command.issuedOn()));
        result.put("dueOn", command.dueOn().toString());
        result.put("financialGroupId", command.financialGroupId() == null ? null : command.financialGroupId().toString());
        List<Map<String, Object>> allocations = new ArrayList<>();
        for (BillingAllocationCommand allocation : command.allocations()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("contractServiceId", allocation.contractServiceId() == null ? null : allocation.contractServiceId().toString());
            value.put("type", allocation.type().name());
            value.put("allocatedAmount", allocation.allocatedAmount().toPlainString());
            allocations.add(value);
        }
        result.put("allocations", allocations);
        return result;
    }

    @SuppressWarnings("unchecked")
    private CreateApprovedBillingInstallmentCommand fromProposal(Map<String, Object> proposal) {
        List<BillingAllocationCommand> allocations = ((List<Map<String, Object>>) proposal.get("allocations")).stream()
                .map(value -> new BillingAllocationCommand(
                        uuid((String) value.get("contractServiceId")),
                        BillingAllocationType.valueOf((String) value.get("type")),
                        new BigDecimal((String) value.get("allocatedAmount"))
                ))
                .toList();
        return new CreateApprovedBillingInstallmentCommand(
                UUID.fromString((String) proposal.get("contractId")),
                (String) proposal.get("reference"),
                (String) proposal.get("description"),
                date((String) proposal.get("issuedOn")),
                LocalDate.parse((String) proposal.get("dueOn")),
                uuid((String) proposal.get("financialGroupId")),
                allocations,
                null
        );
    }

    private BigDecimal sum(List<BillingAllocation> allocations) {
        return allocations.stream().map(BillingAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> auditMetadata(String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin);
        if (changeRequestId != null) {
            metadata.put("changeRequestId", changeRequestId.toString());
        }
        return metadata;
    }

    private String dateValue(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private record Creation(BillingInstallment billingInstallment, List<BillingAllocation> allocations,
                            Receivable receivable) {
    }
}
