package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.ReadNetBillingCapacityUseCase;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"contractId", "currency", "capacityVersion", "contractNetAmount", "activeAllocatedAmount", "remainingNetAmount", "remainingDiscountAmount", "positions"})
public record NetBillingCapacityResponse(UUID contractId, CurrencyCode currency, String capacityVersion,
        @Schema(type = "string", format = "decimal", example = "1800.00") String contractNetAmount,
        @Schema(type = "string", format = "decimal", example = "900.00") String activeAllocatedAmount,
        @Schema(type = "string", format = "decimal", example = "900.00", description = "Global uncommitted net ceiling; blocked origins remain in distribution but cannot be selected.") String remainingNetAmount,
        @Schema(type = "string", format = "decimal", example = "100.00") String remainingDiscountAmount,
        PageResponse<PositionResponse> positions) {
    public static NetBillingCapacityResponse from(ReadNetBillingCapacityUseCase.Result result) {
        var state = result.state();
        return new NetBillingCapacityResponse(state.contractId(), state.currency(), state.capacityVersion(), state.contractNetAmount().toPlainString(),
                state.activeAllocatedAmount().toPlainString(), state.remainingNetAmount().toPlainString(), state.remainingDiscountAmount().toPlainString(),
                PageResponse.from(result.positions(), p -> new PositionResponse(p.contractServiceId(), p.serviceName(),
                        p.origin() == null ? null : p.origin().id(), p.origin() == null ? null : p.origin().measurementId(),
                        p.origin() == null ? null : p.origin().measurementVersionId(), p.origin() == null ? null : p.origin().continuousServiceId(),
                        p.grossAvailableAmount().toPlainString(), p.netAvailableAmount().toPlainString(), p.uncommittedDiscountAmount().toPlainString(),
                        p.eligible() && p.netAvailableAmount().signum() > 0, p.netAvailableAmount().signum() == 0 && p.eligible() ? "NO_NET_CAPACITY" : p.reason())));
    }
    @Schema(name = "NetBillingCapacityPositionResponse", requiredProperties = {"contractServiceId", "serviceName", "grossAvailableAmount", "netAvailableAmount", "uncommittedDiscountAmount", "eligible", "reason"})
    public record PositionResponse(UUID contractServiceId, String serviceName,
            @Schema(types = {"string", "null"}, format = "uuid") UUID measurementContractCompositionId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID measurementId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID measurementVersionId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID continuousServiceId,
            @Schema(type = "string", format = "decimal", example = "1000.00") String grossAvailableAmount,
            @Schema(type = "string", format = "decimal", example = "900.00") String netAvailableAmount,
            @Schema(type = "string", format = "decimal", example = "100.00") String uncommittedDiscountAmount,
            boolean eligible, String reason) { }
}
