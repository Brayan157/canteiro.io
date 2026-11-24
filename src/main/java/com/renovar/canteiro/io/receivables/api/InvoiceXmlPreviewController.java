package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.application.PreviewInvoiceXmlUseCase;
import com.renovar.canteiro.io.receivables.api.response.InvoiceXmlPreviewResponse;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class InvoiceXmlPreviewController {
    private static final int LIMIT=10*1024*1024;
    private final PreviewInvoiceXmlUseCase preview;
    @PostMapping(value="/api/v1/company/invoices/xml/preview",consumes="multipart/form-data",produces="application/json")
    @Operation(summary="Previews an issued national NFSe XML without financial effects",description="Requires INVOICES.READ and invoice creation authority; optional initial billingInstallmentId additionally requires BILLING.READ and must share the selected contract/session tenant. Multipart file and contractId. Up to10MiB, .xml/application/xml or text/xml. Only declared national namespace/version1.01; secure bounded parsing, no DTD/XXE/XInclude/network. Does NOT validate XSD, signature, authenticity, current fiscal status or bank data. DPS fields remain separate from NF; no substitution/cancellation, inferred service matching, zero defaults or automatic taxes. Monetary/percentage values are strings with source paths. Retention is reported separately from amount payable/received; partial breakdown remains review. 200 even for REQUEST_APPROVAL creation authority: no ChangeRequest, invoice, tax, payable, attachment or capacity reservation. Human confirms official IDs/whole billed-service groups using existing invoice registration201/202 and separate authorized attachment/tax operations. No public URL; no-store. Blocking issues prohibit automatic use of prefill; this response is not fiscal authorization.")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="413",ref="#/components/responses/ProblemDetail")})
    public ResponseEntity<InvoiceXmlPreviewResponse> preview(@RequestParam UUID contractId,@RequestParam(required=false) UUID billingInstallmentId,
            @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file)throws java.io.IOException{
        if(file.getSize()>LIMIT)throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,ErrorCode.VALIDATION_FAILED,"XML exceeds the 10 MiB limit");
        String name=file.getOriginalFilename(),mime=file.getContentType();
        if(name==null||!name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")||!("application/xml".equals(mime)||"text/xml".equals(mime)))
            throw new IllegalArgumentException("XML filename and MIME must match");
        byte[] bytes;try(var input=file.getInputStream()){bytes=input.readNBytes(LIMIT+1);}
        if(bytes.length>LIMIT)throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,ErrorCode.VALIDATION_FAILED,"XML exceeds the 10 MiB limit");
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Pragma","no-cache")
                .body(InvoiceXmlPreviewResponse.from(preview.preview(contractId,billingInstallmentId,bytes)));
    }
}
