package com.renovar.canteiro.io.platform.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanTest {

    @Test
    void normalizesPlanCodeAndTextFields() {
        Plan plan = Plan.create(" starter_2026 ", " Starter ", " Entry plan ");

        assertEquals("STARTER_2026", plan.getCode());
        assertEquals("Starter", plan.getName());
        assertEquals("Entry plan", plan.getDescription());
        assertTrue(plan.isActive());
    }

    @Test
    void rejectsAnInvalidPlanCode() {
        assertThrows(IllegalArgumentException.class, () -> Plan.create("starter-plan", "Starter", null));
    }
}
