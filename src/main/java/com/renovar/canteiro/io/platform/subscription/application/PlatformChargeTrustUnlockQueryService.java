package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.api.response.PlatformChargeTrustUnlockResponse;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformChargeTrustUnlockQueryService {

    public static final int TRUST_UNLOCK_LIMIT = 2;

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final PlatformChargeRepository platformChargeRepository;
    private final TrustUnlockRepository trustUnlockRepository;
    private final CompanyRepository companyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<PlatformChargeTrustUnlockResponse> findOverdueCharges(Pageable pageable, boolean eligibleOnly) {
        requireOwner();
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        List<PlatformChargeTrustUnlockResponse> charges = platformChargeRepository.findAllOutstanding().stream()
                .filter(charge -> charge.isOverdueOn(today))
                .map(charge -> toResponse(charge, now))
                .filter(charge -> !eligibleOnly || charge.remainingTrustUnlocks() > 0)
                .sorted(chargeComparator(pageable))
                .toList();

        int first = Math.min(Math.toIntExact(pageable.getOffset()), charges.size());
        int last = Math.min(first + pageable.getPageSize(), charges.size());
        return new PageImpl<>(charges.subList(first, last), pageable, charges.size());
    }

    private PlatformChargeTrustUnlockResponse toResponse(PlatformCharge charge, Instant now) {
        long used = trustUnlockRepository.countByChargeId(charge.getId());
        Company company = companyRepository.findById(charge.getCompanyId()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Charge company was not found")
        );
        TrustUnlock activeUnlock = trustUnlockRepository.findByChargeId(charge.getId()).stream()
                .filter(unlock -> unlock.isActiveAt(now))
                .max(Comparator.comparing(TrustUnlock::getExpiresAt))
                .orElse(null);

        return new PlatformChargeTrustUnlockResponse(
                charge.getId(),
                new PlatformChargeTrustUnlockResponse.CompanyResponse(
                        company.getId(), company.getCorporateName(), company.getTradeName()
                ),
                charge.getAmount().toPlainString(),
                charge.getDueDate(),
                charge.getStatus(),
                used,
                TRUST_UNLOCK_LIMIT,
                Math.max(0, TRUST_UNLOCK_LIMIT - used),
                activeUnlock == null ? null
                        : new PlatformChargeTrustUnlockResponse.ActiveTrustUnlockResponse(activeUnlock.getExpiresAt())
        );
    }

    private Comparator<PlatformChargeTrustUnlockResponse> chargeComparator(Pageable pageable) {
        Comparator<PlatformChargeTrustUnlockResponse> comparator = Comparator
                .comparing(PlatformChargeTrustUnlockResponse::dueDate)
                .thenComparing(PlatformChargeTrustUnlockResponse::id);
        Sort.Order dueDateOrder = pageable.getSort().getOrderFor("dueDate");
        return dueDateOrder != null && dueDateOrder.isDescending() ? comparator.reversed() : comparator;
    }

    private void requireOwner() {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform owner is required"));
        if (operator.globalRole() != PlatformUserRole.PLATFORM_OWNER) {
            throw new AccessDeniedException("A platform owner is required");
        }
    }
}
