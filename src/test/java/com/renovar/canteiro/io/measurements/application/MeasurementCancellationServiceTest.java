package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementCancellationServiceTest {

    private final TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
    private final AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
    private final MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final AuditEventRecorder auditEventRecorder = mock(AuditEventRecorder.class);
    private MeasurementCancellationService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new MeasurementCancellationService(tenantContextHolder, authorizationService,
                measurementRepository, changeRequestService, auditEventRecorder);
        companyId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
    }

    @Test
    void cancelsDirectlyAndAuditsWithoutDeletingTheMeasurement() {
        Measurement measurement = measurement(null, MeasurementStatus.DRAFT);
        when(measurementRepository.findByIdAndCompanyId(measurement.getId(), companyId))
                .thenReturn(Optional.of(measurement));
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CANCEL))
                .thenReturn(ChangeAuthorizationMode.DIRECT);
        when(measurementRepository.save(measurement)).thenReturn(measurement);

        MeasurementCancellationResult result = service.cancel(measurement.getId(), "Cadastro duplicado");

        assertEquals(MeasurementStatus.CANCELLED, measurement.getStatus());
        assertNull(result.changeRequest());
        verify(measurementRepository).save(measurement);
        verify(auditEventRecorder).recordDirectAction(eq(AuditModule.MEASUREMENTS), eq(AuditAction.CANCEL),
                eq("Measurement"), eq(measurement.getId()), any(), any(), any());
    }

    @Test
    void submitsApprovalWithoutChangingTheOfficialMeasurement() {
        Measurement measurement = measurement(null, MeasurementStatus.ACCEPTED);
        ChangeRequest request = mock(ChangeRequest.class);
        when(measurementRepository.findByIdAndCompanyId(measurement.getId(), companyId))
                .thenReturn(Optional.of(measurement));
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CANCEL))
                .thenReturn(ChangeAuthorizationMode.REQUEST_APPROVAL);
        when(changeRequestService.create(any(CreateChangeRequestCommand.class))).thenReturn(request);

        MeasurementCancellationResult result = service.cancel(measurement.getId(), "Serviço não executado");

        assertSame(request, result.changeRequest());
        assertEquals(MeasurementStatus.ACCEPTED, measurement.getStatus());
        verify(measurementRepository, never()).save(any());
        verify(auditEventRecorder, never()).recordDirectAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsContractedMeasurementAndDoesNotLeakAnotherTenant() {
        Measurement contracted = measurement(UUID.randomUUID(), MeasurementStatus.DRAFT);
        when(measurementRepository.findByIdAndCompanyId(contracted.getId(), companyId))
                .thenReturn(Optional.of(contracted));

        assertThrows(ApiException.class, () -> service.cancel(contracted.getId(), null));

        UUID foreignId = UUID.randomUUID();
        when(measurementRepository.findByIdAndCompanyId(foreignId, companyId)).thenReturn(Optional.empty());
        assertThrows(TenantResourceNotFoundException.class, () -> service.cancel(foreignId, null));
        verify(measurementRepository).findByIdAndCompanyId(foreignId, companyId);
    }

    private Measurement measurement(UUID contractId, MeasurementStatus status) {
        UUID workId = contractId == null ? null : UUID.randomUUID();
        return Measurement.rehydrate(UUID.randomUUID(), companyId, workId, contractId, "Medição",
                "MED-1", null, LocalDate.now(), status, 0, Instant.now(), Instant.now());
    }
}
