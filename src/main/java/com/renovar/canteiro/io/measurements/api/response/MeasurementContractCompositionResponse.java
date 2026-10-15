package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record MeasurementContractCompositionResponse(
        UUID id, UUID measurementId, UUID measurementVersionId, UUID measurementServiceId,
        UUID contractId, UUID contractServiceId, MeasurementCompositionOriginType originType,
        @Schema(type = "string", format = "decimal", example = "1250.50") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "1200.50") String netAmount,
        @Schema(type = "string", format = "decimal", example = "1536.0000") String totalWeightKg,
        int itemCount, UUID changeRequestId, ChangeAuthorizationMode authorizationMode, boolean alreadyComposed,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
    public static MeasurementContractCompositionResponse direct(
            com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition composition,
            ChangeAuthorizationMode mode, boolean alreadyComposed, com.renovar.canteiro.io.shared.domain.CurrencyCode currency) {
        return new MeasurementContractCompositionResponse(composition.getId(), composition.getMeasurementId(),
                composition.getMeasurementVersionId(), composition.getMeasurementServiceId(), composition.getContractId(),
                composition.getContractServiceId(), composition.getOriginType(), money(composition.getGrossAmount()),
                money(composition.getNetAmount()), quantity(composition.getTotalWeightKg()), composition.getItemCount(),
                null, mode, alreadyComposed, currency);
    }
    public static MeasurementContractCompositionResponse requested(UUID changeRequestId, ChangeAuthorizationMode mode) {
        return new MeasurementContractCompositionResponse(null, null, null, null, null, null, null, null, null,
                null, 0, changeRequestId, mode, false, null);
    }
    private static String money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String quantity(BigDecimal value) { return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString(); }
}
