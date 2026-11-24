package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateInvoiceSettlementRequest;
import com.renovar.canteiro.io.receivables.api.response.InvoiceSettlementLineResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceSettlementOptionsResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceSettlementResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSettlementResponse;
import com.renovar.canteiro.io.receivables.application.FinancialCurrencyService;
import com.renovar.canteiro.io.receivables.application.InvoiceSettlementService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/receivables/{receivableId}")
public class InvoiceSettlementController {
    private final InvoiceSettlementService service;
    private final FinancialCurrencyService currencies;

    @GetMapping("/invoice-settlement-options")
    @Operation(summary = "Gets official receipt eligibility and paged exact invoice-item origins",
            description = "Requires RECEIVABLES.READ and INVOICES.READ; tenant from session only. Fixed order invoice issuedOn, invoiceId, itemId ascending. page>=0, size1..100. Maximum per line overlaps invoice/title capacity: never sum limits. Registered customer withholding is proportionally distributed by exact gross invoice items within the same original title; cents are deterministic. withheldItemAmount, netInvoicedItemAmount and netAvailableItemAmount are official nullable decimal strings. Uses registered invoices and active service allocations of the title's exact contract/installment. Unknown legacy cash, credits and renegotiations require reconciliation. totalSettlementProposal is independent of page and uses the exact billing allocation/invoice item origins for the entire title balance only when the distribution is uniquely determined. At most100 source candidates; incomplete invoice coverage, unproven withholding scope, incompatible historical cash or more than100 retained invoice items returns unavailable with null amount and empty lines. No redistribution across titles and no FIFO cash choice; partial receipts remain manual. No mutation, reservation or implied permission to create. Confirm the proposal explicitly; POST and approval revalidate all limits. The legacy generic settlement endpoint remains blocked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<InvoiceSettlementOptionsResponse> options(@PathVariable UUID receivableId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(InvoiceSettlementOptionsResponse.from(
                service.options(receivableId, new PageQuery(page, size, null).toPageable(Set.of()))));
    }

    @PostMapping("/invoice-settlements")
    @Operation(summary = "Registers a manually allocated receipt with exact invoice-item origins",
            description = "Requires RECEIVABLES CREATE authorization and INVOICES.READ. Only explicit manual values, positive canonical decimal strings; sum of lines equals amount. Same session tenant/contract/installment and exact composition; no automatic distribution. Revalidates title, invoice and item capacities and UTC date under ordered transactional locks. operationId is tenant-scoped idempotency; different payload gives409, repeat gives same receipt/proposal. 202 creates only one pending proposal and no financial reservation. Approval revalidates. Legacy generic receipts remain blocked; no external bank movement, fiscal replacement or invented historical attribution.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New official receipt", content = @Content(schema = @Schema(implementation = InvoiceSettlementResponse.class))),
            @ApiResponse(responseCode = "200", description = "Replay of the same official receipt", content = @Content(schema = @Schema(implementation = InvoiceSettlementResponse.class))),
            @ApiResponse(responseCode = "202", description = "Only a pending proposal", content = @Content(schema = @Schema(implementation = InvoiceSettlementResponse.Pending.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<?> create(@PathVariable UUID receivableId, @Valid @RequestBody CreateInvoiceSettlementRequest request) {
        var result = service.create(request.toCommand(receivableId));
        if (result.changeRequest() != null) return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new InvoiceSettlementResponse.Pending(result.changeRequest().getId(), result.mode(), result.replayed()));
        var value = result.settlement();
        var currency = currencies.forContract(value.getContractId());
        var settlement = new ReceivableSettlementResponse(value.getId(), value.getReceivableId(), value.getContractId(), value.getReceivedOn(), value.getAmount(),
                value.getEntryType(), value.getReversalOfSettlementId(), value.getPaymentMethod(), value.getNote(), value.getStatus(), currency);
        return ResponseEntity.status(result.replayed() ? 200 : 201).cacheControl(CacheControl.noStore()).body(new InvoiceSettlementResponse(settlement,
                result.items().stream().map(InvoiceSettlementLineResponse::from).toList(), currency, result.balance().confirmedSettlementAmount().setScale(2).toPlainString(),
                result.balance().outstandingAmount().setScale(2).toPlainString(), result.mode(), result.replayed()));
    }
}
