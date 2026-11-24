package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.application.GenerateContractBillingPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/billing-control/contracts")
public class ContractBillingReportController {
    private final GenerateContractBillingPdfService reports;
    @GetMapping(value="/{contractId}/report/pdf",produces="application/pdf")
    @Operation(summary="Exports contract billing control with registered invoices as columns",
        description="Requires BILLING.READ, BILLING.EXPORT, INVOICES.READ and INVOICES.EXPORT in the session tenant. Private streamed inline PDF, no-store, sanitized filename. Service grid: full description, contract identifier, official net commercial amount, sequential registered invoice amounts and remaining service balance. Three-column summary: contract total, invoiced total and remaining total. NF grid: sequence/reference, NF number, fiscal issue date, recorded amount, documentary remaining balance and status. Documentary balance uses the current official contract net amount minus cumulative REGISTERED invoices ordered by issuedOn and id, counted once per invoice; cancelled invoices are identified and excluded. This is the report contractual basis, not reconstruction of past amendments or the immutable cash registration snapshot. Existing V91 snapshots remain untouched and are not displayed as documentary balance. RECEIVABLES.READ plus RECEIVABLES.EXPORT enable a separate open-charge section including charges without NF; no cash summary in the main three parts. Exact tenant/allocation/service origins, existing net discount distribution, unavailable unreconciled service positions, bounded pagination, consistent read snapshot and export audit remain enforced. No mutation or pending proposal. Generation failures after streaming starts cannot become JSON Problem Details.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Private streamed PDF",content=@Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary"))),
        @ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
    public ResponseEntity<StreamingResponseBody> pdf(@PathVariable UUID contractId) {
        var report=reports.generate(contractId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(report.filename(),StandardCharsets.UTF_8).build().toString())
            .body(report.writer()::write);
    }
}
