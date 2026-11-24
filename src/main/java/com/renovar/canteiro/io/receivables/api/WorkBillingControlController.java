package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.receivables.application.GenerateWorkBillingPdfService;
import com.renovar.canteiro.io.receivables.application.ReadWorkBillingControlUseCase;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/billing-control/works")
public class WorkBillingControlController {
    private final ReadWorkBillingControlUseCase control;
    private final GenerateWorkBillingPdfService reports;

    @GetMapping(value = "/{workId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getWorkBillingControl", summary = "Reads official documentary billing consolidated by work",
            description = "Requires WORKS.READ, BILLING.READ and INVOICES.READ. Totals cover all contracts of the session company's work, independently of the contract page. Results are separated by contract currency, without conversion. Invoiced amounts count each registered invoice once and exclude cancelled invoices. Documentary balance is net contract amount minus invoiced amount. Contracts are ordered by ID ascending. A work with no contracts returns empty arrays.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<WorkBillingControlResponse> getWorkBillingControl(@PathVariable UUID workId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size) {
        var position = control.read(workId, new PageQuery(page, size, null).toPageable(Set.of()));
        var totals = position.totalsByCurrency().stream().map(total -> new CurrencyTotalResponse(total.currency(),
                total.contractCount(), total.contractNetAmount().toPlainString(), total.invoicedAmount().toPlainString(),
                total.documentaryBalance().toPlainString())).toList();
        var contracts = PageResponse.from(position.contracts(), row -> new ContractPositionResponse(row.id(),
                row.identifier(), row.reference(), row.name(), ContractStatus.valueOf(row.status()), row.currency(),
                row.commercial().netAmount().toPlainString(), row.invoicedAmount().toPlainString(),
                row.documentaryBalance().toPlainString(), "/api/v1/company/billing-control/contracts/" + row.id()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new WorkBillingControlResponse(workId, position.work().getName(), position.work().getReference(), totals, contracts));
    }

    @GetMapping(value = "/{workId}/report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "exportWorkBillingControlPdf", summary = "Exports a private documentary billing report for the work",
            description = "Requires WORKS.READ, BILLING.READ, INVOICES.READ, BILLING.EXPORT and INVOICES.EXPORT. Inline no-store PDF with the work title and reference, a contract table (name, official contract reference as number, net contract amount, registered invoice amount and documentary balance), then totals per currency. All monetary amounts include the currency symbol. Cancelled invoices are excluded from invoiced amounts. Bounded database batches and disk-backed page streams, no client pagination. The export is audited. Failures after streaming begins cannot be returned as Problem Details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private streamed inline PDF",
                    headers = {
                            @Header(name = "Content-Disposition", schema = @Schema(type = "string", example = "inline; filename=controle-faturamento-obra.pdf")),
                            @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store"))
                    },
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<StreamingResponseBody> exportWorkBillingControlPdf(@PathVariable UUID workId) {
        var report = reports.generate(workId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(report.filename(), StandardCharsets.UTF_8).build().toString())
                .body(report.writer()::write);
    }

    public record WorkBillingControlResponse(UUID workId, String workName,
            @Schema(types = {"string", "null"}) String workReference,
            List<CurrencyTotalResponse> totalsByCurrency, PageResponse<ContractPositionResponse> contracts) { }

    public record CurrencyTotalResponse(CurrencyCode currency, long contractCount,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String contractNetAmount,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String invoicedAmount,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String documentaryBalance) { }

    public record ContractPositionResponse(UUID contractId,
            @Schema(types = {"string", "null"}) String contractIdentifier,
            @Schema(types = {"string", "null"}, description = "Official contract reference shown as the contract number in work billing.") String contractReference,
            String contractName,
            ContractStatus contractStatus, CurrencyCode currency,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String contractNetAmount,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String invoicedAmount,
            @Schema(type = "string", format = "decimal", pattern = "^-?\\d+\\.\\d{2}$") String documentaryBalance,
            String detailsUrl) { }
}
