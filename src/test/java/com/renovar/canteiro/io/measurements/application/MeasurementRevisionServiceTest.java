package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementRevisionServiceTest {

    @Test
    void keepsAnAcceptedRevisionIncrementalWithoutCopyingItsItems() {
        TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
        AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
        MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
        MeasurementVersionRepository versionRepository = mock(MeasurementVersionRepository.class);
        MeasurementItemRepository itemRepository = mock(MeasurementItemRepository.class);
        MeasurementRevisionService service = new MeasurementRevisionService(tenantContextHolder, authorizationService,
                measurementRepository, versionRepository, itemRepository, mock(ChangeRequestService.class),
                mock(AuditEventRecorder.class));
        UUID companyId = UUID.randomUUID();
        UUID measurementId = UUID.randomUUID();
        UUID acceptedVersionId = UUID.randomUUID();
        Measurement measurement = Measurement.rehydrate(measurementId, companyId, null, null, "Medição", "MED-1", null,
                LocalDate.of(2026, 10, 1), MeasurementStatus.ACCEPTED, 0, Instant.now(), Instant.now());
        MeasurementVersion acceptedVersion = MeasurementVersion.rehydrate(acceptedVersionId, companyId, measurementId, 1,
                null, MeasurementVersionStatus.ACCEPTED, 0, LocalDate.of(2026, 10, 2), null, Instant.now(), Instant.now());
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement));
        when(versionRepository.findLatestByMeasurementIdAndCompanyId(measurementId, companyId))
                .thenReturn(Optional.of(acceptedVersion));
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE))
                .thenReturn(ChangeAuthorizationMode.DIRECT);
        when(measurementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(invocation -> {
            MeasurementVersion candidate = invocation.getArgument(0);
            return MeasurementVersion.rehydrate(UUID.randomUUID(), candidate.getCompanyId(), candidate.getMeasurementId(),
                    candidate.getVersionNumber(), candidate.getPreviousVersionId(), candidate.getStatus(), 0,
                    candidate.getExternalAcceptanceOn(), candidate.getExternalAcceptanceNotes(), candidate.getCorrectionItemIds(),
                    Instant.now(), Instant.now());
        });

        MeasurementRevisionChangeResult result = service.create(
                new CreateMeasurementRevisionCommand(measurementId, null));

        assertTrue(result.items().isEmpty());
        verify(itemRepository, never()).findByMeasurementVersionIdAndCompanyId(acceptedVersionId, companyId);
    }
}
