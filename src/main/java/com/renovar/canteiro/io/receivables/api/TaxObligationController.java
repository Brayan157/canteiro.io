package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateTaxObligationRequest;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationCreationResponse;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationResponse;
import com.renovar.canteiro.io.receivables.application.CreateTaxObligationCommand;
import com.renovar.canteiro.io.receivables.application.TaxObligationCreationResult;
import com.renovar.canteiro.io.receivables.application.TaxObligationService;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.receivables.application.ReadInvoiceTaxesUseCase;
import com.renovar.canteiro.io.receivables.api.response.InvoiceTaxObligationResponse;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/company/invoices/{invoiceId}/tax-obligations")
@RequiredArgsConstructor
@Tag(name = "Tax obligations")
public class TaxObligationController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final TaxObligationService taxObligationService;
    private final ReadInvoiceTaxesUseCase readInvoiceTaxesUseCase;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists official historical taxes of an invoice in the authenticated company",
            description = "Requires INVOICES.READ, never inferred from create authority. Tenant comes exclusively from the session; validates invoice/contract/tax and linked receivable chain. Existing invoice without taxes returns an empty page; missing/foreign invoice returns 404. Includes REGISTERED and CANCELLED by default, including cancelled invoices. No receivable/payable IDs, details or balances are disclosed. GET never creates a tax/payable, reapplies withholding, changes financial state or writes financial audit. Default createdAt desc, id asc; no-store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official invoice taxes", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<PageResponse<InvoiceTaxObligationResponse>> find(@PathVariable UUID invoiceId,
            @Parameter(description = "Optional exact persisted status: REGISTERED or CANCELLED. Omit to include both.") @RequestParam(required = false) TaxObligationStatus status,
            @Parameter(description = "Optional exact treatment: PAYABLE_BY_COMPANY, WITHHELD_BY_CUSTOMER or INFORMATIONAL.") @RequestParam(required = false) TaxTreatment treatment,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed non-null fields: createdAt, name, treatment, status. field,asc or field,desc; repeat for multiple fields. Ties id asc.") @RequestParam(required = false) List<String> sort) {
        var result = readInvoiceTaxesUseCase.find(invoiceId, status, treatment,
                new PageQuery(page, size, sort).toPageable(Set.of("createdAt", "name", "treatment", "status")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.from(result, InvoiceTaxObligationResponse::from));
    }

    @PostMapping
    @Operation(summary = "Records a payable, withheld, or informational invoice tax")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Official operation result", content = @Content(schema = @Schema(implementation = TaxObligationCreationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = TaxObligationCreationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<TaxObligationCreationResponse> create(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody CreateTaxObligationRequest request
    ) {
        TaxObligationCreationResult result = taxObligationService.create(new CreateTaxObligationCommand(
                invoiceId, request.receivableId(), request.name(), request.description(), request.dueOn(), request.amount(),
                request.treatment(), request.justification()
        ));
        return ResponseEntity.status(result.taxObligation() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new TaxObligationCreationResponse(
                        toResponse(result.taxObligation()), result.payable() == null ? null : result.payable().getId(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()
                ));
    }

    private TaxObligationResponse toResponse(TaxObligation taxObligation) {
        if (taxObligation == null) {
            return null;
        }
        return new TaxObligationResponse(
                taxObligation.getId(), taxObligation.getInvoiceId(), taxObligation.getContractId(),
                taxObligation.getReceivableId(), taxObligation.getName(), taxObligation.getDescription(),
                taxObligation.getDueOn(), taxObligation.getAmount(), taxObligation.getTreatment(), taxObligation.getStatus(), currencies.forContract(taxObligation.getContractId())
        );
    }
}
