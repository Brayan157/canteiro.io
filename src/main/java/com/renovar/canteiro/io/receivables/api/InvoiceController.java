package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateRegisteredInvoiceRequest;
import com.renovar.canteiro.io.receivables.api.request.InvoiceItemRequest;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.response.InvoiceItemResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceRegistrationResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceResponse;
import com.renovar.canteiro.io.receivables.api.response.FinancialCancellationResponse;
import com.renovar.canteiro.io.receivables.application.CreateRegisteredInvoiceCommand;
import com.renovar.canteiro.io.receivables.application.InvoiceItemCommand;
import com.renovar.canteiro.io.receivables.application.InvoiceRegistrationResult;
import com.renovar.canteiro.io.receivables.application.InvoiceRegistrationService;
import com.renovar.canteiro.io.receivables.application.CancelFinancialRecordCommand;
import com.renovar.canteiro.io.receivables.application.FinancialCancellationService;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceRegistrationService invoiceRegistrationService;
    private final FinancialCancellationService financialCancellationService;

    @PostMapping("/{invoiceId}/cancellation")
    @Operation(summary = "Cancels an invoice, its items and tax effects atomically")
    public ResponseEntity<FinancialCancellationResponse> cancel(@PathVariable UUID invoiceId,
                                                                 @Valid @RequestBody CancelFinancialRecordRequest request) {
        var result = financialCancellationService.cancelInvoice(invoiceId,
                new CancelFinancialRecordCommand(request.reason(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new FinancialCancellationResponse(result.entityId(), result.reversalId(), result.status(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PostMapping
    @Operation(summary = "Registers an invoice for one contract using compatible billing allocations")
    public ResponseEntity<InvoiceRegistrationResponse> register(
            @Valid @RequestBody CreateRegisteredInvoiceRequest request
    ) {
        InvoiceRegistrationResult result = invoiceRegistrationService.register(new CreateRegisteredInvoiceCommand(
                request.contractId(), request.number(), request.series(), request.accessKey(), request.issuedOn(),
                request.items().stream().map(this::toCommand).toList(), request.justification()
        ));
        return ResponseEntity.status(result.invoice() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new InvoiceRegistrationResponse(
                        toResponse(result.invoice(), result.items()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    private InvoiceItemCommand toCommand(InvoiceItemRequest request) {
        return new InvoiceItemCommand(request.billingAllocationId(), request.description(), request.allocatedAmount());
    }

    private InvoiceResponse toResponse(Invoice invoice, List<InvoiceItem> items) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceResponse(
                invoice.getId(), invoice.getContractId(), invoice.getNumber(), invoice.getSeries(), invoice.getAccessKey(),
                invoice.getIssuedOn(), invoice.getTotalAmount(), invoice.getStatus(), items.stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(), item.getBillingAllocationId(), item.getContractServiceId(), item.getDescription(),
                        item.getAllocatedAmount(), item.getStatus()
                )).toList()
        );
    }
}
