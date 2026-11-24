package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CalculateInvoiceTaxRequest;
import com.renovar.canteiro.io.receivables.api.request.CreateCalculatedInvoiceTaxRequest;
import com.renovar.canteiro.io.receivables.api.response.CalculatedTaxObligationCreationResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceTaxCalculationResponse;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationResponse;
import com.renovar.canteiro.io.receivables.application.CalculatedTaxObligationService;
import com.renovar.canteiro.io.receivables.application.FinancialCurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/invoices/{invoiceId}/tax-obligations")
@Tag(name = "Tax obligations")
@ApiResponses({@ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
public class CalculatedInvoiceTaxController {
    private final CalculatedTaxObligationService service;
    private final FinancialCurrencyService currencies;
    @PostMapping(value = "/preview", produces = "application/json")
    @Operation(summary = "Previews explicit-value or user-provided base/rate arithmetic without recording tax", description = "Effective INVOICES.CREATE/request authority and tenant. Registered invoice only. Does not infer invoice total as base or select incidence, rate, regime, treatment or due date. HALF_UP to two decimals after exact base*rate/100, rate up to four decimal places. No writes, tax, withholding, payable or audit of creation; no-store.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    public InvoiceTaxCalculationResponse preview(@PathVariable UUID invoiceId, @Valid @RequestBody CalculateInvoiceTaxRequest request) {
        return InvoiceTaxCalculationResponse.from(service.preview(invoiceId, request.calculate()));
    }
    @GetMapping(value = "/{taxObligationId}/calculation", produces = "application/json")
    @Operation(summary = "Reads recorded tax arithmetic including cancelled history", description = "INVOICES.READ, tenant and invoice/tax chain. No receivable/payable data. Legacy tax has explicit amount without fictitious base/rate and recorded=false. No effects or public URL.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    public ResponseEntity<InvoiceTaxCalculationResponse> recorded(@PathVariable UUID invoiceId, @PathVariable UUID taxObligationId) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(InvoiceTaxCalculationResponse.from(service.recorded(invoiceId, taxObligationId)));
    }
    @PostMapping(value = "/calculated", produces = "application/json")
    @Operation(summary = "Records a reviewed tax calculation idempotently with the existing tax treatment rules", description = "Effective INVOICES.CREATE/request authority and tenant. operationId scopes one immutable intent; same body replays 200 or pending 202, changed input/decided proposal conflicts 409. confirmedAmount must match official arithmetic. Registered invoice, receivable capacity and treatment/due date revalidated under existing locks, also at approval. 202 changes no official tax/withholding/payable. Legacy explicit amount POST unchanged. Does not create payment or cash receipt.")
    @ApiResponses({@ApiResponse(responseCode = "201", useReturnTypeSchema = true), @ApiResponse(responseCode = "200", useReturnTypeSchema = true), @ApiResponse(responseCode = "202", useReturnTypeSchema = true)})
    public ResponseEntity<CalculatedTaxObligationCreationResponse> create(@PathVariable UUID invoiceId, @Valid @RequestBody CreateCalculatedInvoiceTaxRequest request) {
        var result = service.create(request.toCommand(invoiceId)); var r = result.value(); var tax = r.taxObligation();
        var response = tax == null ? null : new TaxObligationResponse(tax.getId(), tax.getInvoiceId(), tax.getContractId(), tax.getReceivableId(), tax.getName(), tax.getDescription(), tax.getDueOn(), tax.getAmount(), tax.getTreatment(), tax.getStatus(), currencies.forContract(tax.getContractId()));
        var calculation = tax == null ? null : InvoiceTaxCalculationResponse.from(new CalculatedTaxObligationService.Preview(invoiceId, tax.getId(), result.calculation(), currencies.forContract(tax.getContractId()), true));
        return ResponseEntity.status(r.changeRequest() != null ? 202 : result.replayed() ? 200 : 201).body(new CalculatedTaxObligationCreationResponse(response, calculation,
                r.payable() == null ? null : r.payable().getId(), r.changeRequest() == null ? null : r.changeRequest().getId(), r.mode()));
    }
}
