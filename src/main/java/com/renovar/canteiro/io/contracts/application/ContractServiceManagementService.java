package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmountCalculator;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplateRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractServiceManagementService {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final ContractDiscountRepository contractDiscountRepository;
    private final ServiceTemplateRepository serviceTemplateRepository;
    private final ContractMeasurementAdjustmentAmountProvider measurementAdjustmentAmountProvider;
    private final ApprovedContractBillingAmountProvider approvedContractBillingAmountProvider;
    private final CalculateContractNetAmountUseCase calculateContractNetAmountUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public ContractServiceChangeResult create(UUID contractId, ContractServiceCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireContract(contractId, companyId);
        ContractService proposed = newService(companyId, contractId, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.SERVICES, ChangeOperation.CREATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return requested(AuditModule.SERVICES, ChangeRequestOperation.CREATE, "ContractService", null, null,
                    proposal(contractId, command), command.justification(), mode);
        }
        ensureBillingLimit(companyId, contractId, proposed, null);
        ContractService saved = contractServiceRepository.save(proposed);
        ContractNetAmount totals = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        auditEventRecorder.recordDirectAction(AuditModule.SERVICES, AuditAction.CREATE, "ContractService", saved.getId(),
                null, auditData(saved), Map.of("origin", "direct", "contractId", contractId.toString()));
        return new ContractServiceChangeResult(saved, totals, null, mode);
    }

    @Transactional
    public ContractServiceChangeResult update(UUID contractId, UUID serviceId, ContractServiceCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ContractService current = requireService(contractId, serviceId, companyId);
        requireUnchangedSourceTemplate(current, command.sourceServiceTemplateId(), companyId);
        ContractService proposed = current.updateProfile(command.name(), command.description(), command.status(), command.quantity(), command.unitPrice())
                .withDiscount(command.discountType(), command.discountValue());
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.SERVICES, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return requested(AuditModule.SERVICES, ChangeRequestOperation.UPDATE, "ContractService", current.getId(), auditData(current),
                    proposal(contractId, command), command.justification(), mode);
        }
        ensureBillingLimit(companyId, contractId, proposed, current.getId());
        ContractService saved = contractServiceRepository.save(proposed);
        ContractNetAmount totals = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        auditEventRecorder.recordDirectAction(AuditModule.SERVICES, AuditAction.UPDATE, "ContractService", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "direct", "contractId", contractId.toString()));
        return new ContractServiceChangeResult(saved, totals, null, mode);
    }

    @Transactional
    public ContractServiceChangeResult setServiceDiscount(UUID contractId, UUID serviceId, DiscountType type, BigDecimal value,
                                                          String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ContractService current = requireService(contractId, serviceId, companyId);
        ContractService proposed = current.withDiscount(type, value);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.DISCOUNTS, ChangeOperation.UPDATE);
        Map<String, Object> proposal = discountProposal(type, value);
        proposal.put("contractId", contractId.toString());
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return requested(AuditModule.DISCOUNTS, ChangeRequestOperation.UPDATE, "ContractServiceDiscount", current.getId(),
                    auditData(current), proposal, justification, mode);
        }
        ensureBillingLimit(companyId, contractId, proposed, current.getId());
        ContractService saved = contractServiceRepository.save(proposed);
        ContractNetAmount totals = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        auditEventRecorder.recordDirectAction(AuditModule.DISCOUNTS, AuditAction.UPDATE, "ContractServiceDiscount", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "direct", "contractId", contractId.toString()));
        return new ContractServiceChangeResult(saved, totals, null, mode);
    }

    @Transactional
    public ContractServiceChangeResult setContractDiscount(UUID contractId, DiscountType type, BigDecimal value, String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireContract(contractId, companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.DISCOUNTS, ChangeOperation.UPDATE);
        ContractDiscount current = contractDiscountRepository.findByContractIdAndCompanyId(contractId, companyId).orElse(null);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return requested(AuditModule.DISCOUNTS, ChangeRequestOperation.UPDATE, "ContractDiscount", contractId,
                    current == null ? null : discountAuditData(current), discountProposal(type, value), justification, mode);
        }
        ContractDiscount saved = setContractDiscountDirect(companyId, contractId, type, value);
        ContractNetAmount totals = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        auditEventRecorder.recordDirectAction(AuditModule.DISCOUNTS, AuditAction.UPDATE, "ContractDiscount", contractId,
                current == null ? null : discountAuditData(current), discountAuditData(saved), Map.of("origin", "direct"));
        return new ContractServiceChangeResult(null, totals, null, mode);
    }

    @Transactional
    public void applyApprovedServiceCreation(ChangeRequest request) {
        ContractServiceCommand command = commandFromProposal(request.getSnapshot().proposedData().values());
        UUID contractId = UUID.fromString((String) request.getSnapshot().proposedData().values().get("contractId"));
        ContractService proposed = newService(request.getCompanyId(), contractId, command);
        requireContract(contractId, request.getCompanyId());
        ensureBillingLimit(request.getCompanyId(), contractId, proposed, null);
        ContractService saved = contractServiceRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.SERVICES, AuditAction.CREATE, "ContractService", saved.getId(), null,
                auditData(saved), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    @Transactional
    public void applyApprovedServiceUpdate(ChangeRequest request) {
        UUID contractId = UUID.fromString((String) request.getSnapshot().proposedData().values().get("contractId"));
        ContractService current = requireService(contractId, request.getEntityId(), request.getCompanyId());
        ContractServiceCommand command = commandFromProposal(request.getSnapshot().proposedData().values());
        requireUnchangedSourceTemplate(current, command.sourceServiceTemplateId(), request.getCompanyId());
        ContractService proposed = current.updateProfile(command.name(), command.description(), command.status(), command.quantity(), command.unitPrice())
                .withDiscount(command.discountType(), command.discountValue());
        ensureBillingLimit(request.getCompanyId(), contractId, proposed, current.getId());
        ContractService saved = contractServiceRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.SERVICES, AuditAction.UPDATE, "ContractService", saved.getId(), auditData(current),
                auditData(saved), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    @Transactional
    public void applyApprovedServiceDiscount(ChangeRequest request) {
        UUID contractId = UUID.fromString((String) request.getSnapshot().proposedData().values().get("contractId"));
        ContractService current = requireService(contractId, request.getEntityId(), request.getCompanyId());
        ContractService proposed = current.withDiscount(DiscountType.valueOf((String) request.getSnapshot().proposedData().values().get("discountType")),
                new BigDecimal((String) request.getSnapshot().proposedData().values().get("discountValue")));
        ensureBillingLimit(request.getCompanyId(), contractId, proposed, current.getId());
        ContractService saved = contractServiceRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.DISCOUNTS, AuditAction.UPDATE, "ContractServiceDiscount", saved.getId(), auditData(current),
                auditData(saved), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    @Transactional
    public void applyApprovedContractDiscount(ChangeRequest request) {
        UUID contractId = request.getEntityId();
        ContractDiscount current = contractDiscountRepository.findByContractIdAndCompanyId(contractId, request.getCompanyId()).orElse(null);
        ContractDiscount saved = setContractDiscountDirect(request.getCompanyId(), contractId,
                DiscountType.valueOf((String) request.getSnapshot().proposedData().values().get("discountType")),
                new BigDecimal((String) request.getSnapshot().proposedData().values().get("discountValue")));
        auditEventRecorder.recordDirectAction(AuditModule.DISCOUNTS, AuditAction.UPDATE, "ContractDiscount", contractId,
                current == null ? null : discountAuditData(current), discountAuditData(saved),
                Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    private ContractDiscount setContractDiscountDirect(UUID companyId, UUID contractId, DiscountType type, BigDecimal value) {
        contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        ContractDiscount discount = ContractDiscount.create(companyId, contractId, type, value);
        ContractNetAmount proposed = ContractNetAmountCalculator.calculate(contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId),
                discount, measurementAdjustmentAmountProvider.approvedAmount(companyId, contractId));
        if (proposed.netAmount().compareTo(approvedContractBillingAmountProvider.approvedNetAmount(companyId, contractId)) < 0) {
            throw new IllegalStateException("Contract net amount must not be lower than its approved billing amount");
        }
        return contractDiscountRepository.save(discount);
    }

    private ContractServiceChangeResult requested(AuditModule module, ChangeRequestOperation operation, String entityType, UUID entityId,
                                                   Map<String, Object> before, Map<String, Object> proposed, String justification,
                                                   ChangeAuthorizationMode mode) {
        ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(module, operation, entityType, entityId, 0,
                new ChangeRequestSnapshot(before == null ? null : new AuditPayload(before), new AuditPayload(proposed)), justification));
        return new ContractServiceChangeResult(null, null, request, mode);
    }

    private ContractService newService(UUID companyId, UUID contractId, ContractServiceCommand command) {
        requireSourceTemplate(command.sourceServiceTemplateId(), companyId);
        return ContractService.create(companyId, contractId, command.sourceServiceTemplateId(), command.name(), command.description(), command.status(), command.quantity(),
                command.unitPrice(), command.discountType(), command.discountValue());
    }

    private ContractService requireService(UUID contractId, UUID serviceId, UUID companyId) {
        requireContract(contractId, companyId);
        ContractService service = contractServiceRepository.findByIdAndCompanyId(serviceId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract service"));
        if (!service.getContractId().equals(contractId)) throw new TenantResourceNotFoundException("Contract service");
        return service;
    }

    private void requireContract(UUID contractId, UUID companyId) {
        if (contractRepository.findByIdAndCompanyId(contractId, companyId).isEmpty()) throw new TenantResourceNotFoundException("Contract");
    }

    private void requireSourceTemplate(UUID sourceServiceTemplateId, UUID companyId) {
        if (sourceServiceTemplateId != null && serviceTemplateRepository.findByIdAndCompanyId(sourceServiceTemplateId, companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Service template");
        }
    }

    private void requireUnchangedSourceTemplate(ContractService current, UUID sourceServiceTemplateId, UUID companyId) {
        if (sourceServiceTemplateId != null && !sourceServiceTemplateId.equals(current.getSourceServiceTemplateId())) {
            requireSourceTemplate(sourceServiceTemplateId, companyId);
            throw new IllegalArgumentException("Contract service source template cannot be changed");
        }
    }

    private void ensureBillingLimit(UUID companyId, UUID contractId, ContractService proposed, UUID replacingId) {
        List<ContractService> services = new ArrayList<>(contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId));
        if (replacingId == null) services.add(proposed); else services.replaceAll(service -> service.getId().equals(replacingId) ? proposed : service);
        ContractNetAmount amount = ContractNetAmountCalculator.calculate(services,
                contractDiscountRepository.findByContractIdAndCompanyId(contractId, companyId).orElse(null),
                measurementAdjustmentAmountProvider.approvedAmount(companyId, contractId));
        if (amount.netAmount().compareTo(approvedContractBillingAmountProvider.approvedNetAmount(companyId, contractId)) < 0) {
            throw new IllegalStateException("Contract net amount must not be lower than its approved billing amount");
        }
    }

    private Map<String, Object> proposal(UUID contractId, ContractServiceCommand command) {
        Map<String, Object> values = new HashMap<>();
        values.put("contractId", contractId.toString());
        values.put("sourceServiceTemplateId", command.sourceServiceTemplateId() == null ? null : command.sourceServiceTemplateId().toString());
        values.put("name", command.name()); values.put("description", command.description()); values.put("status", command.status().name());
        values.put("quantity", command.quantity().toPlainString()); values.put("unitPrice", command.unitPrice().toPlainString());
        values.put("discountType", command.discountType() == null ? null : command.discountType().name());
        values.put("discountValue", command.discountValue() == null ? null : command.discountValue().toPlainString());
        return values;
    }

    private Map<String, Object> discountProposal(DiscountType type, BigDecimal value) {
        return new HashMap<>(Map.of("discountType", type.name(), "discountValue", value.toPlainString()));
    }

    private ContractServiceCommand commandFromProposal(Map<String, Object> values) {
        String type = (String) values.get("discountType");
        String value = (String) values.get("discountValue");
        String sourceTemplateId = (String) values.get("sourceServiceTemplateId");
        return new ContractServiceCommand(sourceTemplateId == null ? null : UUID.fromString(sourceTemplateId), (String) values.get("name"), (String) values.get("description"),
                com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.valueOf((String) values.get("status")),
                new BigDecimal((String) values.get("quantity")), new BigDecimal((String) values.get("unitPrice")),
                type == null ? null : DiscountType.valueOf(type), value == null ? null : new BigDecimal(value), null);
    }

    private Map<String, Object> auditData(ContractService service) {
        return Map.of("contractId", service.getContractId().toString(), "name", service.getName(), "status", service.getStatus().name(),
                "quantity", service.getQuantity().toPlainString(), "unitPrice", service.getUnitPrice().toPlainString(),
                "discountType", service.getDiscountType() == null ? "NONE" : service.getDiscountType().name(),
                "netAmount", service.getNetAmount().toPlainString());
    }

    private Map<String, Object> discountAuditData(ContractDiscount discount) {
        return Map.of("discountType", discount.getDiscountType().name(), "discountValue", discount.getDiscountValue().toPlainString());
    }
}
