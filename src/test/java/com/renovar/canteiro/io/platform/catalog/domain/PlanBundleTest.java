package com.renovar.canteiro.io.platform.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanBundleTest {

    @Test
    void normalizesBundleCodeAndTextFields() {
        PlanBundle planBundle = PlanBundle.create(" starter_professional ", " Starter + Professional ", " Promotion ");

        assertEquals("STARTER_PROFESSIONAL", planBundle.getCode());
        assertEquals("Starter + Professional", planBundle.getName());
        assertEquals("Promotion", planBundle.getDescription());
    }

    @Test
    void rejectsAnInvalidBundleCode() {
        assertThrows(IllegalArgumentException.class, () -> PlanBundle.create("starter-professional", "Promotion", null));
    }
}
