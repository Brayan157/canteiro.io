package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementTest {

    @Test
    void requiresCompanyAndTitleButAllowsAnUnboundDraft() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> Measurement.create(null, id, null, "Medição", null, null, null));
        Measurement unbound = Measurement.create(id, null, null, "Medição", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> Measurement.create(id, id, null, " ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Measurement.create(id, null, UUID.randomUUID(), "Medição", null, null, null));

        assertNull(unbound.getWorkId());
        assertNull(unbound.getContractId());
    }

    @Test
    void linksAnUnboundMeasurementWithoutAllowingItToMoveToAnotherWorkOrContract() {
        UUID workId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        Measurement measurement = Measurement.create(
                UUID.randomUUID(), null, null, "Medição", null, null, null);

        measurement.linkToWork(workId, null);
        measurement.linkToWork(workId, contractId);

        assertEquals(workId, measurement.getWorkId());
        assertEquals(contractId, measurement.getContractId());
        assertThrows(IllegalStateException.class,
                () -> measurement.linkToWork(UUID.randomUUID(), contractId));
        assertThrows(IllegalStateException.class,
                () -> measurement.linkToWork(workId, UUID.randomUUID()));
    }

    @Test
    void normalizesOptionalHeaderFields() {
        Measurement measurement = Measurement.create(
                UUID.randomUUID(), UUID.randomUUID(), null, "  Medição da fundação  ", "  Reference  ", "  Scope  ", null
        );

        assertEquals("Medição da fundação", measurement.getTitle());
        assertEquals("Reference", measurement.getReference());
        assertEquals("Scope", measurement.getDescription());
        assertNull(measurement.getContractId());
        assertEquals(MeasurementStatus.DRAFT, measurement.getStatus());
    }

    @Test
    void cancelsWithoutPhysicalDeletionOnlyWhenThereIsNoContractLink() {
        Measurement unbound = Measurement.create(
                UUID.randomUUID(), null, null, "Medição avulsa", null, null, null);
        Measurement workOnly = Measurement.create(
                UUID.randomUUID(), UUID.randomUUID(), null, "Medição da obra", null, null, null);
        Measurement contracted = Measurement.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Medição contratada", null, null, null);

        unbound.cancel();
        workOnly.cancel();

        assertEquals(MeasurementStatus.CANCELLED, unbound.getStatus());
        assertEquals(MeasurementStatus.CANCELLED, workOnly.getStatus());
        assertThrows(IllegalStateException.class, contracted::cancel);
        assertEquals(MeasurementStatus.DRAFT, contracted.getStatus());
    }
}
