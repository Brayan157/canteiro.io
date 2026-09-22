package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
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
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAllocationBalance;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceRegistrationService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ContractRepository contractRepository;
    private final BillingAllocationRepository billingAllocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CalculateInvoiceAllocationBalanceUseCase calculateInvoiceAllocationBalanceUseCase;
    private final CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public InvoiceRegistrationResult register(CreateRegisteredInvoiceCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateHeaderAndContract(command, companyId, false);
        List<ResolvedInvoiceItem> items = resolveAndValidateItems(command, companyId, false);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.INVOICES, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.INVOICES,
                    ChangeRequestOperation.CREATE,
                    "Invoice",
                    null,
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new InvoiceRegistrationResult(null, List.of(), changeRequest, mode);
        }
        Registration registration = persistRegistered(companyId, command, items);
        recordRegistration(registration, "direct", null);
        return new InvoiceRegistrationResult(registration.invoice(), registration.items(), null, mode);
    }

    @Transactional
    public void applyApprovedRegistration(ChangeRequest changeRequest) {
        CreateRegisteredInvoiceCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        validateHeaderAndContract(command, changeRequest.getCompanyId(), false);
        Registration registration = persistRegistered(changeRequest.getCompanyId(), command, null);
        recordRegistration(registration, "approved-change-request", changeRequest.getId());
    }

    private Registration persistRegistered(UUID companyId, CreateRegisteredInvoiceCommand command,
                                           List<ResolvedInvoiceItem> validatedItems) {
        contractRepository.findByIdAndCompanyIdWithBillingLock(command.contractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        List<ResolvedInvoiceItem> items = validatedItems == null
                ? resolveAndValidateItems(command, companyId, true)
                : validateCapacity(validatedItems, companyId);
        BigDecimal totalAmount = sum(items.stream().map(ResolvedInvoiceItem::allocatedAmount).toList());
        requireContractInvoiceCapacity(companyId, command.contractId(), totalAmount);
        Invoice invoice = invoiceRepository.save(Invoice.register(
                companyId, command.contractId(), command.number(), command.series(), command.accessKey(),
                command.issuedOn(), totalAmount
        ));
        List<InvoiceItem> savedItems = items.stream().map(item -> invoiceItemRepository.save(InvoiceItem.create(
                companyId, invoice.getId(), command.contractId(), item.allocation().getId(),
                item.allocation().getContractServiceId(), item.description(), item.allocatedAmount()
        ))).toList();
        return new Registration(invoice, savedItems);
    }

    private void validateHeaderAndContract(CreateRegisteredInvoiceCommand command, UUID companyId, boolean locked) {
        if (command == null || command.contractId() == null || command.issuedOn() == null || command.items() == null
                || command.items().isEmpty()) {
            throw new IllegalArgumentException("Invoice contract, issue date and at least one item are required");
        }
        if (locked) {
            contractRepository.findByIdAndCompanyIdWithBillingLock(command.contractId(), companyId)
                    .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        } else if (contractRepository.findByIdAndCompanyId(command.contractId(), companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Contract");
        }
        Invoice.register(companyId, command.contractId(), command.number(), command.series(), command.accessKey(),
                command.issuedOn(), BigDecimal.ONE);
    }

    private List<ResolvedInvoiceItem> resolveAndValidateItems(CreateRegisteredInvoiceCommand command, UUID companyId,
                                                               boolean validateCapacity) {
        Map<UUID, BillingAllocation> allocations = billingAllocationRepository
                .findByContractIdAndCompanyId(command.contractId(), companyId).stream()
                .collect(Collectors.toMap(BillingAllocation::getId, Function.identity()));
        Set<UUID> seenAllocationIds = new HashSet<>();
        List<ResolvedInvoiceItem> resolvedItems = new ArrayList<>();
        for (InvoiceItemCommand item : command.items()) {
            if (item == null || item.billingAllocationId() == null || item.allocatedAmount() == null) {
                throw new IllegalArgumentException("Invoice item billing allocation and amount are required");
            }
            if (!seenAllocationIds.add(item.billingAllocationId())) {
                throw new IllegalArgumentException("An invoice can contain only one item for each billing allocation");
            }
            BillingAllocation allocation = allocations.get(item.billingAllocationId());
            if (allocation == null) {
                throw new TenantResourceNotFoundException("Billing allocation");
            }
            if (allocation.getStatus() != BillingAllocationStatus.ACTIVE) {
                throw new IllegalStateException("Only active billing allocations can be included in an invoice");
            }
            BigDecimal amount = nonZero(item.allocatedAmount());
            if (amount.signum() != allocation.getAllocatedAmount().signum()) {
                throw new IllegalArgumentException("Invoice item amount must preserve the billing allocation sign");
            }
            resolvedItems.add(new ResolvedInvoiceItem(allocation, item.description(), amount));
        }
        if (sum(resolvedItems.stream().map(ResolvedInvoiceItem::allocatedAmount).toList()).signum() <= 0) {
            throw new IllegalArgumentException("Registered invoice total amount must be positive");
        }
        return validateCapacity ? validateCapacity(resolvedItems, companyId) : resolvedItems;
    }

    private List<ResolvedInvoiceItem> validateCapacity(List<ResolvedInvoiceItem> items, UUID companyId) {
        for (ResolvedInvoiceItem item : items) {
            InvoiceAllocationBalance balance = calculateInvoiceAllocationBalanceUseCase.calculate(
                    companyId, item.allocation().getId()
            );
            if (!balance.canRegister(item.allocatedAmount())) {
                throw new IllegalStateException("Invoice item exceeds the available amount of its billing allocation");
            }
        }
        return items;
    }

    private void requireContractInvoiceCapacity(UUID companyId, UUID contractId, BigDecimal proposedInvoiceAmount) {
        BigDecimal registeredAmount = invoiceItemRepository
                .registeredAmountByContractIdAndCompanyId(contractId, companyId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocatedAmount = calculateContractBillingBalanceUseCase
                .calculate(companyId, contractId).activeAllocatedAmount();
        if (registeredAmount.add(proposedInvoiceAmount).compareTo(allocatedAmount) > 0) {
            throw new BillingLimitExceededException("Registered invoices exceed the contract active billing allocations");
        }
    }

    private void recordRegistration(Registration registration, String origin, UUID changeRequestId) {
        Map<String, Object> invoiceData = new LinkedHashMap<>();
        invoiceData.put("contractId", registration.invoice().getContractId().toString());
        invoiceData.put("number", registration.invoice().getNumber());
        invoiceData.put("series", registration.invoice().getSeries());
        invoiceData.put("totalAmount", registration.invoice().getTotalAmount());
        invoiceData.put("itemCount", registration.items().size());
        auditEventRecorder.recordDirectAction(
                AuditModule.INVOICES, AuditAction.CREATE, "Invoice", registration.invoice().getId(), null, invoiceData,
                auditMetadata(origin, changeRequestId)
        );
        for (InvoiceItem item : registration.items()) {
            auditEventRecorder.recordDirectAction(
                    AuditModule.INVOICES, AuditAction.CREATE, "InvoiceItem", item.getId(), null,
                    Map.of(
                            "invoiceId", registration.invoice().getId().toString(),
                            "billingAllocationId", item.getBillingAllocationId().toString(),
                            "contractId", item.getContractId().toString(),
                            "allocatedAmount", item.getAllocatedAmount()
                    ), auditMetadata(origin, changeRequestId)
            );
        }
    }

    private Map<String, Object> proposal(CreateRegisteredInvoiceCommand command) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("contractId", command.contractId().toString());
        proposal.put("number", command.number());
        proposal.put("series", command.series());
        proposal.put("accessKey", command.accessKey());
        proposal.put("issuedOn", command.issuedOn().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (InvoiceItemCommand item : command.items()) {
            Map<String, Object> itemProposal = new LinkedHashMap<>();
            itemProposal.put("billingAllocationId", item.billingAllocationId().toString());
            itemProposal.put("description", item.description());
            itemProposal.put("allocatedAmount", item.allocatedAmount().toPlainString());
            items.add(itemProposal);
        }
        proposal.put("items", items);
        return proposal;
    }

    @SuppressWarnings("unchecked")
    private CreateRegisteredInvoiceCommand fromProposal(Map<String, Object> proposal) {
        List<InvoiceItemCommand> items = ((List<Map<String, Object>>) proposal.get("items")).stream()
                .map(item -> new InvoiceItemCommand(
                        UUID.fromString((String) item.get("billingAllocationId")),
                        (String) item.get("description"),
                        new BigDecimal((String) item.get("allocatedAmount"))
                )).toList();
        return new CreateRegisteredInvoiceCommand(
                UUID.fromString((String) proposal.get("contractId")),
                (String) proposal.get("number"),
                (String) proposal.get("series"),
                (String) proposal.get("accessKey"),
                LocalDate.parse((String) proposal.get("issuedOn")),
                items,
                null
        );
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonZero(BigDecimal value) {
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException("Invoice item amount must not be zero");
        }
        return normalized;
    }

    private Map<String, Object> auditMetadata(String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin);
        if (changeRequestId != null) {
            metadata.put("changeRequestId", changeRequestId.toString());
        }
        return metadata;
    }

    private record ResolvedInvoiceItem(BillingAllocation allocation, String description, BigDecimal allocatedAmount) {
    }

    private record Registration(Invoice invoice, List<InvoiceItem> items) {
    }
}
