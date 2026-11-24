package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetContractClosureEligibilityUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final ContractRepository contracts;
    private final ContractServiceRepository services;
    private final ContractClosureFinancialProvider financial;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result get(UUID contractId, UUID serviceId, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        auth.requirePermission(AccessModule.CONTRACTS, AccessAction.READ);
        auth.requirePermission(AccessModule.SERVICES, AccessAction.READ);
        if (pageable.getOffset() > Integer.MAX_VALUE) throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        var contract = contracts.findByIdAndCompanyId(contractId, company).orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        var all = services.findByContractIdAndCompanyId(contractId, company);
        if (serviceId != null && all.stream().noneMatch(s -> s.getId().equals(serviceId))) throw new TenantResourceNotFoundException("Contract service");
        boolean canRead = auth.hasPermission(AccessModule.BILLING, AccessAction.READ) && auth.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ)
                && auth.hasPermission(AccessModule.INVOICES, AccessAction.READ);
        Map<UUID, ContractClosureFinancialProvider.Position> positions = canRead ? financial.positions(company, contractId, all) : Map.of();
        var contractUpdate = auth.changeAuthorizationIfAllowed(AccessModule.CONTRACTS, ChangeOperation.UPDATE).orElse(null);
        var contractCancel = auth.changeAuthorizationIfAllowed(AccessModule.CONTRACTS, ChangeOperation.CANCEL).orElse(null);
        var serviceUpdate = auth.changeAuthorizationIfAllowed(AccessModule.SERVICES, ChangeOperation.UPDATE).orElse(null);
        var serviceCancel = auth.changeAuthorizationIfAllowed(AccessModule.SERVICES, ChangeOperation.CANCEL).orElse(null);
        String completeReason = ContractLifecycleService.contractStructuralReason(ContractStatus.COMPLETED, all);
        String cancelReason = ContractLifecycleService.contractStructuralReason(ContractStatus.CANCELLED, all);
        boolean hasCommitments = canRead && financial.hasContractCommitments(company, contractId);
        if (completeReason == null && canRead) {
            completeReason = all.stream().noneMatch(s -> s.getStatus() == ContractServiceStatus.COMPLETED) && hasCommitments
                    ? "CONTRACT_HAS_FINANCIAL_COMMITMENTS" : ContractLifecycleService.contractFinancialReason(ContractStatus.COMPLETED, all, positions);
        }
        if (cancelReason == null && canRead) cancelReason = hasCommitments ? "CONTRACT_HAS_FINANCIAL_COMMITMENTS"
                : ContractLifecycleService.contractFinancialReason(ContractStatus.CANCELLED, all, positions);
        if (contract.getStatus() == ContractStatus.COMPLETED || contract.getStatus() == ContractStatus.FINALIZED) completeReason = "CONTRACT_ALREADY_COMPLETED";
        if (contract.getStatus() == ContractStatus.CANCELLED) { completeReason = "CONTRACT_REOPEN_REQUIRED"; cancelReason = "CONTRACT_ALREADY_CANCELLED"; }
        var filtered = all.stream().filter(s -> serviceId == null || s.getId().equals(serviceId))
                .sorted(Comparator.comparing(ContractService::getName).thenComparing(ContractService::getId)).toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size()), end = Math.min(start + pageable.getPageSize(), filtered.size());
        var page = new PageImpl<>(filtered.subList(start, end).stream().map(s -> {
            var position = positions.get(s.getId());
            String reason = position == null ? null : position.completionReason();
            String cancellation = position == null ? null : position.cancellationReason();
            if (s.getStatus() == ContractServiceStatus.CANCELLED) { reason = "SERVICE_REOPEN_REQUIRED"; cancellation = "SERVICE_ALREADY_CANCELLED"; }
            if (s.getStatus() == ContractServiceStatus.COMPLETED && reason == null) reason = "SERVICE_ALREADY_COMPLETED";
            return new ServiceResult(s.getId(), s.getName(), s.getStatus(), decision(serviceUpdate, canRead, reason), decision(serviceCancel, canRead, cancellation));
        }).toList(), pageable, filtered.size());
        return new Result(contractId, contract.getStatus(), all.size(), all.stream().filter(s -> ContractLifecycleService.pending(s.getStatus())).count(),
                decision(contractUpdate, canRead, completeReason), decision(contractCancel, canRead, cancelReason), page);
    }
    private Decision decision(ChangeAuthorizationMode mode, boolean canRead, String reason) {
        if (mode == null) return new Decision(false, "CLOSURE_NOT_AUTHORIZED", null);
        if (reason != null) return new Decision(false, reason, mode);
        return canRead ? new Decision(true, null, mode) : new Decision(null, "CLOSURE_FINANCIAL_READ_REQUIRED", mode);
    }
    public record Decision(Boolean eligible, String reason, ChangeAuthorizationMode authorizationMode) { }
    public record ServiceResult(UUID id, String name, ContractServiceStatus status, Decision completion, Decision cancellation) { }
    public record Result(UUID contractId, ContractStatus status, long serviceCount, long nonTerminalServiceCount,
            Decision completion, Decision cancellation, Page<ServiceResult> services) { }
}
