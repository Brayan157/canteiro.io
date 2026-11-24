package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.measurements.domain.BillableMeasurementOrigin;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindBillableMeasurementOriginsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final MeasurementContractCompositionRepository compositions;
    private final NetBillingCapacityService capacities;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result find(UUID contractId, UUID measurementId, UUID contractServiceId, Pageable pageable) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.BILLING, AccessAction.READ);
        authorization.requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        var contract = contracts.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        return new Result(compositions.findBillableOrigins(companyId, contractId, measurementId, contractServiceId, pageable),
                contract.getCurrency(), capacities.calculate(companyId, contractId));
    }

    public record Result(Page<BillableMeasurementOrigin> origins, CurrencyCode currency, NetBillingCapacityService.State capacity) {
    }
}
