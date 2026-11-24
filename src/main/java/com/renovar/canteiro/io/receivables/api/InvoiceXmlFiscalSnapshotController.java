package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.application.InvoiceXmlFiscalSnapshot;
import com.renovar.canteiro.io.receivables.application.PreserveInvoiceXmlFiscalSnapshotUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Invoices")
@RequestMapping(value = "/api/v1/company/invoices/{invoiceId}/xml-fiscal-snapshot",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class InvoiceXmlFiscalSnapshotController {
    private final PreserveInvoiceXmlFiscalSnapshotUseCase snapshots;

    @GetMapping
    @Operation(operationId = "getInvoiceXmlFiscalSnapshot", summary = "Reads confirmed XML fiscal facts",
            description = "Requires INVOICES.READ. The immutable snapshot is scoped to an invoice and its private XML attachment. No raw XML, storage path, party documents or access key. Cache-Control: no-store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<InvoiceXmlFiscalSnapshotResponse> get(@PathVariable UUID invoiceId) {
        return response(snapshots.read(invoiceId));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "confirmInvoiceXmlFiscalSnapshot", summary = "Confirms fiscal facts from an existing private XML attachment",
            description = "Requires INVOICES.READ and direct INVOICES.CREATE authority. Revalidates attachment integrity, invoice identity, parties, currency and commercial amount. Returns 201 for initial confirmation and 200 on replay with the same attachment. A different source or unresolved fiscal match returns 409. This does not register or approve an invoice, create tax obligations or change any financial balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing immutable evidence", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "201", description = "Evidence confirmed", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<InvoiceXmlFiscalSnapshotResponse> put(@PathVariable UUID invoiceId,
            @Valid @RequestBody ConfirmInvoiceXmlFiscalSnapshotRequest request) {
        return response(snapshots.preserve(invoiceId, request.attachmentId()));
    }

    private ResponseEntity<InvoiceXmlFiscalSnapshotResponse> response(PreserveInvoiceXmlFiscalSnapshotUseCase.View view) {
        var snapshot = view.snapshot();
        return ResponseEntity.status(view.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(new InvoiceXmlFiscalSnapshotResponse(snapshot.invoiceId(), snapshot.contractId(),
                        snapshot.attachmentId(), snapshot.sourceProfile(), snapshot.currency(),
                        view.invoice().getIssuedOn(), view.invoice().getSeries(), snapshot.facts(),
                        snapshot.confirmedBy(), snapshot.confirmedAt()));
    }

    public record ConfirmInvoiceXmlFiscalSnapshotRequest(@NotNull UUID attachmentId) { }

    public record InvoiceXmlFiscalSnapshotResponse(UUID invoiceId, UUID contractId, UUID attachmentId,
            String sourceProfile, String currency, LocalDate registeredIssuedOn, String registeredSeries,
            InvoiceXmlFiscalSnapshot.Facts facts, UUID confirmedBy, Instant confirmedAt) { }
}
