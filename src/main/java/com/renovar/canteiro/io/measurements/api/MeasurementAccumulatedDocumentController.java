package com.renovar.canteiro.io.measurements.api;
import com.renovar.canteiro.io.measurements.application.GenerateMeasurementAccumulatedReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
@RestController @RequestMapping("/api/v1/company/measurements") @RequiredArgsConstructor @Tag(name="Measurements")
@ApiResponses({@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail")})
public class MeasurementAccumulatedDocumentController {
    private final GenerateMeasurementAccumulatedReportService reports;
    @GetMapping(value="/{measurementId}/accumulated/pdf",produces="application/pdf")
    @Operation(summary="Streams the complete accumulated measurement PDF, including the identified pending advance",
        description="MEASUREMENTS.EXPORT. Ten landscape columns, private tenant logo with safe fallback, centered title/service bands, wrapped descriptions and repeated page headers. Every monetary value uses the immutable BRL/USD/EUR symbol. Official accepted/pending subtotals and accepted/received/remaining totals stay separate. Retired artifacts and corrective duplicates are excluded; historical reports keep their version scope.")
    @ApiResponse(responseCode="200",description="PDF download",content=@Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary")))
    public ResponseEntity<StreamingResponseBody> pdf(@PathVariable UUID measurementId){return response(measurementId,false,false);}
    @GetMapping(value="/{measurementId}/accumulated/spreadsheet",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary="Streams the complete accumulated XLSX report, read-only for import purposes",
        description="MEASUREMENTS.EXPORT. Ten visible columns, private tenant branding, stable continuous identities and official accepted/pending subtotals. BRL/USD/EUR presentation preserves decimal precision. This accumulated report is read-only and cannot be imported; use the current DRAFT version XLSX for synchronization.")
    @ApiResponse(responseCode="200",description="XLSX download",content=@Content(mediaType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",schema=@Schema(type="string",format="binary")))
    public ResponseEntity<StreamingResponseBody> spreadsheet(@PathVariable UUID measurementId){return response(measurementId,true,false);}
    @GetMapping(value="/{measurementId}/accumulated/spreadsheet/preview",produces="application/pdf")
    @Operation(summary="Streams an inline PDF preview using the same accumulated contents and financial snapshot")
    @ApiResponse(responseCode="200",description="Inline PDF",content=@Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary")))
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable UUID measurementId){return response(measurementId,false,true);}
    private ResponseEntity<StreamingResponseBody> response(UUID mid,boolean spreadsheet,boolean inline){
        var report=reports.generate(mid,spreadsheet);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(spreadsheet?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/pdf"))
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,
                (inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(report.filename(),StandardCharsets.UTF_8).build().toString())
            .body(report.writer()::writeTo);
    }
}

