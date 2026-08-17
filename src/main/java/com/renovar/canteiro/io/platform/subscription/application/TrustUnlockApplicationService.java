package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrustUnlockApplicationService {

    private static final long MAX_UNLOCKS_PER_CHARGE = 2;

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    private final PlatformChargeRepository platformChargeRepository;
    private final TrustUnlockRepository trustUnlockRepository;
    private final SubscriptionDunningCompanyService subscriptionDunningCompanyService;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional
    public TrustUnlock grant(GrantTrustUnlockCommand command) {
        PlatformOperatorContext operator = requireOwner();
        if (command == null || command.chargeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "A platform charge is required");
        }
        PlatformCharge initialCharge = platformChargeRepository.findById(command.chargeId())
                .orElseThrow(() -> notFound("Platform charge was not found"));
        companySubscriptionAccessRepository.lockCompanyId(initialCharge.getCompanyId());
        PlatformCharge charge = platformChargeRepository.findByIdForUpdate(command.chargeId())
                .orElseThrow(() -> notFound("Platform charge was not found"));
        Instant now = clock.instant();
        requireOverdueCharge(charge);
        requireFutureExpiration(command.expiresAt(), now);
        if (trustUnlockRepository.countByChargeId(charge.getId()) >= MAX_UNLOCKS_PER_CHARGE) {
            throw conflict("A platform charge can receive at most two trust unlocks");
        }

        TrustUnlock trustUnlock = TrustUnlock.create(
                charge.getCompanyId(), charge.getId(), operator.userId(), command.reason(), now, command.expiresAt()
        );
        TrustUnlock savedTrustUnlock = trustUnlockRepository.save(trustUnlock);
        auditEventRecorder.recordPlatformAction(
                charge.getCompanyId(),
                AuditModule.PLATFORM,
                AuditAction.CREATE,
                "TrustUnlock",
                savedTrustUnlock.getId(),
                null,
                auditData(savedTrustUnlock),
                Map.of("origin", "platform-trust-unlock")
        );
        subscriptionDunningCompanyService.reevaluateCompany(charge.getCompanyId());
        return savedTrustUnlock;
    }

    private PlatformOperatorContext requireOwner() {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform owner is required"));
        if (operator.globalRole() != PlatformUserRole.PLATFORM_OWNER) {
            throw new AccessDeniedException("A platform owner is required");
        }
        return operator;
    }

    private void requireOverdueCharge(PlatformCharge charge) {
        if (!charge.isOverdueOn(LocalDate.now(clock))) {
            throw conflict("Trust unlocks can only be granted for overdue platform charges");
        }
    }

    private void requireFutureExpiration(Instant expiresAt, Instant now) {
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Trust unlock expiration must be in the future"
            );
        }
    }

    private ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, detail);
    }

    private ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, detail);
    }

    private Map<String, Object> auditData(TrustUnlock trustUnlock) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chargeId", trustUnlock.getChargeId());
        data.put("reason", trustUnlock.getReason());
        data.put("startsAt", trustUnlock.getStartsAt().toString());
        data.put("expiresAt", trustUnlock.getExpiresAt().toString());
        data.put("grantedByUserId", trustUnlock.getGrantedByUserId());
        return data;
    }
}
