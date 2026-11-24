package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayableService;
import com.renovar.canteiro.io.payables.application.PayableSettlementCommand;
import com.renovar.canteiro.io.payables.application.PayableSettlementService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/company/payables/{payableId}", produces = "application/json")
@Tag(name = "Payables")
@ApiResponses({
    @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
})
public class PayableSettlementController {
    private final PayableService payables;
    private final PayableSettlementService settlements;

    @GetMapping("/balance")
    @Operation(operationId = "getPayableBalance", summary = "Reads the official balance of one payable", description = "Requires PAYABLES.READ.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    public PayableBalanceResponse balance(@PathVariable UUID payableId) {
        return PayableBalanceResponse.from(payables.detail(payableId));
    }

    @GetMapping("/settlements")
    @Operation(operationId = "listPayableSettlements", summary = "Lists recorded payments and reversals for one payable",
            description = "Requires PAYABLES.READ. Ordered by effective date descending, recording time descending, then ID descending. page starts at 0; size is 1..100.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    public PageResponse<PayableSettlementResponse> history(@PathVariable UUID payableId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = settlements.history(payableId, new PageQuery(page, size, null).toPageable(Set.of()));
        return PageResponse.from(result, PayableSettlementResponse::from);
    }

    @PostMapping("/settlements")
    @Operation(operationId = "createPayableSettlement", summary = "Records an already made payment",
            description = "Requires PAYABLES.READ and CREATE authority. Records a partial or total payment in the title currency. operationId is idempotent within the company. No external money transfer.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = PayableSettlementChangeResponse.class))),
        @ApiResponse(responseCode = "200", description = "Identical replay of an applied payment", content = @Content(schema = @Schema(implementation = PayableSettlementChangeResponse.class))),
        @ApiResponse(responseCode = "202", description = "Approval request only, without official payment or balance change", content = @Content(schema = @Schema(implementation = PendingPayableSettlementResponse.class)))
    })
    public ResponseEntity<?> create(@PathVariable UUID payableId,
            @Valid @RequestBody PayableSettlementCreateRequest request) {
        return response(settlements.submit(new PayableSettlementCommand(request.operationId(), payableId, null,
                new BigDecimal(request.amount()), request.paidOn(), request.paymentMethod(), request.justification())));
    }

    @PostMapping("/settlements/{settlementId}/reversals")
    @Operation(operationId = "reversePayableSettlement", summary = "Reverses an official payment in full",
            description = "Requires PAYABLES.READ and CANCEL authority. Creates a signed linked reversal; the original remains immutable. operationId is idempotent within the company.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = PayableSettlementChangeResponse.class))),
        @ApiResponse(responseCode = "200", description = "Identical replay of an applied reversal", content = @Content(schema = @Schema(implementation = PayableSettlementChangeResponse.class))),
        @ApiResponse(responseCode = "202", description = "Approval request only, without official reversal or balance change", content = @Content(schema = @Schema(implementation = PendingPayableSettlementResponse.class)))
    })
    public ResponseEntity<?> reverse(@PathVariable UUID payableId, @PathVariable UUID settlementId,
            @Valid @RequestBody PayableSettlementReversalCreateRequest request) {
        return response(settlements.submit(new PayableSettlementCommand(request.operationId(), payableId, settlementId,
                null, request.reversedOn(), null, request.justification())));
    }

    private ResponseEntity<?> response(PayableSettlementService.Result result) {
        if (result.changeRequestId() != null) {
            return ResponseEntity.accepted().body(new PendingPayableSettlementResponse(result.changeRequestId(), result.authorizationMode()));
        }
        var body = new PayableSettlementChangeResponse(
                PayableSettlementResponse.from(result.settlement()), PayableBalanceResponse.from(result.position()),
                result.authorizationMode());
        return ResponseEntity.status(result.replay() ? 200 : 201).body(body);
    }
}
