package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementVersionTest {

    @Test
    void startsInDraftAndRequiresAPositiveVersionNumber() {
        MeasurementVersion version = MeasurementVersion.create(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertEquals(MeasurementVersionStatus.DRAFT, version.getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementVersion.create(UUID.randomUUID(), UUID.randomUUID(), 0));
    }
}
