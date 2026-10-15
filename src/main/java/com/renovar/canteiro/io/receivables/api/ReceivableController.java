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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/receivables")
@RequiredArgsConstructor
@Tag(name = "Receivables")
public class ReceivableController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final ReceivableSettlementService receivableSettlementService;
    private final GetReceivableBalanceUseCase getReceivableBalanceUseCase;
    private final FinancialCancellationService financialCancellationService;

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
    @Operation(summary = "Records a partial or full receivable settlement")
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
    @Operation(summary = "Calculates the receivable balance and its current overdue status")
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
                balance.outstandingAmount(), status, currencies.forReceivable(balance.receivableId())
        );
    }
}
