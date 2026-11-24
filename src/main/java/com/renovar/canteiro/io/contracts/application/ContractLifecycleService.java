package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractLifecycleService {
    private final ContractServiceRepository services;
    private final ContractClosureFinancialProvider financial;

    public static boolean terminal(ContractStatus status) {
        return status == ContractStatus.COMPLETED || status == ContractStatus.FINALIZED || status == ContractStatus.CANCELLED;
    }
    public static boolean pending(ContractServiceStatus status) {
        return status == ContractServiceStatus.ACTIVE || status == ContractServiceStatus.DRAFT;
    }
    public static void requireOpen(Contract contract) {
        if (terminal(contract.getStatus())) reject("CONTRACT_REOPEN_REQUIRED");
    }
    public void requireContractStatus(Contract contract, ContractStatus proposed) {
        if (!terminal(proposed)) return;
        var all = services.findByContractIdAndCompanyId(contract.getId(), contract.getCompanyId());
        String reason = contractStructuralReason(proposed, all);
        if (reason != null) reject(reason);
        financial.lockInputs(contract.getCompanyId(), contract.getId());
        if ((proposed == ContractStatus.CANCELLED || all.stream().noneMatch(s -> s.getStatus() == ContractServiceStatus.COMPLETED))
                && financial.hasContractCommitments(contract.getCompanyId(), contract.getId())) reject("CONTRACT_HAS_FINANCIAL_COMMITMENTS");
        var positions = financial.positions(contract.getCompanyId(), contract.getId(), all);
        reason = contractFinancialReason(proposed, all, positions);
        if (reason != null) reject(reason);
    }
    public void requireServiceStatus(Contract contract, ContractService current, ContractService proposed) {
        if (current == null || pending(proposed.getStatus())) requireOpen(contract);
        if (proposed.getStatus() != ContractServiceStatus.COMPLETED && proposed.getStatus() != ContractServiceStatus.CANCELLED) return;
        if (current != null && current.getStatus() == ContractServiceStatus.CANCELLED && proposed.getStatus() == ContractServiceStatus.COMPLETED)
            reject("SERVICE_REOPEN_REQUIRED");
        var projected = new ArrayList<>(services.findByContractIdAndCompanyId(contract.getId(), contract.getCompanyId()));
        if (current == null) projected.add(proposed);
        else projected.replaceAll(s -> s.getId().equals(current.getId()) ? proposed : s);
        financial.lockInputs(contract.getCompanyId(), contract.getId());
        var position = financial.positions(contract.getCompanyId(), contract.getId(), projected).get(proposed.getId());
        if (position == null) reject("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED");
        String reason = proposed.getStatus() == ContractServiceStatus.COMPLETED ? position.completionReason() : position.cancellationReason();
        if (reason != null) reject(reason);
    }
    public static String contractStructuralReason(ContractStatus proposed, List<ContractService> all) {
        if (proposed == ContractStatus.CANCELLED)
            return all.stream().allMatch(s -> s.getStatus() == ContractServiceStatus.CANCELLED) ? null : "CONTRACT_SERVICES_NOT_CANCELLED";
        return all.stream().anyMatch(s -> pending(s.getStatus())) ? "CONTRACT_HAS_NON_TERMINAL_SERVICES" : null;
    }
    public static String contractFinancialReason(ContractStatus proposed, List<ContractService> all,
            Map<UUID, ContractClosureFinancialProvider.Position> positions) {
        boolean unsettled = false;
        for (var service : all) {
            var position = positions.get(service.getId());
            if (position == null) return "CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED";
            String reason = service.getStatus() == ContractServiceStatus.CANCELLED || proposed == ContractStatus.CANCELLED
                    ? position.cancellationReason() : position.completionReason();
            if ("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED".equals(reason)
                    || "SERVICE_CREDIT_REQUIRES_RECONCILIATION".equals(reason)) return "CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED";
            if (reason != null) unsettled = true;
        }
        return unsettled ? "CONTRACT_HAS_UNSETTLED_SERVICES" : null;
    }
    public static void reject(String reason) {
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                "The requested change is not currently eligible.", List.of(), reason);
    }
}
