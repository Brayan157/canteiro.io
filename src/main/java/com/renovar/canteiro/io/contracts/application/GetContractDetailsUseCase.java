package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetContractDetailsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final ContractDiscountRepository discounts;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Details find(UUID id) {
        authorization.requirePermission(AccessModule.CONTRACTS, AccessAction.READ);
        UUID company = tenants.requireCurrentTenant().companyId();
        Contract contract = contracts.findByIdAndCompanyId(id, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        return new Details(contract, discounts.findActiveByContractIdAndCompanyId(id, company).isPresent());
    }

    public record Details(Contract contract, boolean hasActiveContractDiscount) { }
}
