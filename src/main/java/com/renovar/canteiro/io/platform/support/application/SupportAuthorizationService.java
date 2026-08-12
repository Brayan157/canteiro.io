package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.platform.support.domain.SupportOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupportAuthorizationService {

    private final SupportTargetContextHolder supportTargetContextHolder;

    public void requireAllowed(SupportOperation operation) {
        supportTargetContextHolder.currentTarget()
                .orElseThrow(() -> new AccessDeniedException("A platform support target context is required"));
        if (!isAllowed(operation)) {
            throw new AccessDeniedException("Platform support is not allowed to perform this operation");
        }
    }

    private boolean isAllowed(SupportOperation operation) {
        return switch (operation) {
            case READ_OPERATIONAL_DATA,
                    CREATE_OPERATIONAL_DATA,
                    UPDATE_OPERATIONAL_DATA,
                    GENERATE_REPORT,
                    SEND_REPORT -> true;
            case APPROVE_CHANGE,
                    DELETE_RECORD,
                    MANAGE_SUBSCRIPTION,
                    MANAGE_COMPANY_STRUCTURE,
                    MANAGE_COMPANY_ROLES -> false;
        };
    }
}
