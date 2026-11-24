package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.NetBillingCapacityService;
import com.renovar.canteiro.io.receivables.application.ResolveServiceNetBilling;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"contractId", "currency", "capacityVersion", "coverageComplete", "totalServices", "services"}, description = "Complete origin coverage per service, calculated before pagination. Page may contain only some services; never sum pages to compute capacity. All currently usable sources are covered, blocked capacity is separate.")
public record ServiceNetBillingCapacityResponse(UUID contractId, CurrencyCode currency, String capacityVersion,
        boolean coverageComplete, int totalServices, PageResponse<ServiceCapacity> services) {
    @Schema(name = "ServiceNetBillingCapacityItemResponse", requiredProperties = {"contractServiceId", "serviceName", "originCount", "usableOriginCount", "coverageComplete", "availableNetAmount", "baseNetAvailableAmount", "measurementNetAvailableAmount", "blockedNetAmount"})
    public record ServiceCapacity(UUID contractServiceId, String serviceName, int originCount, int usableOriginCount,
            boolean coverageComplete, @Schema(type = "string", format = "decimal", example = "100.00") String availableNetAmount,
            @Schema(type = "string", format = "decimal", example = "60.00") String baseNetAvailableAmount,
            @Schema(type = "string", format = "decimal", example = "40.00") String measurementNetAvailableAmount,
            @Schema(type = "string", format = "decimal", example = "0.00") String blockedNetAmount) { }
    public static ServiceNetBillingCapacityResponse from(com.renovar.canteiro.io.receivables.application.ServiceNetBillingCapacity value) {
        var state = value.state();
        return new ServiceNetBillingCapacityResponse(state.contractId(), state.currency(), state.capacityVersion(), true, Math.toIntExact(value.services().getTotalElements()),
                PageResponse.from(value.services(), p -> new ServiceCapacity(p.contractServiceId(), p.serviceName(), p.originCount(), p.usableOriginCount(), true,
                        p.availableNetAmount().toPlainString(), p.baseNetAvailableAmount().toPlainString(), p.measurementNetAvailableAmount().toPlainString(), p.blockedNetAmount().toPlainString())));
    }
}
