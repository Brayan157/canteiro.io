package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateReceivableSettlementRequest;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.response.ReceivableBalanceResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSettlementApprovalResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSettlementResponse;
import com.renovar.canteiro.io.receivables.api.response.FinancialCancellationResponse;
import com.renovar.canteiro.io.receivables.application.CreateReceivableSettlementCommand;
import com.renovar.canteiro.io.receivables.application.GetReceivableBalanceUseCase;
import com.renovar.canteiro.io.receivables.application.ReceivableBalanceSnapshot;
import com.renovar.canteiro.io.receivables.application.ReceivableSettlementResult;
import com.renovar.canteiro.io.receivables.application.ReceivableSettlementService;
import com.renovar.canteiro.io.receivables.application.CancelFinancialRecordCommand;
import com.renovar.canteiro.io.receivables.application.FinancialCancellationService;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableFinancialView;
import com.renovar.canteiro.io.receivables.application.ReadReceivablesUseCase;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSummaryResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableDetailsResponse;
import com.renovar.canteiro.io.receivables.application.ReadReceivableSettlementsUseCase;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSettlementHistoryResponse;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/company/receivables")
@RequiredArgsConstructor
@Tag(name = "Receivables")
public class ReceivableController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final ReceivableSettlementService receivableSettlementService;
    private final GetReceivableBalanceUseCase getReceivableBalanceUseCase;
    private final FinancialCancellationService financialCancellationService;
    private final ReadReceivablesUseCase readReceivablesUseCase;
    private final ReadReceivableSettlementsUseCase readReceivableSettlementsUseCase;

    @GetMapping(value = "/{receivableId}/settlements", produces = "application/json")
    @Operation(summary = "Lists historical receipts and reversals of one receivable",
            description = "Requires only RECEIVABLES.READ, never BILLING.READ; tenant exclusively from session. Validates title/contract/company chain and exact original/reversal links, not shared service/name/date/amount. Includes cancelled entries and entries of cancelled/renegotiated titles by default. Original receipt is not rewritten when reversed. Signed persisted amounts and immutable contract currency; no notes, bank/fiscal data, allocation detail or audit payload. Empty valid title returns 200 empty page; missing/foreign title returns 404. No proposal 202 or financial/audit mutation. Balance remains /{receivableId}/balance; never sum locally or infer reversal permission. Default createdAt desc, id asc; nulls last.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official historical receipts and reversals", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<ReceivableSettlementHistoryResponse> history(@PathVariable UUID receivableId,
            @Parameter(description = "Exact SETTLEMENT or REVERSAL; omitted includes both.") @RequestParam(required = false) ReceivableSettlementEntryType entryType,
            @Parameter(description = "Exact persisted CONFIRMED or CANCELLED; omitted includes both. Reversed original may still be CONFIRMED.") @RequestParam(required = false) ReceivableSettlementStatus status,
            @Parameter(description = "Exact persisted payment method, not a bank account or Pix key.") @RequestParam(required = false) ReceivableSettlementPaymentMethod paymentMethod,
            @Parameter(description = "Inclusive entry business date lower bound, ISO yyyy-MM-dd. Reversals use their reversal date.") @RequestParam(required = false) LocalDate receivedFrom,
            @Parameter(description = "Inclusive upper bound; must not precede receivedFrom.") @RequestParam(required = false) LocalDate receivedTo,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: receivedOn, createdAt, entryType, paymentMethod, status. field,asc or field,desc; repeat for multiple fields. Default createdAt desc, id asc, nulls last.") @RequestParam(required = false) List<String> sort) {
        return PageResponse.from(readReceivableSettlementsUseCase.find(receivableId,
                new ReceivableSettlementFilter(entryType, status, paymentMethod, receivedFrom, receivedTo),
                new PageQuery(page, size, sort).toPageable(Set.of("receivedOn", "createdAt", "entryType", "paymentMethod", "status"))),
                ReceivableSettlementHistoryResponse::from);
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "Lists official receivable titles and their readable business context",
            description = "Requires RECEIVABLES.READ; tenant exclusively from the session. financialView filters the official current position BEFORE pagination and count. Omitted/ALL preserves historical compatibility; the operational UI should explicitly request OUTSTANDING. financialPosition separates confirmed cash net of reversals, withholding, credits and outstanding. Cancelled/renegotiated titles appear only in ALL. Persisted status and face value are unchanged. Literal business filters combine with AND. No writes, financial reservations, invoice attribution or access to other modules. Default dueOn asc, id asc, nulls last.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official titles, never pending proposals", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<ReceivableSummaryResponse> find(
            @Parameter(description = "Literal reference, installment reference, contract name/reference/identifier, work name/reference or customer name fragment; max 200. Never document, tax or bank data.") @RequestParam(required = false) String search,
            @Parameter(description = "Literal title reference fragment, max 100.") @RequestParam(required = false) String reference,
            @Parameter(description = "Literal contract name/reference/business identifier fragment, max 200.") @RequestParam(required = false) String contractSearch,
            @Parameter(description = "Literal work name/reference fragment, max 200.") @RequestParam(required = false) String workSearch,
            @Parameter(description = "Literal final customer name fragment, max 200; not document.") @RequestParam(required = false) String customerName,
            @Parameter(description = "Literal originating billing installment reference fragment, max 100.") @RequestParam(required = false) String billingReference,
            @Parameter(description = "Optional internal contract context ID; foreign/nonmatching yields empty page.") @RequestParam(required = false) UUID contractId,
            @Parameter(description = "Optional internal work context ID; foreign/nonmatching yields empty page.") @RequestParam(required = false) UUID workId,
            @Parameter(description = "Optional internal final customer context ID; foreign/nonmatching yields empty page.") @RequestParam(required = false) UUID finalCustomerId,
            @Parameter(description = "Exact persisted status; omit for all including cancelled/renegotiated. Does not filter derived overdue/current balance status.") @RequestParam(required = false) ReceivableStatus status,
            @Parameter(description = "ALL (default): historical titles; OUTSTANDING: active titles with official outstandingAmount > 0, including partial receipts; RECEIVED: active titles with outstandingAmount = 0 AND confirmed cash > 0. Fully adjusted titles with no cash appear only in ALL. Credits/withholding never become cash. Combined with other filters before count/pagination.", schema = @Schema(defaultValue = "ALL", allowableValues = {"ALL", "OUTSTANDING", "RECEIVED"})) @RequestParam(required = false) ReceivableFinancialView financialView,
            @Parameter(description = "Exact immutable contract currency. No conversion or mixed-currency total.") @RequestParam(required = false) CurrencyCode currency,
            @Parameter(description = "Inclusive own title due date lower bound, ISO yyyy-MM-dd.") @RequestParam(required = false) LocalDate dueFrom,
            @Parameter(description = "Inclusive upper bound; must not precede dueFrom.") @RequestParam(required = false) LocalDate dueTo,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: dueOn, issuedOn, reference, status, createdAt, contractName, workName, finalCustomerName. field,asc or field,desc; repeat for multiple fields. Nulls last, ties id asc.") @RequestParam(required = false) List<String> sort) {
        return PageResponse.from(readReceivablesUseCase.find(new ReceivableFilter(search, reference, contractSearch, workSearch,
                customerName, billingReference, contractId, workId, finalCustomerId, status, currency, dueFrom, dueTo, financialView),
                new PageQuery(page, size, sort).toPageable(Set.of("dueOn", "issuedOn", "reference", "status", "createdAt",
                        "contractName", "workName", "finalCustomerName"))), ReceivableSummaryResponse::from);
    }

    @GetMapping(value = "/{receivableId}", produces = "application/json")
    @Operation(summary = "Reads basic official receivable title and context",
            description = "Requires only RECEIVABLES.READ. Same tenant and contract/work/customer/installment chain validated on the server, including original renegotiation lineage. Missing/foreign returns 404. Cancelled and renegotiated titles remain readable. No tax, invoice, billing allocation, measurement or settlement detail. Current financial position remains GET /{receivableId}/balance; status here is persisted and originalAmount is face value. No writes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Basic official title; no derived financial balance", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ReceivableDetailsResponse get(@PathVariable UUID receivableId) {
        return ReceivableDetailsResponse.from(readReceivablesUseCase.get(receivableId));
    }

    @PostMapping("/{receivableId}/settlements/{settlementId}/reversal")
    @Operation(summary = "Reverses a settlement and reopens the receivable balance atomically")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Official operation result", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinancialCancellationResponse> reverse(@PathVariable UUID receivableId,
                                                                  @PathVariable UUID settlementId,
                                                                  @Valid @RequestBody CancelFinancialRecordRequest request) {
        var result = financialCancellationService.reverseSettlement(receivableId, settlementId,
                new CancelFinancialRecordCommand(request.reason(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new FinancialCancellationResponse(result.entityId(), result.reversalId(), result.status(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PostMapping("/{receivableId}/settlements")
    @Operation(summary = "Records a partial or full receivable settlement",
            description = "LEGACY COMMAND BLOCKED: generic receipts, direct or approval-only, return 409 BUSINESS_RULE_VIOLATION with reason SETTLEMENT_INVOICE_LINK_REQUIRED. Use the distinct invoice-settlement-options/invoice-settlements flow with exact manual invoice-item links when available. This legacy operation creates no proposal, reservation or cash entry; old pending approvals remain blocked. Historical reads/reversals remain available. receivedOn must not exceed the official current UTC date; future dates return400 with a receivedOn violation. No runtime bypass or generic invoice-presence fallback.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Official operation result", content = @Content(schema = @Schema(implementation = ReceivableSettlementApprovalResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = ReceivableSettlementApprovalResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ReceivableSettlementApprovalResponse> settle(
            @PathVariable UUID receivableId,
            @Valid @RequestBody CreateReceivableSettlementRequest request
    ) {
        ReceivableSettlementResult result = receivableSettlementService.settle(new CreateReceivableSettlementCommand(
                receivableId, request.receivedOn(), request.amount(), request.paymentMethod(), request.note(),
                request.justification()
        ));
        return ResponseEntity.status(result.settlement() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new ReceivableSettlementApprovalResponse(
                        toSettlementResponse(result.settlement()),
                        result.balance() == null ? null : toBalanceResponse(result.balance(), result.receivableStatus()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    @GetMapping("/{receivableId}/balance")
    @Operation(summary = "Calculates the receivable balance and its current overdue status",
            description = "Requires RECEIVABLES.READ in the authenticated company. maxReceivedOn is the official maximum receipt date (UTC calendar, yyyy-MM-dd), not a schedule. settlementCreationAvailable remains false for the legacy generic /settlements command, with SETTLEMENT_INVOICE_LINK_REQUIRED. The distinct manual invoice-item flow uses /invoice-settlement-options and /invoice-settlements; this flag must not enable the legacy form. Historical reads/reversals remain available. Balance alone does not authorize a receipt; no reservation or historical correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official operation result", content = @Content(schema = @Schema(implementation = ReceivableBalanceResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ReceivableBalanceResponse balance(@PathVariable UUID receivableId) {
        ReceivableBalanceSnapshot snapshot = getReceivableBalanceUseCase.get(receivableId);
        return toBalanceResponse(snapshot.balance(), snapshot.currentStatus());
    }

    private ReceivableSettlementResponse toSettlementResponse(ReceivableSettlement settlement) {
        if (settlement == null) {
            return null;
        }
        return new ReceivableSettlementResponse(
                settlement.getId(), settlement.getReceivableId(), settlement.getContractId(), settlement.getReceivedOn(),
                settlement.getAmount(), settlement.getEntryType(), settlement.getReversalOfSettlementId(),
                settlement.getPaymentMethod(), settlement.getNote(), settlement.getStatus(), currencies.forContract(settlement.getContractId())
        );
    }

    private ReceivableBalanceResponse toBalanceResponse(ReceivableBalance balance,
                                                         com.renovar.canteiro.io.receivables.domain.ReceivableStatus status) {
        return new ReceivableBalanceResponse(
                balance.receivableId(), balance.originalAmount(), balance.withheldAmount(), balance.creditedAmount(),balance.netReceivableAmount(),
                balance.confirmedSettlementAmount(),
                balance.outstandingAmount(), status, currencies.forReceivable(balance.receivableId()),
                getReceivableBalanceUseCase.maximumReceivedOn(), getReceivableBalanceUseCase.settlementCreationAvailable(),
                getReceivableBalanceUseCase.settlementCreationBlockReason()
        );
    }
}
