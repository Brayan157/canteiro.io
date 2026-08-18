package com.renovar.canteiro.io.access;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialPermissionCatalogIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsControlledPermissionsForEachInitialAccessCategory() {
        assertEquals(152, permissionCount());
        assertPermission(AccessModule.CUSTOMERS, AccessAction.READ);
        assertPermission(AccessModule.CONTRACTS, AccessAction.CREATE_DIRECT);
        assertPermission(AccessModule.CONTRACTS, AccessAction.CANCEL_DIRECT);
        assertPermission(AccessModule.BILLING, AccessAction.REQUEST_UPDATE);
        assertPermission(AccessModule.PAYABLES, AccessAction.APPROVE);
        assertPermission(AccessModule.MEASUREMENTS, AccessAction.REJECT);
        assertPermission(AccessModule.REPORTING, AccessAction.EXPORT);
        assertPermission(AccessModule.REPORTING, AccessAction.SEND_REPORT);
        assertPermission(AccessModule.USERS, AccessAction.MANAGE_USERS);
        assertPermission(AccessModule.EMPLOYEES, AccessAction.CREATE_DIRECT);
        assertPermission(AccessModule.EMPLOYEES, AccessAction.APPROVE);
        assertPermission(AccessModule.ROLES, AccessAction.MANAGE_ROLES);
    }

    @Test
    void doesNotSeedActionsOutsideTheirControlledModuleScope() {
        assertTrue(permissionRepository.findByModuleAndAction(
                AccessModule.AUDIT,
                AccessAction.UPDATE_DIRECT
        ).isEmpty());
        assertTrue(permissionRepository.findByModuleAndAction(
                AccessModule.REPORTING,
                AccessAction.APPROVE
        ).isEmpty());
        assertTrue(permissionRepository.findByModuleAndAction(
                AccessModule.CONTRACTS,
                AccessAction.MANAGE_ROLES
        ).isEmpty());
    }

    private void assertPermission(AccessModule module, AccessAction action) {
        assertTrue(permissionRepository.findByModuleAndAction(module, action).isPresent());
    }

    private Integer permissionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permission", Integer.class);
    }
}
