package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.response.MeasurementDocumentResponse;
import com.renovar.canteiro.io.measurements.application.MeasurementDocumentUploadService;
import com.renovar.canteiro.io.measurements.application.GenerateMeasurementSpreadsheetService;
import com.renovar.canteiro.io.measurements.application.GeneratedMeasurementSpreadsheet;
import com.renovar.canteiro.io.measurements.application.GenerateMeasurementPdfService;
import com.renovar.canteiro.io.measurements.application.GeneratedMeasurementPdf;
import com.renovar.canteiro.io.measurements.application.UploadMeasurementDocumentCommand;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocument;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentType;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementDocumentController {
    private final MeasurementDocumentUploadService uploadService;
    private final GenerateMeasurementSpreadsheetService generateSpreadsheetService;
    private final GenerateMeasurementPdfService generatePdfService;

    @GetMapping(path = "/{measurementId}/versions/{versionId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Streams a PDF for a measurement version", description = "MEASUREMENTS.EXPORT. Ten landscape columns, private emitter branding, centered title/service bands, wrapped activities and official version totals. Every monetary value carries the immutable BRL/USD/EUR symbol. No public URL or cross-version sum.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Streaming PDF download",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<StreamingResponseBody> downloadPdf(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        GeneratedMeasurementPdf pdf = generatePdfService.generate(measurementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(pdf.writer()::writeTo);
    }

    @GetMapping(path = "/{measurementId}/versions/{versionId}/spreadsheet",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Streams an XLSX spreadsheet for a measurement version", description = "MEASUREMENTS.EXPORT. Ten visible columns. Hidden stable item/service fields, exact decimal inputs, preview-only formulas and very-hidden measurement/version/currency metadata preserve DRAFT reimport. BRL/USD/EUR symbols are presentation only; metadata mismatch returns 409 on import. Accepted history is never edited.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Streaming XLSX download",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<StreamingResponseBody> downloadSpreadsheet(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        GeneratedMeasurementSpreadsheet spreadsheet = generateSpreadsheetService.generate(measurementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(spreadsheet.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(spreadsheet.writer()::writeTo);
    }

    @GetMapping(path = "/{measurementId}/versions/{versionId}/spreadsheet/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Streams an inline PDF preview of the official measurement spreadsheet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inline PDF spreadsheet preview",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<StreamingResponseBody> previewSpreadsheet(@PathVariable UUID measurementId,
                                                                      @PathVariable UUID versionId) {
        GeneratedMeasurementPdf preview = generatePdfService.generateSpreadsheetPreview(measurementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(preview.writer()::writeTo);
    }

    @PostMapping(path = "/{measurementId}/versions/{versionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Uploads evidence or a spreadsheet for a measurement version")
    public MeasurementDocumentResponse upload(@PathVariable UUID measurementId, @PathVariable UUID versionId,
                                              @RequestParam MeasurementDocumentType documentType,
                                              @RequestParam MultipartFile file) throws IOException {
        MeasurementDocument document = uploadService.upload(new UploadMeasurementDocumentCommand(measurementId, versionId,
                documentType, file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        return new MeasurementDocumentResponse(document.getId(), document.getMeasurementId(), document.getMeasurementVersionId(),
                document.getDocumentType(), document.getOriginalFilename(), document.getContentType(), document.getContentSize(),
                document.getSha256(), document.getCreatedAt());
    }
}
