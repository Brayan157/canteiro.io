package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayablePaymentInstructionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/company/payables/{payableId}/payment-instructions")
@Tag(name="Payable payment instructions")
@ApiResponses({@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class PayablePaymentInstructionController {
    private final PayablePaymentInstructionService service;
    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Reads payment instruction metadata",description="PAYABLES.READ; tenant only. Existing payable without instructions returns 200 exists=false/version=0. Never contains payment secrets.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public ResponseEntity<PayablePaymentInstructionResponse> read(@PathVariable UUID payableId){return noStore(PayablePaymentInstructionResponse.from(service.read(payableId)));}

    @PutMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Replaces instructions for manual payment, without recording a payment",description="Requires PAYABLES.READ, VIEW_PAYMENT_DETAILS and UPDATE authority. Append-only revisions, optimistic expectedVersion under title lock. Full replacement: omitted/null fields clear old values; prior PDF is not carried across replacements. Reveal current state explicitly before editing. BOLETO accepts no code for subsequent PDF-only attachment. Open/overdue/partially paid titles only. 202 changes nothing official; no cash/status/amount changes.")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<PayablePaymentInstructionChangeResponse> update(@PathVariable UUID payableId,@Valid @RequestBody PayablePaymentInstructionUpdateRequest r){
        return result(service.replace(payableId,r.expectedVersion(),r.details().content(),r.justification()));
    }
    @PostMapping(value="/reveal",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Explicitly reveals official or pending payment instructions",description="PAYABLES.READ + PAYABLES.VIEW_PAYMENT_DETAILS. Optional changeRequestId must be a pending instruction proposal for this title/tenant; only requester or PAYABLES.APPROVE reader may inspect it. Audited without secrets; Cache-Control no-store. Missing official instructions or foreign proposal returns 404. No bank/beneficiary validation.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public ResponseEntity<PayablePaymentInstructionRevealResponse> reveal(@PathVariable UUID payableId,@RequestParam(required=false) UUID changeRequestId){
        return noStore(PayablePaymentInstructionRevealResponse.from(service.reveal(payableId,changeRequestId)));
    }
    @PostMapping(value="/boleto",consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Attaches a private boleto PDF to official BOLETO instructions",description="Requires READ + VIEW_PAYMENT_DETAILS + UPDATE authority. Save title and official BOLETO instruction first. Multipart file + instruction expectedVersion, optional justification<=1000. PDF 1 byte..10 MiB, .pdf/application/pdf, readable, unencrypted, at least one page; structural validation is not bank authenticity or malware scanning. Encrypted private storage. New immutable revision; 202 changes no official data and remains private to the pending proposal. No financial effect.")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<PayablePaymentInstructionChangeResponse> upload(@PathVariable UUID payableId,
            @Parameter(schema=@Schema(type="string",format="binary")) @RequestParam MultipartFile file,
            @RequestParam long expectedVersion,@RequestParam(required=false) String justification)throws IOException{
        if(file.getSize()>10L*1024*1024)throw new IllegalArgumentException("PDF exceeds 10 MB");
        return result(service.uploadPdf(payableId,expectedVersion,file.getOriginalFilename(),file.getContentType(),file.getBytes(),justification));
    }
    @GetMapping(value="/boleto",produces=MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary="Downloads the private boleto PDF",description="PAYABLES.READ + VIEW_PAYMENT_DETAILS. Authenticated stream, audit VIEW_SENSITIVE, no-store, no public URL. Optional changeRequestId has the same requester/approver gates as reveal; no official PDF returns 404. Fixed safe filename boleto.pdf.")
    @ApiResponse(responseCode="200",content=@Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary")))
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID payableId,@RequestParam(required=false) UUID changeRequestId){
        var d=service.download(payableId,changeRequestId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache")
                .header("Content-Disposition","attachment; filename=\"boleto.pdf\"").header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.APPLICATION_PDF).contentLength(d.size()).body(new InputStreamResource(d.stream()));
    }
    private ResponseEntity<PayablePaymentInstructionChangeResponse> result(PayablePaymentInstructionService.Result r){
        return ResponseEntity.status(r.changeRequestId()==null?200:202).cacheControl(CacheControl.noStore()).body(PayablePaymentInstructionChangeResponse.from(r));
    }
    private <T> ResponseEntity<T> noStore(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(value);}
}
