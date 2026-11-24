package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadNetBillingCapacityUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final NetBillingCapacityService capacities;
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result get(UUID contractId, UUID serviceId, UUID originId, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.BILLING, AccessAction.READ);
        authorization.requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        var state = capacities.calculate(company, contractId);
        var positions = state.positions().stream().filter(p -> serviceId == null || serviceId.equals(p.contractServiceId()))
                .filter(p -> originId == null || p.origin() != null && originId.equals(p.origin().id())).toList();
        int start = (int) Math.min(pageable.getOffset(), positions.size());
        return new Result(state, new PageImpl<>(positions.subList(start, Math.min(start + pageable.getPageSize(), positions.size())), pageable, positions.size()));
    }
    public record Result(NetBillingCapacityService.State state, Page<NetBillingCapacityService.Position> positions) { }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ServiceNetBillingCapacity services(UUID contractId, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.BILLING, AccessAction.READ);
        authorization.requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        if (pageable.getOffset() > Integer.MAX_VALUE) throw new IllegalArgumentException("Page offset is too large");
        var state = capacities.calculate(company, contractId);
        var ids = state.positions().stream().map(NetBillingCapacityService.Position::contractServiceId).distinct().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        int start = (int) Math.min(pageable.getOffset(), ids.size());
        var values = ids.subList(start, Math.min(start + pageable.getPageSize(), ids.size())).stream()
                .map(id -> ServiceNetBillingCapacity.group(id, state.positions())).toList();
        return new ServiceNetBillingCapacity(state, new PageImpl<>(values, pageable, ids.size()));
    }
}
