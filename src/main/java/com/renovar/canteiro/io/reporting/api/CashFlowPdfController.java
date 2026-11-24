package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.GenerateCashFlowPdfService;
import com.renovar.canteiro.io.reporting.application.GenerateCashFlowPdfService.Prepared;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/cash-flow")
@Tag(name = "Cash flow")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Private PDF attachment",
            headers = {@Header(name = "Content-Disposition", description = "Attachment with a safe PDF filename"),
                    @Header(name = "Cache-Control", description = "no-store")},
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
})
public class CashFlowPdfController {
    private final GenerateCashFlowPdfService reports;

    @GetMapping(value = "/report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "exportCompanyCashFlowPdf", summary = "Downloads the company cash flow report",
            description = "Requires REPORTING.EXPORT and READ plus EXPORT for at least one of RECEIVABLES and PAYABLES. "
                    + "Shows realized cash, current residuals by due date, and payment comparison when authorized. "
                    + "Dates are inclusive through today UTC; horizonDays accepts 1 to 365. One currency per file.")
    public ResponseEntity<StreamingResponseBody> company(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(schema = @Schema(minimum = "1", maximum = "365", defaultValue = "30"))
            @RequestParam(defaultValue = "30") int horizonDays,
            @RequestParam CurrencyCode currency) {
        return response(reports.generate(null, from, to, horizonDays, currency));
    }

    @GetMapping(value = "/works/{workId}/report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "exportWorkCashFlowPdf", summary = "Downloads the cash flow report for one work",
            description = "Requires WORKS.READ, REPORTING.EXPORT and READ plus EXPORT for at least one financial source. "
                    + "Only authorized receipts and WORK payments of this work. Dates are inclusive through today UTC; "
                    + "horizonDays accepts 1 to 365. One currency per file.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public ResponseEntity<StreamingResponseBody> work(@PathVariable UUID workId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(schema = @Schema(minimum = "1", maximum = "365", defaultValue = "30"))
            @RequestParam(defaultValue = "30") int horizonDays,
            @RequestParam CurrencyCode currency) {
        return response(reports.generate(workId, from, to, horizonDays, currency));
    }

    private ResponseEntity<StreamingResponseBody> response(Prepared report) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(report.filename()).build().toString())
                .body(output -> reports.write(report, output));
    }
}
