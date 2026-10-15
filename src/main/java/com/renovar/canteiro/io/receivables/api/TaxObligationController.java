package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateTaxObligationRequest;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationCreationResponse;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationResponse;
import com.renovar.canteiro.io.receivables.application.CreateTaxObligationCommand;
import com.renovar.canteiro.io.receivables.application.TaxObligationCreationResult;
import com.renovar.canteiro.io.receivables.application.TaxObligationService;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/invoices/{invoiceId}/tax-obligations")
@RequiredArgsConstructor
@Tag(name = "Tax obligations")
public class TaxObligationController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final TaxObligationService taxObligationService;

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
