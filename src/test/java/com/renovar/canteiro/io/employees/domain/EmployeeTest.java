package com.renovar.canteiro.io.employees.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeTest {

    @Test
    void createsOperationalEmployeeWithoutEmailOrSystemUser() {
        Employee employee = Employee.create(UUID.randomUUID(), "  Ana da Silva  ", "Montadora", "11999999999");

        assertEquals("Ana da Silva", employee.getFullName());
        assertNull(employee.getUserId());
    }

    @Test
    void linksAtMostOneSystemUserToEmployee() {
        Employee employee = Employee.create(UUID.randomUUID(), "Ana da Silva", null, null);
        employee.linkUser(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> employee.linkUser(UUID.randomUUID()));
    }

    @Test
    void requiresEmployeeFullName() {
        assertThrows(IllegalArgumentException.class, () -> Employee.create(UUID.randomUUID(), " ", null, null));
    }
}
