package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Provides the governance-only view of pending proposals.
 *
 * <p>Change requests are not operational records. They remain in this separate view until their
 * proposal is applied by the owning use case after an approval.</p>
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestQueryService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestPresentationService changeRequestPresentationService;
    private final WorkforceRepository workforce;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ChangeRequest> findPending(Pageable pageable) {
        requireReadPermission();
        return restrictPage(changeRequestRepository.findByCompanyIdAndStatus(
                tenantContextHolder.requireCurrentTenant().companyId(), ChangeRequestStatus.PENDING, Pageable.unpaged()
        ).getContent().stream().filter(this::visibleInWork).toList(), pageable);
    }

    /**
     * Filters only pending requests from the trusted current tenant. The presentation is assembled
     * before searching, so search never reads raw proposal fields such as documents or payment data.
     */
    @Transactional(readOnly = true)
    public Page<ChangeRequestListItem> findPending(ChangeRequestFilter filter, Pageable pageable) {
        requireReadPermission();
        var tenant = tenantContextHolder.requireCurrentTenant();
        List<ChangeRequestListItem> filtered = changeRequestRepository.findByCompanyIdAndStatus(
                        tenant.companyId(), ChangeRequestStatus.PENDING, Pageable.unpaged()
                ).getContent().stream()
                .filter(this::visibleInWork)
                .filter(request -> matchesFilter(request, filter))
                .map(changeRequestPresentationService::present)
                .filter(item -> changeRequestPresentationService.matchesSearch(item, filter.search()))
                .sorted(toComparator(pageable.getSort()))
                .toList();

        return restrictPage(filtered, pageable);
    }

    private boolean visibleInWork(ChangeRequest request) {
        if (request.getModule() != AuditModule.WORK_DIARY) {
            return true;
        }
        var tenant = tenantContextHolder.requireCurrentTenant();
        UUID workId = UUID.fromString((String) request.getSnapshot().proposedData().values().get("workId"));
        return accessAuthorizationService.hasPermission(AccessModule.WORK_DIARY, AccessAction.READ)
                && workforce.hasGrant(tenant.companyId(), workId, tenant.userId(), WorkOperation.READ, clock.instant());
    }

    private static <T> Page<T> restrictPage(List<T> values, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(values);
        }
        int start = Math.toIntExact(Math.min(pageable.getOffset(), values.size()));
        int end = Math.min(start + pageable.getPageSize(), values.size());
        return new PageImpl<>(values.subList(start, end), pageable, values.size());
    }

    private boolean matchesFilter(ChangeRequest request, ChangeRequestFilter filter) {
        return (filter.module() == null || filter.module() == request.getModule())
                && (filter.operation() == null || filter.operation() == request.getOperation())
                && (filter.requesterUserId() == null || filter.requesterUserId().equals(request.getRequesterUserId()))
                && isOnOrAfter(request.getCreatedAt(), filter.createdFrom())
                && isOnOrBefore(request.getCreatedAt(), filter.createdTo());
    }

    private Comparator<ChangeRequestListItem> toComparator(Sort sort) {
        Comparator<ChangeRequestListItem> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<ChangeRequestListItem> next = switch (order.getProperty()) {
                case "createdAt" -> Comparator.comparing(
                        item -> item.changeRequest().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "updatedAt" -> Comparator.comparing(
                        item -> item.changeRequest().getUpdatedAt(), Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "module" -> Comparator.comparing(item -> item.changeRequest().getModule().name());
                case "operation" -> Comparator.comparing(item -> item.changeRequest().getOperation().name());
                default -> throw new IllegalArgumentException("Unsupported change request sort field");
            };
            Comparator<ChangeRequestListItem> ordered = order.isAscending() ? next : next.reversed();
            comparator = comparator == null ? ordered : comparator.thenComparing(ordered);
        }
        if (comparator == null) {
            comparator = Comparator.comparing(
                    item -> item.changeRequest().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())
            );
        }
        return comparator.thenComparing(item -> item.changeRequest().getId());
    }

    private boolean isOnOrAfter(java.time.Instant createdAt, LocalDate createdFrom) {
        return createdFrom == null || !createdAt.isBefore(createdFrom.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }

    private boolean isOnOrBefore(java.time.Instant createdAt, LocalDate createdTo) {
        return createdTo == null || createdAt.isBefore(createdTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }

    private void requireReadPermission() {
        accessAuthorizationService.requirePermission(AccessModule.AUDIT, AccessAction.READ);
    }
}
