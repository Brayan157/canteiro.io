package com.renovar.canteiro.io.platform.support;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.support.application.SupportAuthorizationService;
import com.renovar.canteiro.io.platform.support.domain.SupportOperation;
import com.renovar.canteiro.io.platform.support.domain.SupportTargetContext;
import com.renovar.canteiro.io.platform.support.infrastructure.ThreadLocalSupportTargetContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupportAuthorizationServiceTest {

    @Test
    void allowsOnlyOperationalSupportAndReportingActionsForATargetCompany() {
        ThreadLocalSupportTargetContextHolder targetContextHolder = new ThreadLocalSupportTargetContextHolder();
        targetContextHolder.setCurrentTarget(new SupportTargetContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PlatformUserRole.PLATFORM_SUPPORT,
                UUID.randomUUID()
        ));
        SupportAuthorizationService supportAuthorizationService = new SupportAuthorizationService(targetContextHolder);

        assertDoesNotThrow(() -> supportAuthorizationService.requireAllowed(SupportOperation.READ_OPERATIONAL_DATA));
        assertDoesNotThrow(() -> supportAuthorizationService.requireAllowed(SupportOperation.CREATE_OPERATIONAL_DATA));
        assertDoesNotThrow(() -> supportAuthorizationService.requireAllowed(SupportOperation.UPDATE_OPERATIONAL_DATA));
        assertDoesNotThrow(() -> supportAuthorizationService.requireAllowed(SupportOperation.GENERATE_REPORT));
        assertDoesNotThrow(() -> supportAuthorizationService.requireAllowed(SupportOperation.SEND_REPORT));
    }

    @Test
    void deniesSupportFromApprovingDeletingOrManagingRestrictedCompanyData() {
        ThreadLocalSupportTargetContextHolder targetContextHolder = new ThreadLocalSupportTargetContextHolder();
        targetContextHolder.setCurrentTarget(new SupportTargetContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PlatformUserRole.PLATFORM_SUPPORT,
                UUID.randomUUID()
        ));
        SupportAuthorizationService supportAuthorizationService = new SupportAuthorizationService(targetContextHolder);

        assertThrows(AccessDeniedException.class, () -> supportAuthorizationService.requireAllowed(SupportOperation.APPROVE_CHANGE));
        assertThrows(AccessDeniedException.class, () -> supportAuthorizationService.requireAllowed(SupportOperation.DELETE_RECORD));
        assertThrows(AccessDeniedException.class, () -> supportAuthorizationService.requireAllowed(SupportOperation.MANAGE_SUBSCRIPTION));
        assertThrows(AccessDeniedException.class, () ->
                supportAuthorizationService.requireAllowed(SupportOperation.MANAGE_COMPANY_STRUCTURE));
        assertThrows(AccessDeniedException.class, () -> supportAuthorizationService.requireAllowed(SupportOperation.MANAGE_COMPANY_ROLES));
    }

    @Test
    void deniesAnySupportOperationWithoutASecureTargetCompanyContext() {
        SupportAuthorizationService supportAuthorizationService = new SupportAuthorizationService(
                new ThreadLocalSupportTargetContextHolder()
        );

        assertThrows(AccessDeniedException.class, () ->
                supportAuthorizationService.requireAllowed(SupportOperation.READ_OPERATIONAL_DATA));
    }
}
