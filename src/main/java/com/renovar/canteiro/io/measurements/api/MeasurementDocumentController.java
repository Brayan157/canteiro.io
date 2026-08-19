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
    @Operation(summary = "Generates a PDF for a measurement version")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        GeneratedMeasurementPdf pdf = generatePdfService.generate(measurementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(pdf.content());
    }

    @GetMapping(path = "/{measurementId}/versions/{versionId}/spreadsheet",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Generates an XLSX spreadsheet for a measurement version")
    public ResponseEntity<byte[]> downloadSpreadsheet(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        GeneratedMeasurementSpreadsheet spreadsheet = generateSpreadsheetService.generate(measurementId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(spreadsheet.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(spreadsheet.content());
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
