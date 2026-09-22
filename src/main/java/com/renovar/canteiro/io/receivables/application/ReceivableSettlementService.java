package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceivableSettlementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ReceivableRepository receivableRepository;
    private final ReceivableSettlementRepository receivableSettlementRepository;
    private final CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional
    public ReceivableSettlementResult settle(CreateReceivableSettlementCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validate(command, companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.RECEIVABLES, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.RECEIVABLES,
                    ChangeRequestOperation.CREATE,
                    "ReceivableSettlement",
                    command.receivableId(),
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new ReceivableSettlementResult(null, null, null, changeRequest, mode);
        }
        Settlement settlement = persist(companyId, command);
        record(settlement, "direct", null);
        return new ReceivableSettlementResult(
                settlement.receivableSettlement(), settlement.balance(), settlement.updatedReceivable().getStatus(), null, mode
        );
    }

    @Transactional
    public void applyApprovedSettlement(ChangeRequest changeRequest) {
        CreateReceivableSettlementCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        validate(command, changeRequest.getCompanyId());
        Settlement settlement = persist(changeRequest.getCompanyId(), command);
        record(settlement, "approved-change-request", changeRequest.getId());
    }

    private Settlement persist(UUID companyId, CreateReceivableSettlementCommand command) {
        Receivable receivable = receivableRepository.findByIdAndCompanyIdWithSettlementLock(command.receivableId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        validateReceivableStatus(receivable);
        ReceivableBalance currentBalance = calculateReceivableBalanceUseCase.calculate(companyId, command.receivableId());
        if (!currentBalance.canSettle(command.amount())) {
            throw new IllegalStateException("Receivable settlement exceeds the outstanding amount");
        }
        ReceivableSettlement receivableSettlement = receivableSettlementRepository.save(ReceivableSettlement.confirm(
                companyId, receivable.getId(), receivable.getContractId(), command.receivedOn(), command.amount(),
                command.paymentMethod(), command.note()
        ));
        ReceivableBalance updatedBalance = currentBalance.afterSettlement(command.amount());
        ReceivableStatus updatedStatus = updatedBalance.statusOn(receivable, LocalDate.now(clock));
        Receivable updatedReceivable = receivableRepository.updateStatus(receivable.withStatus(updatedStatus));
        return new Settlement(receivableSettlement, updatedBalance, updatedReceivable, receivable.getStatus());
    }

    private void validate(CreateReceivableSettlementCommand command, UUID companyId) {
        if (command == null || command.receivableId() == null || command.receivedOn() == null || command.amount() == null
                || command.paymentMethod() == null) {
            throw new IllegalArgumentException("Receivable, received date, amount and payment method are required");
        }
        Receivable receivable = receivableRepository.findByIdAndCompanyId(command.receivableId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        if (receivable.getIssuedOn() != null && command.receivedOn().isBefore(receivable.getIssuedOn())) {
            throw new IllegalArgumentException("Receivable settlement date must not precede its issue date");
        }
        if (command.amount().signum() <= 0) {
            throw new IllegalArgumentException("Receivable settlement amount must be positive");
        }
        validateReceivableStatus(receivable);
    }

    private void validateReceivableStatus(Receivable receivable) {
        if (receivable.getStatus() == ReceivableStatus.CANCELLED || receivable.getStatus() == ReceivableStatus.RENEGOTIATED
                || receivable.getStatus() == ReceivableStatus.RECEIVED) {
            throw new IllegalStateException("Receivable does not accept settlements in its current status");
        }
    }

    private void record(Settlement settlement, String origin, UUID changeRequestId) {
        auditEventRecorder.recordDirectAction(
                AuditModule.RECEIVABLES, AuditAction.CREATE, "ReceivableSettlement", settlement.receivableSettlement().getId(),
                null, Map.of(
                        "receivableId", settlement.receivableSettlement().getReceivableId().toString(),
                        "contractId", settlement.receivableSettlement().getContractId().toString(),
                        "amount", settlement.receivableSettlement().getAmount(),
                        "paymentMethod", settlement.receivableSettlement().getPaymentMethod().name(),
                        "status", settlement.receivableSettlement().getStatus().name()
                ), metadata(origin, changeRequestId)
        );
        if (settlement.previousStatus() != settlement.updatedReceivable().getStatus()) {
            auditEventRecorder.recordDirectAction(
                    AuditModule.RECEIVABLES, AuditAction.UPDATE, "Receivable", settlement.updatedReceivable().getId(),
                    Map.of("status", settlement.previousStatus().name()),
                    Map.of(
                            "status", settlement.updatedReceivable().getStatus().name(),
                            "confirmedSettlementAmount", settlement.balance().confirmedSettlementAmount(),
                            "outstandingAmount", settlement.balance().outstandingAmount()
                    ), metadata(origin, changeRequestId)
            );
        }
    }

    private Map<String, Object> proposal(CreateReceivableSettlementCommand command) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("receivableId", command.receivableId().toString());
        proposal.put("receivedOn", command.receivedOn().toString());
        proposal.put("amount", command.amount().toPlainString());
        proposal.put("paymentMethod", command.paymentMethod().name());
        proposal.put("note", command.note());
        return proposal;
    }

    private CreateReceivableSettlementCommand fromProposal(Map<String, Object> proposal) {
        return new CreateReceivableSettlementCommand(
                UUID.fromString((String) proposal.get("receivableId")),
                LocalDate.parse((String) proposal.get("receivedOn")),
                new BigDecimal((String) proposal.get("amount")),
                com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod
                        .valueOf((String) proposal.get("paymentMethod")),
                (String) proposal.get("note"),
                null
        );
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin);
        if (changeRequestId != null) {
            metadata.put("changeRequestId", changeRequestId.toString());
        }
        return metadata;
    }

    private record Settlement(ReceivableSettlement receivableSettlement, ReceivableBalance balance,
                              Receivable updatedReceivable, ReceivableStatus previousStatus) {
    }
}
