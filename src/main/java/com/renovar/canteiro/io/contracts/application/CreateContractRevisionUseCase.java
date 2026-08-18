package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractRevision;
import com.renovar.canteiro.io.contracts.domain.ContractRevisionRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateContractRevisionUseCase {

    private final TenantContextHolder tenantContextHolder;
    private final CalculateContractNetAmountUseCase calculateContractNetAmountUseCase;
    private final ApprovedContractBillingAmountProvider approvedContractBillingAmountProvider;
    private final ContractRevisionRepository contractRevisionRepository;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public ContractRevision create(UUID contractId, BigDecimal proposedNetAmount, String reason) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ContractNetAmount currentAmount = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        BigDecimal approvedBilledAmount = approvedContractBillingAmountProvider.approvedNetAmount(companyId, contractId);
        ContractRevision revision = ContractRevision.create(
                companyId,
                contractId,
                contractRevisionRepository.nextRevisionNumber(contractId, companyId),
                currentAmount.netAmount(),
                proposedNetAmount,
                approvedBilledAmount,
                reason
        );
        ContractRevision savedRevision = contractRevisionRepository.save(revision);
        auditEventRecorder.recordDirectAction(
                AuditModule.CONTRACTS,
                AuditAction.UPDATE,
                "ContractRevision",
                savedRevision.getId(),
                Map.of("netAmount", savedRevision.getPreviousNetAmount()),
                Map.of("netAmount", savedRevision.getProposedNetAmount()),
                Map.of(
                        "contractId", contractId.toString(),
                        "approvedBilledAmount", savedRevision.getApprovedBilledAmount()
                )
        );
        return savedRevision;
    }
}
