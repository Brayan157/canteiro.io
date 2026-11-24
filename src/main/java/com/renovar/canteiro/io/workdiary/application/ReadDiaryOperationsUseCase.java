package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadDiaryOperationsUseCase {
    private final TenantContextHolder tenants;
    private final WorkforceRepository workforce;
    private final AccessAuthorizationService authorization;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OperationsResponse read(UUID work) {
        var tenant = tenants.requireCurrentTenant();
        Instant now = clock.instant();
        var granted = workforce.activeOperations(tenant.companyId(), work, tenant.userId(), now);
        if (granted.isEmpty()) {
            throw new TenantResourceNotFoundException("Work");
        }
        var create = authorization.changeAuthorizationIfAllowed(AccessModule.WORK_DIARY, ChangeOperation.CREATE);
        var update = authorization.changeAuthorizationIfAllowed(AccessModule.WORK_DIARY, ChangeOperation.UPDATE);
        var operations = new ArrayList<OperationResponse>();
        for (WorkOperation operation : WorkOperation.values()) {
            if (!granted.contains(operation)) {
                continue;
            }
            switch (operation) {
                case CREATE -> create.ifPresent(mode -> operations.add(new OperationResponse(operation, mode)));
                case UPDATE, CLOSE, REOPEN, PLAN -> update.ifPresent(mode -> operations.add(new OperationResponse(operation, mode)));
                case READ -> add(operations, operation, read(AccessModule.WORK_DIARY));
                case APPROVE -> add(operations, operation, authorization.hasPermission(AccessModule.WORK_DIARY, AccessAction.APPROVE));
                case REJECT -> add(operations, operation, authorization.hasPermission(AccessModule.WORK_DIARY, AccessAction.REJECT));
                case REGISTER_OCCASIONAL -> add(operations, operation,
                        granted.contains(WorkOperation.CREATE) && create.isPresent()
                                || granted.contains(WorkOperation.UPDATE) && update.isPresent());
                case READ_FINANCIAL -> add(operations, operation, read(AccessModule.REPORTING) && read(AccessModule.WORKS)
                        && read(AccessModule.PAYABLES) && (read(AccessModule.RECEIVABLES)
                        || read(AccessModule.BILLING) && read(AccessModule.INVOICES)));
            }
        }
        return new OperationsResponse(now, operations, true, true, true, true);
    }

    private boolean read(AccessModule module) {
        return authorization.hasPermission(module, AccessAction.READ);
    }

    private static void add(List<OperationResponse> rows, WorkOperation operation, boolean allowed) {
        if (allowed) {
            rows.add(new OperationResponse(operation, null));
        }
    }

    public record OperationsResponse(Instant calculatedAt, List<OperationResponse> operations,
                                     boolean fieldObservationsSupported, boolean photoEvidenceSupported,
                                     boolean occurrenceTimingSupported, boolean itemPhotoEvidenceSupported) { }

    public record OperationResponse(WorkOperation operation, ChangeAuthorizationMode mode) { }
}
