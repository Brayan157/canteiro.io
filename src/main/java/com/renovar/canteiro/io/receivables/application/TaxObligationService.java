package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
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
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxObligationService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final InvoiceRepository invoiceRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final TaxObligationRepository taxObligationRepository;
    private final PayableRepository payableRepository;
    private final CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public TaxObligationCreationResult create(CreateTaxObligationCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validate(command, companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.INVOICES, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.INVOICES, ChangeRequestOperation.CREATE, "TaxObligation", null, 0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()
            ));
            return new TaxObligationCreationResult(null, null, changeRequest, mode);
        }
        Creation creation = persist(companyId, command);
        record(creation, "direct", null);
        return new TaxObligationCreationResult(creation.taxObligation(), creation.payable(), null, mode);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateTaxObligationCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        validate(command, changeRequest.getCompanyId());
        Creation creation = persist(changeRequest.getCompanyId(), command);
        record(creation, "approved-change-request", changeRequest.getId());
    }

    private Creation persist(UUID companyId, CreateTaxObligationCommand command) {
        Invoice invoice = invoice(companyId, command.invoiceId(), true);
        Contract contract = contract(companyId, invoice.getContractId());
        if (command.treatment() == TaxTreatment.WITHHELD_BY_CUSTOMER) {
            receivableRepository.findByIdAndCompanyIdWithSettlementLock(command.receivableId(), companyId)
                    .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        }
        validateWithheldCapacity(command, companyId);
        TaxObligation taxObligation = taxObligationRepository.save(TaxObligation.register(
                companyId, invoice.getId(), contract.getId(), command.receivableId(), command.name(), command.description(),
                command.dueOn(), command.amount(), command.treatment()
        ));
        Payable payable = command.treatment() == TaxTreatment.PAYABLE_BY_COMPANY
                ? payableRepository.save(Payable.createTaxPayable(
                        companyId, contract.getWorkId(), contract.getId(), taxObligation.getId(),
                        "TAX-" + invoice.getNumber(), taxObligation.getName(), taxObligation.getDueOn(), taxObligation.getAmount()
                ))
                : null;
        return new Creation(taxObligation, payable);
    }

    private void validate(CreateTaxObligationCommand command, UUID companyId) {
        if (command == null || command.invoiceId() == null || command.name() == null || command.amount() == null
                || command.treatment() == null) {
            throw new IllegalArgumentException("Invoice, tax name, amount and treatment are required");
        }
        Invoice invoice = invoice(companyId, command.invoiceId(), false);
        contract(companyId, invoice.getContractId());
        TaxObligation.register(companyId, invoice.getId(), invoice.getContractId(), command.receivableId(), command.name(),
                command.description(), command.dueOn(), command.amount(), command.treatment());
        if (command.treatment() == TaxTreatment.WITHHELD_BY_CUSTOMER) {
            Receivable receivable = receivableRepository.findByIdAndCompanyId(command.receivableId(), companyId)
                    .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
            if (!receivable.getContractId().equals(invoice.getContractId())) {
                throw new IllegalArgumentException("Withheld tax receivable must belong to the invoice contract");
            }
            validateWithheldCapacity(command, companyId);
        }
    }

    private void validateWithheldCapacity(CreateTaxObligationCommand command, UUID companyId) {
        if (command.treatment() != TaxTreatment.WITHHELD_BY_CUSTOMER) {
            return;
        }
        if (!calculateReceivableBalanceUseCase.calculate(companyId, command.receivableId()).canSettle(command.amount())) {
            throw new IllegalStateException("Withheld tax exceeds the receivable outstanding amount");
        }
    }

    private Invoice invoice(UUID companyId, UUID invoiceId, boolean locked) {
        Invoice invoice = (locked
                ? invoiceRepository.findByIdAndCompanyIdWithLock(invoiceId, companyId)
                : invoiceRepository.findByIdAndCompanyId(invoiceId, companyId))
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        if (invoice.getStatus() != InvoiceStatus.REGISTERED) {
            throw new IllegalStateException("Taxes can only be recorded for a registered invoice");
        }
        return invoice;
    }

    private Contract contract(UUID companyId, UUID contractId) {
        return contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
    }

    private void record(Creation creation, String origin, UUID changeRequestId) {
        auditEventRecorder.recordDirectAction(
                AuditModule.INVOICES, AuditAction.CREATE, "TaxObligation", creation.taxObligation().getId(), null,
                Map.of(
                        "invoiceId", creation.taxObligation().getInvoiceId().toString(),
                        "contractId", creation.taxObligation().getContractId().toString(),
                        "amount", creation.taxObligation().getAmount(),
                        "treatment", creation.taxObligation().getTreatment().name()
                ), metadata(origin, changeRequestId)
        );
        if (creation.payable() != null) {
            auditEventRecorder.recordDirectAction(
                    AuditModule.PAYABLES, AuditAction.CREATE, "Payable", creation.payable().getId(), null,
                    Map.of(
                            "taxObligationId", creation.taxObligation().getId().toString(),
                            "workId", creation.payable().getWorkId().toString(),
                            "contractId", creation.payable().getContractId().toString(),
                            "amount", creation.payable().getOriginalAmount()
                    ), metadata(origin, changeRequestId)
            );
        }
    }

    private Map<String, Object> proposal(CreateTaxObligationCommand command) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("invoiceId", command.invoiceId().toString());
        proposal.put("receivableId", command.receivableId() == null ? null : command.receivableId().toString());
        proposal.put("name", command.name());
        proposal.put("description", command.description());
        proposal.put("dueOn", command.dueOn() == null ? null : command.dueOn().toString());
        proposal.put("amount", command.amount().toPlainString());
        proposal.put("treatment", command.treatment().name());
        return proposal;
    }

    private CreateTaxObligationCommand fromProposal(Map<String, Object> proposal) {
        return new CreateTaxObligationCommand(
                UUID.fromString((String) proposal.get("invoiceId")), uuid((String) proposal.get("receivableId")),
                (String) proposal.get("name"), (String) proposal.get("description"),
                date((String) proposal.get("dueOn")), new BigDecimal((String) proposal.get("amount")),
                TaxTreatment.valueOf((String) proposal.get("treatment")), null
        );
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin);
        if (changeRequestId != null) {
            metadata.put("changeRequestId", changeRequestId.toString());
        }
        return metadata;
    }

    private UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }

    private LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }

    private record Creation(TaxObligation taxObligation, Payable payable) {
    }
}
