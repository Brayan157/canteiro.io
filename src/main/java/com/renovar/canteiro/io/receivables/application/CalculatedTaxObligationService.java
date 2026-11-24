package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceTaxOperationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxCalculation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculatedTaxObligationService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final TaxObligationService taxes;
    private final InvoiceRepository invoices;
    private final TaxObligationRepository taxRecords;
    private final InvoiceTaxOperationRepository operations;
    private final PayableRepository payables;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final FinancialCurrencyService currencies;
    private final tools.jackson.databind.ObjectMapper json;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Preview preview(UUID invoiceId, TaxCalculation calculation) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requireChangeAuthorization(AccessModule.INVOICES, ChangeOperation.CREATE);
        var invoice = invoices.findByIdAndCompanyId(invoiceId, company).orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        if (invoice.getStatus() != InvoiceStatus.REGISTERED) throw new IllegalStateException("A registered invoice is required");
        return new Preview(invoiceId, null, calculation, currencies.forContract(invoice.getContractId()), false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Preview recorded(UUID invoiceId, UUID taxId) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        var invoice = invoices.findByIdAndCompanyId(invoiceId, company).orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var tax = taxRecords.findByIdAndCompanyId(taxId, company).filter(t -> invoiceId.equals(t.getInvoiceId()) && invoice.getContractId().equals(t.getContractId()))
                .orElseThrow(() -> new TenantResourceNotFoundException("Tax obligation"));
        var operation = operations.findByTax(company, taxId);
        if (operation.isPresent() && !operation.get().invoiceId().equals(invoiceId)) throw new TenantResourceNotFoundException("Tax obligation");
        return new Preview(invoiceId, taxId, operation.map(InvoiceTaxOperationRepository.Operation::calculation)
                .orElseGet(() -> TaxCalculation.calculate(TaxCalculation.Mode.AMOUNT, tax.getAmount(), null, null)), currencies.forContract(tax.getContractId()), operation.isPresent());
    }

    @Transactional
    public Result create(CreateCalculatedTaxCommand command) {
        UUID company = tenants.requireCurrentTenant().companyId();
        var mode = authorization.requireChangeAuthorization(AccessModule.INVOICES, ChangeOperation.CREATE);
        require(command); operations.lock(company, command.operationId());
        String fingerprint = fingerprint(command);
        var existing = operations.find(company, command.operationId());
        if (existing.isPresent()) {
            var op = existing.get();
            if (!op.fingerprint().equals(fingerprint) || !op.invoiceId().equals(command.invoiceId())) throw new IllegalStateException("Tax operation ID already used with different inputs");
            if (op.taxId() != null) {
                var tax = taxRecords.findByIdAndCompanyId(op.taxId(), company).orElseThrow(() -> new TenantResourceNotFoundException("Tax obligation"));
                return new Result(new TaxObligationCreationResult(tax, payables.findByTaxObligationIdAndCompanyId(tax.getId(), company).orElse(null), null, ChangeAuthorizationMode.DIRECT), op.calculation(), true);
            }
            var request = requests.findByIdAndCompanyId(op.requestId(), company).orElseThrow();
            if (request.getStatus() != ChangeRequestStatus.PENDING) throw new IllegalStateException("Tax proposal was decided; use a new operation ID");
            return new Result(new TaxObligationCreationResult(null, null, request, ChangeAuthorizationMode.REQUEST_APPROVAL), null, true);
        }
        taxes.validate(command.legacy(), company);
        var invoice = invoices.findByIdAndCompanyId(command.invoiceId(), company).orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            var request = changes.create(new CreateChangeRequestCommand(AuditModule.INVOICES, ChangeRequestOperation.CREATE,
                    "CalculatedTaxObligation", null, 0, new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()));
            operations.remember(company, command.operationId(), command.invoiceId(), invoice.getContractId(), fingerprint, command.calculation(), null, request.getId());
            return new Result(new TaxObligationCreationResult(null, null, request, mode), null, false);
        }
        var result = taxes.applyCalculated(company, command.legacy(), null);
        operations.remember(company, command.operationId(), command.invoiceId(), invoice.getContractId(), fingerprint, command.calculation(), result.taxObligation().getId(), null);
        return new Result(result, command.calculation(), false);
    }

    @Transactional
    public void apply(ChangeRequest request) {
        var command = fromProposal(request.getSnapshot().proposedData().values()); require(command);
        operations.lock(request.getCompanyId(), command.operationId());
        var op = operations.find(request.getCompanyId(), command.operationId()).orElseThrow();
        if (!request.getId().equals(op.requestId()) || op.taxId() != null || !fingerprint(command).equals(op.fingerprint())) throw new IllegalStateException("Tax proposal no longer matches its operation");
        var result = taxes.applyCalculated(request.getCompanyId(), command.legacy(), request.getId());
        operations.complete(request.getCompanyId(), command.operationId(), request.getId(), result.taxObligation().getId());
    }

    private void require(CreateCalculatedTaxCommand c) {
        if (c == null || c.operationId() == null || c.invoiceId() == null || c.treatment() == null || c.name() == null || c.name().isBlank() || c.name().length() > 100
                || c.description() != null && c.description().length() > 1000 || c.justification() != null && c.justification().length() > 2000
                || c.calculation() == null || c.confirmedAmount() == null || c.confirmedAmount().compareTo(c.calculation().amount()) != 0) throw new IllegalArgumentException("Provide valid tax inputs and confirm exactly the official calculated amount before registering");
    }
    private Map<String, Object> proposal(CreateCalculatedTaxCommand c) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("operationId", c.operationId().toString()); p.put("invoiceId", c.invoiceId().toString()); p.put("receivableId", c.receivableId() == null ? null : c.receivableId().toString());
        p.put("name", c.name()); p.put("description", c.description()); p.put("dueOn", c.dueOn() == null ? null : c.dueOn().toString()); p.put("treatment", c.treatment().name()); p.put("justification", c.justification());
        p.put("calculationMode", c.calculation().mode().name()); p.put("baseAmount", c.calculation().baseAmount() == null ? null : c.calculation().baseAmount().toPlainString());
        p.put("ratePercent", c.calculation().ratePercent() == null ? null : c.calculation().ratePercent().toPlainString()); p.put("amount", c.calculation().amount().toPlainString()); p.put("confirmedAmount", c.confirmedAmount().toPlainString());
        return p;
    }
    private String fingerprint(CreateCalculatedTaxCommand c) { return NetBillingCapacityService.hash(json.writeValueAsString(proposal(c))); }
    private CreateCalculatedTaxCommand fromProposal(Map<String, Object> p) {
        var calculation = new TaxCalculation(TaxCalculation.Mode.valueOf((String) p.get("calculationMode")), decimal(p.get("baseAmount")), decimal(p.get("ratePercent")), decimal(p.get("amount")));
        return new CreateCalculatedTaxCommand(UUID.fromString((String) p.get("operationId")), UUID.fromString((String) p.get("invoiceId")), p.get("receivableId") == null ? null : UUID.fromString((String) p.get("receivableId")),
                (String) p.get("name"), (String) p.get("description"), p.get("dueOn") == null ? null : LocalDate.parse((String) p.get("dueOn")), TaxTreatment.valueOf((String) p.get("treatment")), (String) p.get("justification"), calculation, decimal(p.get("confirmedAmount")));
    }
    private BigDecimal decimal(Object v) { return v == null ? null : new BigDecimal(v.toString()); }
    public record Preview(UUID invoiceId, UUID taxObligationId, TaxCalculation calculation, CurrencyCode currency, boolean recorded) { }
    public record Result(TaxObligationCreationResult value, TaxCalculation calculation, boolean replayed) { }
}
