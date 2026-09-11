package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.response.InvoiceAttachmentResponse;
import com.renovar.canteiro.io.receivables.application.InvoiceAttachmentUploadService;
import com.renovar.canteiro.io.receivables.application.UploadInvoiceAttachmentCommand;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceAttachmentController {
    private final InvoiceAttachmentUploadService uploadService;

    @PostMapping(path = "/{invoiceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Uploads a PDF or XML attachment for an invoice")
    public InvoiceAttachmentResponse upload(@PathVariable UUID invoiceId, @RequestParam InvoiceAttachmentType attachmentType,
                                            @RequestParam MultipartFile file) throws IOException {
        InvoiceAttachment attachment = uploadService.upload(new UploadInvoiceAttachmentCommand(invoiceId, attachmentType,
                file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        return new InvoiceAttachmentResponse(attachment.getId(), attachment.getInvoiceId(), attachment.getAttachmentType(),
                attachment.getOriginalFilename(), attachment.getContentType(), attachment.getContentSize(),
                attachment.getSha256(), attachment.getCreatedAt());
    }
}
