package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementTest {

    @Test
    void requiresCompanyAndWork() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> Measurement.create(null, id, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Measurement.create(id, null, null, null, null, null));
    }

    @Test
    void normalizesOptionalHeaderFields() {
        Measurement measurement = Measurement.create(
                UUID.randomUUID(), UUID.randomUUID(), null, "  Reference  ", "  Scope  ", null
        );

        assertEquals("Reference", measurement.getReference());
        assertEquals("Scope", measurement.getDescription());
        assertNull(measurement.getContractId());
        assertEquals(MeasurementStatus.DRAFT, measurement.getStatus());
    }
}
