package com.renovar.canteiro.io.platform.subscription.api;

import com.renovar.canteiro.io.platform.subscription.api.request.GrantTrustUnlockRequest;
import com.renovar.canteiro.io.platform.subscription.api.response.PlatformChargeTrustUnlockResponse;
import com.renovar.canteiro.io.platform.subscription.api.response.TrustUnlockResponse;
import com.renovar.canteiro.io.platform.subscription.application.GrantTrustUnlockCommand;
import com.renovar.canteiro.io.platform.subscription.application.PlatformChargeTrustUnlockQueryService;
import com.renovar.canteiro.io.platform.subscription.application.TrustUnlockApplicationService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform/subscriptions")
@Tag(name = "Platform subscriptions")
public class PlatformSubscriptionController {

    private final TrustUnlockApplicationService trustUnlockApplicationService;
    private final PlatformChargeTrustUnlockQueryService platformChargeTrustUnlockQueryService;
    private final TrustUnlockApiMapper trustUnlockApiMapper;

    @GetMapping("/charges")
    @Operation(summary = "Lists overdue platform charges and their trust unlock availability")
    public PageResponse<PlatformChargeTrustUnlockResponse> findCharges(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(defaultValue = "true") boolean eligibleForTrustUnlock
    ) {
        return PageResponse.from(
                platformChargeTrustUnlockQueryService.findOverdueCharges(
                        new PageQuery(page, size, sort).toPageable(Set.of("dueDate")),
                        eligibleForTrustUnlock
                ),
                charge -> charge
        );
    }

    @PostMapping("/charges/{chargeId}/trust-unlocks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grants a temporary trust unlock for an overdue platform charge")
    public TrustUnlockResponse grantTrustUnlock(
            @PathVariable UUID chargeId,
            @Valid @RequestBody GrantTrustUnlockRequest request
    ) {
        return trustUnlockApiMapper.toResponse(trustUnlockApplicationService.grant(
                new GrantTrustUnlockCommand(chargeId, request.reason(), request.expiresAt())
        ));
    }
}
