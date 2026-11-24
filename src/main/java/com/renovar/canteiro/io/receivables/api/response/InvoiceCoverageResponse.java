package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.ReadInvoiceCoverageUseCase;
import com.renovar.canteiro.io.receivables.domain.InvoiceServiceCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InvoiceCoverageResponse(UUID billingInstallmentId,UUID contractId,String currency,boolean available,
        @Schema(types={"string","null"}) String unavailableReason,
        @Schema(types={"string","null"},format="decimal",example="200.00") String amount,
        @Schema(types={"string","null"},format="decimal",example="100.00") String documentedAmount,
        @Schema(types={"string","null"},format="decimal",example="100.00") String remainingToDocumentAmount,
        List<GroupResponse> services,java.time.Instant calculatedAt){
    @Schema(name="InvoiceCoverageOriginResponse")
    public record OriginResponse(UUID billingAllocationId,@Schema(types={"string","null"},format="uuid") UUID measurementContractCompositionId,
            @Schema(type="string",format="decimal",example="100.00") String amount,
            @Schema(types={"string","null"},format="decimal") String documentedAmount) { }
    @Schema(name="InvoiceCoverageServiceResponse")
    public record GroupResponse(UUID billingServiceGroupId,UUID contractServiceId,String serviceName,InvoiceServiceCoverage.State state,
            boolean selectable,@Schema(types={"string","null"}) String blockingReason,
            @Schema(type="string",format="decimal",example="100.00") String amount,
            @Schema(types={"string","null"},format="decimal") String documentedAmount,
            @Schema(types={"string","null"},format="decimal") String availableToDocumentAmount,List<OriginResponse> origins) { }
    public static InvoiceCoverageResponse from(ReadInvoiceCoverageUseCase.View view){
        return new InvoiceCoverageResponse(view.billingInstallmentId(),view.contractId(),view.currency(),view.available(),
                view.available()?null:"INVOICE_COVERAGE_REQUIRES_REVIEW",decimal(view.amount()),decimal(view.documentedAmount()),decimal(view.remainingToDocumentAmount()),view.groups().stream().map(g->new GroupResponse(g.id(),g.contractServiceId(),
                view.serviceNames().getOrDefault(g.contractServiceId(),"Serviço histórico indisponível"),g.state(),view.available()&&g.selectable(),
                view.available()?g.blockingReason():"INVOICE_SERVICE_GROUP_REVIEW_REQUIRED",decimal(g.amount()),decimal(g.documentedAmount()),
                decimal(g.availableToDocumentAmount()),g.origins().stream()
                .map(o->new OriginResponse(o.billingAllocationId(),o.measurementContractCompositionId(),decimal(o.amount()),decimal(o.documentedAmount()))).toList())).toList(),view.calculatedAt());
    }
    private static String decimal(BigDecimal value){return value==null?null:value.setScale(2,java.math.RoundingMode.UNNECESSARY).toPlainString();}
}
