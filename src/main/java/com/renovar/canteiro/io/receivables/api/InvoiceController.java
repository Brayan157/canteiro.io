package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateRegisteredInvoiceRequest;
import com.renovar.canteiro.io.receivables.api.request.InvoiceItemRequest;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.response.InvoiceItemResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceRegistrationResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceResponse;
import com.renovar.canteiro.io.receivables.api.response.FinancialCancellationResponse;
import com.renovar.canteiro.io.receivables.application.CreateRegisteredInvoiceCommand;
import com.renovar.canteiro.io.receivables.application.InvoiceItemCommand;
import com.renovar.canteiro.io.receivables.application.InvoiceRegistrationResult;
import com.renovar.canteiro.io.receivables.application.InvoiceRegistrationService;
import com.renovar.canteiro.io.receivables.application.CancelFinancialRecordCommand;
import com.renovar.canteiro.io.receivables.application.FinancialCancellationService;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceFilter;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.application.ReadInvoicesUseCase;
import com.renovar.canteiro.io.receivables.application.ReadInvoiceContractsUseCase;
import com.renovar.canteiro.io.receivables.api.response.InvoiceContractResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceSummaryResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceDetailsResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/company/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final InvoiceRegistrationService invoiceRegistrationService;
    private final FinancialCancellationService financialCancellationService;
    private final ReadInvoicesUseCase readInvoicesUseCase;
    private final ReadInvoiceContractsUseCase readInvoiceContractsUseCase;

    @GetMapping(value = "/contracts", produces = "application/json")
    @Operation(summary = "Lists contract groups represented by official invoices",
            description = "Requires only INVOICES.READ; tenant exclusively from session. One minimal contract row per group, including contracts represented only by cancelled invoices. No money, receipts or counts are aggregated. Literal case-insensitive name/reference/business-identifier search applied before count/pagination. Omit search for all groups; default contractName asc, contractId asc, nulls last. Not access to full contract, work or billing modules. Pending proposals create no group.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of minimal invoice contract groups", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<InvoiceContractResponse> findInvoiceContracts(
            @Parameter(description = "Literal fragment of contract name, reference or business identifier, at most 200 characters. Never UUID, customer document or tax key; %, _ and ! are literals.") @RequestParam(required = false) String search,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: contractName, contractReference, contractIdentifier. field,asc or field,desc; repeat for multiple fields. Always contractId asc tie-break, nulls last.") @RequestParam(required = false) List<String> sort) {
        return PageResponse.from(readInvoiceContractsUseCase.find(search, new PageQuery(page, size, sort)
                .toPageable(Set.of("contractName", "contractReference", "contractIdentifier"))), InvoiceContractResponse::from);
    }

    @GetMapping(value = "/contracts/{contractId}", produces = "application/json")
    @Operation(summary = "Restores exact minimal contract context for an invoice group",
            description = "Requires only INVOICES.READ and session tenant. Exact internal contractId selected from the index; not a user-entered search or name identity. Returns 404 for missing/foreign contract or a contract with no official invoice, including the same response for inaccessible context. Includes cancelled invoice history, independent of the current list filters. Use GET /invoices?contractId with this exact ID for its invoice page. No money, full contract or side effects.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Minimal exact invoice group context", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public InvoiceContractResponse getInvoiceContract(@PathVariable UUID contractId) {
        return InvoiceContractResponse.from(readInvoiceContractsUseCase.get(contractId));
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "Lists official invoices including cancelled history",
            description = "Requires INVOICES.READ. Tenant exclusively from session. Documentary status remains separate from receiptPosition. Receipt amounts require additional RECEIVABLES.READ and exact invoice-item ownership; the manual ledger supplies confirmed cash net of exact reversals. Unknown legacy ownership/credits/renegotiations remain unavailable with null amounts, never inferred from contract cash. Literal business filters, no fiscal key/document search. Default issuedOn desc, id asc; nulls last. No writes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official invoices", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<InvoiceSummaryResponse> find(
            @Parameter(description = "Optional internal contract ID; foreign/nonmatching returns empty page.") @RequestParam(required = false) UUID contractId,
            @Parameter(description = "Literal invoice number fragment, at most 100 characters.") @RequestParam(required = false) String number,
            @Parameter(description = "Literal series fragment, at most 50 characters.") @RequestParam(required = false) String series,
            @Parameter(description = "Literal contract name, reference or business identifier fragment, at most 200 characters. No UUID search required.") @RequestParam(required = false) String contractSearch,
            @Parameter(description = "Literal linked installment reference fragment, at most 100 characters; preserves cancelled history.") @RequestParam(required = false) String installmentReference,
            @Parameter(description = "Persisted status: DRAFT, REGISTERED or CANCELLED. Omit to include all.") @RequestParam(required = false) InvoiceStatus status,
            @Parameter(description = "Inclusive issue date lower bound, ISO yyyy-MM-dd.") @RequestParam(required = false) LocalDate issuedFrom,
            @Parameter(description = "Inclusive upper bound, ISO yyyy-MM-dd; must not precede issuedFrom.") @RequestParam(required = false) LocalDate issuedTo,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: number, series, issuedOn, status, createdAt. field,asc or field,desc; repeat for multiple fields. Nulls last, ties id asc.") @RequestParam(required = false) List<String> sort) {
        return PageResponse.from(readInvoicesUseCase.find(new InvoiceFilter(contractId, number, series, contractSearch,
                installmentReference, status, issuedFrom, issuedTo), new PageQuery(page, size, sort)
                .toPageable(Set.of("number", "series", "issuedOn", "status", "createdAt"))), InvoiceSummaryResponse::from);
    }

    @GetMapping(value = "/{invoiceId}", produces = "application/json")
    @Operation(summary = "Reads an official invoice and its exact item allocation origins",
            description = "Requires INVOICES.READ. Missing/foreign invoice returns404. Cancelled invoices/items remain readable. Item order id asc. invoice.receiptPosition uses exact manual invoice-item cash links and requires additional RECEIVABLES.READ; unknown historical ownership/adjustments remain unavailable with null amounts. No inferred payment from contract/title/allocation. Own official withholding reduces invoice net balance without distribution among items. No fiscal key, storage URL, attachment, tax or settlement payload. No writes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official invoice detail", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public InvoiceDetailsResponse get(@PathVariable UUID invoiceId) {
        return InvoiceDetailsResponse.from(readInvoicesUseCase.get(invoiceId));
    }

    @PostMapping("/{invoiceId}/cancellation")
    @Operation(summary = "Cancels an invoice, its items and tax effects atomically",
            description = "Internal documentary cancellation; does not cancel the debt or reverse money. Revalidates before proposal and approval. "
                    + "409 BUSINESS_RULE_VIOLATION reasons: INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS, "
                    + "INVOICE_CANCELLATION_HAS_TAX_PAYMENTS or INVOICE_CANCELLATION_REQUIRES_REVIEW. "
                    + "Shared titles with active receipts are conservatively blocked; no inferred invoice attribution. "
                    + "202 preserves all official invoice, tax and financial state until approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official operation result", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinancialCancellationResponse> cancel(@PathVariable UUID invoiceId,
                                                                 @Valid @RequestBody CancelFinancialRecordRequest request) {
        var result = financialCancellationService.cancelInvoice(invoiceId,
                new CancelFinancialRecordCommand(request.reason(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new FinancialCancellationResponse(result.entityId(), result.reversalId(), result.status(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PostMapping
    @Operation(summary = "Registers an invoice for one contract using complete billed services",description="Every selected service in a charge must include ALL its exact active base/composition allocation origins at their full frozen commercial amounts, before withholding. NF may cover complete groups from several charges of the SAME contract. Partial legacy requires review; no rewriting history or restriction of partial cash receipts. Direct creation and approval revalidate the same rule under contract lock;202 has no coverage/capacity effect.400 for malformed input;409 Problem Details reasons include INVOICE_SERVICE_GROUP_INCOMPLETE, INVOICE_SERVICE_GROUP_ALREADY_DOCUMENTED, INVOICE_SERVICE_GROUP_LEGACY_PARTIAL, INVOICE_SERVICE_GROUP_REVIEW_REQUIRED and INVOICE_FISCAL_IDENTITY_ALREADY_REGISTERED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Official operation result", content = @Content(schema = @Schema(implementation = InvoiceRegistrationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = InvoiceRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<InvoiceRegistrationResponse> register(
            @Valid @RequestBody CreateRegisteredInvoiceRequest request
    ) {
        InvoiceRegistrationResult result = invoiceRegistrationService.register(new CreateRegisteredInvoiceCommand(
                request.contractId(), request.number(), request.series(), request.accessKey(), request.issuedOn(),
                request.items().stream().map(this::toCommand).toList(), request.justification(),request.documentTotalAmount()==null?null:new java.math.BigDecimal(request.documentTotalAmount())
        ));
        return ResponseEntity.status(result.invoice() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new InvoiceRegistrationResponse(
                        toResponse(result.invoice(), result.items()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    private InvoiceItemCommand toCommand(InvoiceItemRequest request) {
        return new InvoiceItemCommand(request.billingAllocationId(), request.description(), request.allocatedAmount());
    }

    private InvoiceResponse toResponse(Invoice invoice, List<InvoiceItem> items) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceResponse(
                invoice.getId(), invoice.getContractId(), invoice.getNumber(), invoice.getSeries(), invoice.getAccessKey(),
                invoice.getIssuedOn(), invoice.getTotalAmount(), invoice.getStatus(), items.stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(), item.getBillingAllocationId(), item.getContractServiceId(), item.getDescription(),
                        item.getAllocatedAmount(), item.getStatus()
                )).toList(), currencies.forContract(invoice.getContractId())
        );
    }
}
