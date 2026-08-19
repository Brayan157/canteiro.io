package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementWorkflowTest {

    @Test
    void advancesFromDraftToAcceptedThenFinalizedWithExternalAcceptanceRecorded() {
        Measurement measurement = Measurement.create(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null);
        MeasurementVersion version = MeasurementVersion.create(UUID.randomUUID(), UUID.randomUUID(), 1);

        measurement.markSent();
        version.markSent();
        measurement.markPendingAcceptance();
        version.markPendingAcceptance();
        measurement.recordExternalAcceptance(true);
        version.recordExternalAcceptance(true, LocalDate.of(2026, 8, 18), "Accepted by customer email");
        measurement.finalizeMeasurement();

        assertEquals(MeasurementStatus.FINALIZED, measurement.getStatus());
        assertEquals(MeasurementVersionStatus.ACCEPTED, version.getStatus());
        assertEquals(LocalDate.of(2026, 8, 18), version.getExternalAcceptanceOn());
    }

    @Test
    void rejectsInvalidStateTransitionsAndAcceptanceWithoutDate() {
        Measurement measurement = Measurement.create(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null);
        MeasurementVersion version = MeasurementVersion.create(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertThrows(IllegalStateException.class, measurement::finalizeMeasurement);
        assertThrows(IllegalStateException.class, version::markPendingAcceptance);
        version.markSent();
        version.markPendingAcceptance();
        assertThrows(IllegalArgumentException.class, () -> version.recordExternalAcceptance(true, null, null));
    }

    @Test
    void startsAnIncrementalRevisionOnlyAfterTheMeasurementIsAccepted() {
        Measurement measurement = Measurement.create(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null);
        MeasurementVersion acceptedVersion = MeasurementVersion.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, null,
                MeasurementVersionStatus.ACCEPTED, 0, LocalDate.of(2026, 8, 18), null, null, null
        );

        assertThrows(IllegalStateException.class, measurement::startRevision);
        measurement.markSent();
        measurement.markPendingAcceptance();
        measurement.recordExternalAcceptance(true);
        measurement.startRevision();
        MeasurementVersion revision = MeasurementVersion.createRevision(
                acceptedVersion.getCompanyId(), acceptedVersion.getMeasurementId(), 2, acceptedVersion.getId()
        );

        assertEquals(MeasurementStatus.DRAFT, measurement.getStatus());
        assertEquals(acceptedVersion.getId(), revision.getPreviousVersionId());
        assertEquals(MeasurementVersionStatus.DRAFT, revision.getStatus());
    }
}
