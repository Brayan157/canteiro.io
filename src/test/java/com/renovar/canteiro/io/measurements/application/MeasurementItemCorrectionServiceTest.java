package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementItemCorrectionServiceTest {

    private final TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
    private final AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
    private final MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
    private final MeasurementVersionRepository versionRepository = mock(MeasurementVersionRepository.class);
    private final MeasurementItemRepository itemRepository = mock(MeasurementItemRepository.class);
    private final MeasurementDiscountRepository discountRepository = mock(MeasurementDiscountRepository.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final AuditEventRecorder auditEventRecorder = mock(AuditEventRecorder.class);
    private MeasurementItemCorrectionService service;
    private UUID companyId;
    private UUID measurementId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MeasurementItemCorrectionService(tenantContextHolder, authorizationService, measurementRepository,
                versionRepository, itemRepository, discountRepository, changeRequestService, auditEventRecorder);
        companyId = UUID.randomUUID();
        measurementId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement()));
        when(versionRepository.findByIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(version()));
        when(versionRepository.findLatestByMeasurementIdAndCompanyId(measurementId,companyId)).thenReturn(Optional.of(version()));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(discountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(Optional.empty());
    }

    @Test
    void directRemovalLogicallyDeactivatesAndAtomicallyRenumbersTheRemainingActiveItems() {
        MeasurementItem first = item(1, "10.00");
        MeasurementItem removed = item(2, "20.00");
        MeasurementItem third = item(3, "30.00");
        when(itemRepository.findWithLockByIdAndCompanyId(removed.getId(), companyId)).thenReturn(Optional.of(removed));
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId))
                .thenReturn(List.of(first, removed, third));
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CANCEL))
                .thenReturn(ChangeAuthorizationMode.DIRECT);

        MeasurementItemCorrectionResult result = service.removeDraft(measurementId, versionId, removed.getId(), "Atividade removida");

        assertFalse(result.item().isActive());
        assertEquals(2, result.item().getItemNumber(), "The inactive item retains its historical number");
        assertEquals(new BigDecimal("40.00"), result.amounts().grossAmount());
        ArgumentCaptor<MeasurementItem> saved = ArgumentCaptor.forClass(MeasurementItem.class);
        verify(itemRepository, times(5)).save(saved.capture());
        assertFalse(saved.getAllValues().getFirst().isActive());
        assertEquals(List.of(6, 7), saved.getAllValues().subList(1, 3).stream()
                .map(MeasurementItem::getItemNumber).toList());
        assertEquals(List.of(1, 2), saved.getAllValues().subList(3, 5).stream()
                .map(MeasurementItem::getItemNumber).toList());
        verify(itemRepository, times(3)).flush();
        verify(auditEventRecorder).recordDirectAction(any(), any(), eq("MeasurementItem"), eq(removed.getId()),
                any(), any(), any());
    }

    @Test
    void approvalRemovalDoesNotDeactivateOrRenumberAnyOfficialItem() {
        MeasurementItem removed = item(2, "20.00");
        ChangeRequest changeRequest = mock(ChangeRequest.class);
        when(itemRepository.findWithLockByIdAndCompanyId(removed.getId(), companyId)).thenReturn(Optional.of(removed));
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CANCEL))
                .thenReturn(ChangeAuthorizationMode.REQUEST_APPROVAL);
        when(changeRequestService.create(any(CreateChangeRequestCommand.class))).thenReturn(changeRequest);

        MeasurementItemCorrectionResult result = service.removeDraft(measurementId, versionId, removed.getId(), null);

        assertSame(changeRequest, result.changeRequest());
        verify(itemRepository, never()).save(any());
        verify(itemRepository, never()).flush();
        verify(auditEventRecorder, never()).recordDirectAction(any(), any(), any(), any(), any(), any(), any());
    }

    private Measurement measurement() {
        return Measurement.rehydrate(measurementId, companyId, null, null, "Medição", "MED-1", null,
                LocalDate.of(2026, 10, 1), MeasurementStatus.DRAFT, 0, Instant.now(), Instant.now());
    }

    private MeasurementVersion version() {
        return MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.DRAFT, 0, null, null, Instant.now(), Instant.now());
    }

    private MeasurementItem item(int itemNumber, String totalAmount) {
        BigDecimal amount = new BigDecimal(totalAmount);
        return MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, itemNumber, "Serviço", "Atividade", null,
                LocalDate.of(2026, 10, 1), MeasurementChargeType.LINEAR_METER, null, new BigDecimal("1.0000"),
                null, null, null, amount, null, amount, "linear_meters × unit_price", true, null,
                Instant.now(), Instant.now());
    }
}
