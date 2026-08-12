package com.renovar.canteiro.io.access.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionTest {

    @Test
    void createsPermissionFromControlledModuleAndAction() {
        Permission permission = Permission.create(AccessModule.BILLING, AccessAction.APPROVE);

        assertEquals("BILLING.APPROVE", permission.code());
    }

    @Test
    void rejectsPermissionWithoutModuleOrAction() {
        assertThrows(IllegalArgumentException.class, () -> Permission.create(null, AccessAction.READ));
        assertThrows(IllegalArgumentException.class, () -> Permission.create(AccessModule.USERS, null));
    }
}
