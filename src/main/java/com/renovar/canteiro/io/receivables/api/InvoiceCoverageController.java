package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.application.ReadInvoiceCoverageUseCase;
import com.renovar.canteiro.io.receivables.api.response.InvoiceCoverageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class InvoiceCoverageController {
    private final ReadInvoiceCoverageUseCase read;
    @GetMapping(value="/api/v1/company/billing-installments/{billingInstallmentId}/invoice-coverage",produces="application/json")
    @Operation(summary="Reads complete billed-service groups and missing invoice coverage",description="Requires INVOICES.READ and BILLING.READ, session tenant only. Stable projected group identity scopes company/contract/installment/service and includes ALL exact active base/composition allocation origins; no persistence or title-name matching. Amounts are canonical decimal strings before withholding. Select whole NOT_DOCUMENTED groups using all returned allocation IDs/amounts in invoice creation. Partial legacy is visible but not selectable; no ledger rewrite or effect on partial receipts. A single invoice may cover groups of several charges in the same contract. Read does not reserve; registration and approval revalidate under contract lock.")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
    public ResponseEntity<InvoiceCoverageResponse> get(@PathVariable UUID billingInstallmentId){
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(InvoiceCoverageResponse.from(read.read(billingInstallmentId)));
    }
}
