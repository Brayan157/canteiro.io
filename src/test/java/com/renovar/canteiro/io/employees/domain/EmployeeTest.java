package com.renovar.canteiro.io.employees.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeTest {

    @Test
    void createsOperationalEmployeeWithoutEmailOrSystemUser() {
        Employee employee = employee("  Ana da Silva  ", EmployeeDocumentType.CPF, "529.982.247-25");

        assertEquals("Ana da Silva", employee.getFullName());
        assertNull(employee.getUserId());
    }

    @Test
    void linksAtMostOneSystemUserToEmployee() {
        Employee employee = employee("Ana da Silva", EmployeeDocumentType.CPF, "529.982.247-25");
        employee.linkUser(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> employee.linkUser(UUID.randomUUID()));
    }

    @Test
    void requiresEmployeeFullName() {
        assertThrows(IllegalArgumentException.class,
                () -> employee(" ", EmployeeDocumentType.CPF, "529.982.247-25"));
    }

    @Test
    void requiresAnIdentificationDocument() {
        assertThrows(IllegalArgumentException.class, () -> employee("Ana da Silva", null, null));
        assertThrows(IllegalArgumentException.class, () -> employee("Ana da Silva", EmployeeDocumentType.PASSPORT, null));
    }

    @Test
    void normalizesAndMasksCpfWithoutExposingItInTheProfile() {
        Employee employee = employee("Ana da Silva", EmployeeDocumentType.CPF, "529.982.247-25");

        assertEquals("52998224725", employee.getDocumentNumber());
        assertEquals("***.***.***-25", employee.getDocumentNumberMasked());
    }

    @Test
    void rejectsAnEmploymentEndDateBeforeTheStartDate() {
        assertThrows(IllegalArgumentException.class, () -> Employee.create(
                UUID.randomUUID(), "Ana da Silva", null, "Montadora", null, null, "MAT-001",
                EmployeeDocumentType.CPF, "529.982.247-25", null, LocalDate.of(1990, 5, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), false,
                EmployeeGender.FEMALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("3500.00")
        ));
    }

    private static Employee employee(String fullName, EmployeeDocumentType documentType, String documentNumber) {
        return Employee.create(
                UUID.randomUUID(), fullName, null, "Montadora", null, "11999999999", "MAT-001",
                documentType, documentNumber, null, LocalDate.of(1990, 5, 20), EmployeeEmploymentType.EMPLOYEE,
                LocalDate.of(2024, 1, 10), null, true, EmployeeGender.FEMALE, null,
                EmployeeCompensationFrequency.MONTHLY, new BigDecimal("3500.00")
        );
    }
}
