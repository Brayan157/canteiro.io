package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.CalculatedTaxObligationService;
import com.renovar.canteiro.io.receivables.domain.TaxCalculation;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"invoiceId", "mode", "amount", "currency", "rounding", "recorded"}, description = "Official arithmetic, not tax advice. Preview has no tax ID and recorded=false. Historical legacy explicit-value tax has its ID but recorded=false because no new calculation intent was stored.")
public record InvoiceTaxCalculationResponse(UUID invoiceId,
        @Schema(types = {"string", "null"}, format = "uuid") UUID taxObligationId, TaxCalculation.Mode mode,
        @Schema(types = {"string", "null"}, format = "decimal", example = "1000.00") String baseAmount,
        @Schema(types = {"string", "null"}, format = "decimal", example = "2.1234") String ratePercent,
        @Schema(type = "string", format = "decimal", example = "21.23", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$") String amount,
        CurrencyCode currency, String rounding, boolean recorded) {
    public static InvoiceTaxCalculationResponse from(CalculatedTaxObligationService.Preview p) {
        return new InvoiceTaxCalculationResponse(p.invoiceId(), p.taxObligationId(), p.calculation().mode(),
                p.calculation().baseAmount() == null ? null : p.calculation().baseAmount().toPlainString(),
                p.calculation().ratePercent() == null ? null : p.calculation().ratePercent().toPlainString(),
                p.calculation().amount().toPlainString(), p.currency(), "HALF_UP_2", p.recorded());
    }
}
