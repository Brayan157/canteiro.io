package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.application.GetContractClosureEligibilityUseCase;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Read-only lifecycle decisions. Whole-contract summary precedes filtering/paging. CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED distinguishes unproven origin/credit history from CONTRACT_HAS_UNSETTLED_SERVICES; reconciliation takes precedence across services and does not mean missing cash. No financial amounts or receipt identifiers. A decision is not a reservation; PUT and approval revalidate.")
public record ContractClosureEligibilityResponse(UUID contractId, ContractStatus status, long serviceCount, long nonTerminalServiceCount,
        ClosureDecisionResponse completion, ClosureDecisionResponse cancellation, PageResponse<ServiceClosureEligibilityResponse> services) {
    public static ContractClosureEligibilityResponse from(GetContractClosureEligibilityUseCase.Result value) {
        return new ContractClosureEligibilityResponse(value.contractId(), value.status(), value.serviceCount(), value.nonTerminalServiceCount(),
                ClosureDecisionResponse.from(value.completion()), ClosureDecisionResponse.from(value.cancellation()),
                PageResponse.from(value.services(), s -> new ServiceClosureEligibilityResponse(s.id(), s.name(), s.status(),
                        ClosureDecisionResponse.from(s.completion()), ClosureDecisionResponse.from(s.cancellation()))));
    }
    @Schema(name = "ClosureDecisionResponse", requiredProperties = {"eligible", "reason", "authorizationMode"})
    public record ClosureDecisionResponse(
            @Schema(types = {"boolean", "null"}, description = "Null when financial READ permissions are missing; false is a known block, true permits requesting the indicated authorization mode.") Boolean eligible,
            @Schema(types = {"string", "null"}, description = "Controlled lifecycle or permission reason; null only when eligible. For a COMPLETED service, an authorized financial blocking reason takes precedence over SERVICE_ALREADY_COMPLETED; that historical-status reason is only a fallback when no financial block is available. No financial reason is resolved without the required READ permissions.") String reason,
            @Schema(types = {"string", "null"}, description = "DIRECT or REQUEST_APPROVAL; null when action authorization is missing.") ChangeAuthorizationMode authorizationMode) {
        static ClosureDecisionResponse from(GetContractClosureEligibilityUseCase.Decision value) {
            return new ClosureDecisionResponse(value.eligible(), value.reason(), value.authorizationMode());
        }
    }
    @Schema(name = "ServiceClosureEligibilityResponse")
    public record ServiceClosureEligibilityResponse(UUID id, String name, ContractServiceStatus status, ClosureDecisionResponse completion, ClosureDecisionResponse cancellation) { }
}
