package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.response.InvoiceAttachmentResponse;
import com.renovar.canteiro.io.receivables.application.InvoiceAttachmentUploadService;
import com.renovar.canteiro.io.receivables.application.UploadInvoiceAttachmentCommand;
import com.renovar.canteiro.io.receivables.application.ReadInvoiceAttachmentsUseCase;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/company/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceAttachmentController {
    private final InvoiceAttachmentUploadService uploadService;
    private final ReadInvoiceAttachmentsUseCase readService;

    @GetMapping(path = "/{invoiceId}/attachments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists historical invoice attachment metadata in the authenticated company",
            description = "Requires INVOICES.READ. Missing/foreign invoice returns 404; existing invoice without attachments returns an empty page. Includes cancelled invoice history; no financial or audit changes. No storage access, public URL or fiscal contents. Default createdAt desc, id asc; Cache-Control no-store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official attachment metadata", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<PageResponse<InvoiceAttachmentResponse>> find(@PathVariable UUID invoiceId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: createdAt, attachmentType, originalFilename, contentSize; field,asc or field,desc. Repeat for multiple fields. Ties id asc; all sortable metadata fields are non-null.") @RequestParam(required = false) List<String> sort) {
        var result = readService.find(invoiceId, new PageQuery(page, size, sort)
                .toPageable(Set.of("createdAt", "attachmentType", "originalFilename", "contentSize")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.from(result, attachment ->
                new InvoiceAttachmentResponse(attachment.id(), attachment.invoiceId(), attachment.attachmentType(),
                        attachment.originalFilename(), attachment.contentType(), attachment.contentSize(), attachment.sha256(), attachment.createdAt())));
    }

    @GetMapping(path = "/{invoiceId}/attachments/{attachmentId}/download")
    @Operation(summary = "Downloads an existing private invoice PDF or XML attachment",
            description = "Requires both INVOICES.READ and INVOICES.EXPORT, never inferred from create authority. Validates tenant, invoice/attachment/contract chain and stored SHA-256/size before returning bytes; maximum 10 MiB. Cancelled invoice history is available. Records safe EXPORT audit only, never financial changes, content or storage key. No redirect/public URL; no-store and nosniff; no range/conditional-cache contract.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exact existing PDF/XML file, as an attachment",
                    headers = {@Header(name = "Content-Disposition", description = "attachment; sanitized filename with .pdf or .xml extension", schema = @Schema(type = "string")),
                            @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                            @Header(name = "X-Content-Type-Options", schema = @Schema(type = "string", example = "nosniff"))},
                    content = {@Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/xml", schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "503", description = "Stored content unavailable or integrity check failed", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<byte[]> download(@PathVariable UUID invoiceId, @PathVariable UUID attachmentId) {
        var file = readService.download(invoiceId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.content().length)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @PostMapping(path = "/{invoiceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Uploads the PDF attachment for an invoice", description = "Requires direct INVOICES.CREATE authority in the authenticated tenant. Accepts one PDF per invoice. Maximum 10 MiB; the .pdf extension and application/pdf MIME must match. PDF must parse, contain pages and not be encrypted. An existing PDF returns 409 INVOICE_PDF_ALREADY_ATTACHED. XML or incompatible file media returns 415 INVOICE_ATTACHMENT_PDF_REQUIRED. Legacy XML remains readable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attachment stored", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/ProblemDetail")
    })
    public InvoiceAttachmentResponse upload(@PathVariable UUID invoiceId,
                                            @Parameter(schema = @Schema(type = "string", allowableValues = {"PDF"}))
                                            @RequestParam InvoiceAttachmentType attachmentType,
                                            @RequestParam MultipartFile file) throws IOException {
        InvoiceAttachment attachment = uploadService.upload(new UploadInvoiceAttachmentCommand(invoiceId, attachmentType,
                file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        return new InvoiceAttachmentResponse(attachment.getId(), attachment.getInvoiceId(), attachment.getAttachmentType(),
                attachment.getOriginalFilename(), attachment.getContentType(), attachment.getContentSize(),
                attachment.getSha256(), attachment.getCreatedAt());
    }
}
