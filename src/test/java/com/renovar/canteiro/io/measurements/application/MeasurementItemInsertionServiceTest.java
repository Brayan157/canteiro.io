package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementItemInsertionServiceTest {

    private final TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
    private final AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
    private final MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
    private final MeasurementVersionRepository versionRepository = mock(MeasurementVersionRepository.class);
    private final MeasurementItemRepository itemRepository = mock(MeasurementItemRepository.class);
    private final MeasurementDiscountRepository discountRepository = mock(MeasurementDiscountRepository.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final AuditEventRecorder auditEventRecorder = mock(AuditEventRecorder.class);
    private MeasurementItemInsertionService service;
    private UUID companyId;
    private UUID measurementId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MeasurementItemInsertionService(tenantContextHolder, authorizationService, measurementRepository,
                versionRepository, itemRepository, discountRepository, changeRequestService, auditEventRecorder,
                mock(MeasurementContinuousServiceManagementService.class),
                mock(com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository.class));
        companyId = UUID.randomUUID();
        measurementId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement()));
        when(versionRepository.findWithLockByIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(version()));
        when(versionRepository.findLatestByMeasurementIdAndCompanyId(measurementId,companyId)).thenReturn(Optional.of(version()));
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(discountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    }

    @Test
    void insertsAfterAnchorAndAtomicallyRenumbersOnlyTheActiveSequence() {
        List<MeasurementItem> current = List.of(item(1), item(2), item(3), item(4), item(5));
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(current);
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CREATE))
                .thenReturn(ChangeAuthorizationMode.DIRECT);

        MeasurementItemInsertionResult result = service.insert(command(current.get(1).getId()));

        assertEquals(3, result.item().getItemNumber());
        assertEquals(new BigDecimal("1.0000"), result.item().getUnitQuantity());
        assertEquals(new BigDecimal("250.00"), result.item().getTotalAmount());
        ArgumentCaptor<MeasurementItem> saved = ArgumentCaptor.forClass(MeasurementItem.class);
        verify(itemRepository, times(11)).save(saved.capture());
        assertEquals(List.of(11, 12, 13, 14, 15), saved.getAllValues().subList(0, 5).stream()
                .map(MeasurementItem::getItemNumber).toList());
        assertEquals(List.of(1, 2, 4, 5, 6), saved.getAllValues().subList(5, 10).stream()
                .map(MeasurementItem::getItemNumber).toList());
        assertEquals(3, saved.getAllValues().get(10).getItemNumber());
        verify(itemRepository, times(2)).flush();
        verify(auditEventRecorder).recordDirectAction(any(), any(), eq("MeasurementItemInsertion"), any(), any(), any(), any());
    }

    @Test
    void approvalRequestDoesNotPersistOrRenumberTheOfficialItems() {
        List<MeasurementItem> current = List.of(item(1), item(2), item(3));
        ChangeRequest changeRequest = mock(ChangeRequest.class);
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(current);
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CREATE))
                .thenReturn(ChangeAuthorizationMode.REQUEST_APPROVAL);
        when(changeRequestService.create(any(CreateChangeRequestCommand.class))).thenReturn(changeRequest);

        MeasurementItemInsertionResult result = service.insert(command(current.get(1).getId()));

        assertSame(changeRequest, result.changeRequest());
        verify(itemRepository, never()).save(any());
        verify(itemRepository, never()).flush();
        verify(auditEventRecorder, never()).recordDirectAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void excludesInactiveItemsFromTheRenumberedSequence() {
        MeasurementItem inactiveHistoricalItem = inactiveItem(3);
        List<MeasurementItem> current = List.of(item(1), item(2), inactiveHistoricalItem, item(4));
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(current);
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CREATE))
                .thenReturn(ChangeAuthorizationMode.DIRECT);

        service.insert(command(current.get(1).getId()));

        ArgumentCaptor<MeasurementItem> saved = ArgumentCaptor.forClass(MeasurementItem.class);
        verify(itemRepository, times(7)).save(saved.capture());
        assertEquals(List.of(8, 9, 10, 1, 2, 4, 3), saved.getAllValues().stream()
                .map(MeasurementItem::getItemNumber).toList());
    }

    @Test
    void doesNotRevealAnAnchorFromAnotherTenant() {
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(List.of(item(1)));

        assertThrows(TenantResourceNotFoundException.class, () -> service.insert(command(UUID.randomUUID())));
    }

    private InsertMeasurementItemCommand command(UUID anchorId) {
        return new InsertMeasurementItemCommand(measurementId, versionId, anchorId, "Novo serviço", "Nova atividade", null,
                LocalDate.of(2026, 10, 1), MeasurementChargeType.LINEAR_METER, null, new BigDecimal("10.0000"),
                null, null, new BigDecimal("1.0000"), new BigDecimal("25.00"), null, new BigDecimal("250.00"), null);
    }

    private Measurement measurement() {
        return Measurement.rehydrate(measurementId, companyId, null, null, "Medição", "MED-1", null,
                LocalDate.of(2026, 10, 1), MeasurementStatus.DRAFT, 0, Instant.now(), Instant.now());
    }

    private MeasurementVersion version() {
        return MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.DRAFT, 0, null, null, Instant.now(), Instant.now());
    }

    private MeasurementItem item(int number) {
        return MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, number, "Serviço " + number,
                "Atividade " + number, null, LocalDate.of(2026, 10, 1), MeasurementChargeType.LINEAR_METER,
                null, new BigDecimal("1.0000"), null, null, new BigDecimal("25.00"), null,
                new BigDecimal("25.00"), "linear_meters × unit_price", true, null, Instant.now(), Instant.now());
    }

    private MeasurementItem inactiveItem(int number) {
        MeasurementItem active = item(number);
        return MeasurementItem.rehydrate(active.getId(), active.getCompanyId(), active.getMeasurementVersionId(),
                active.getItemNumber(), active.getServiceTitle(), active.getActivity(), active.getDescription(), active.getMeasuredOn(),
                active.getChargeType(), active.getAreaSquareMeters(), active.getLinearMeters(), active.getKilogramsPerSquareMeter(),
                active.getKilogramsPerLinearMeter(), active.getUnitQuantity(), active.getUnitPrice(), active.getTotalWeightKg(),
                active.getTotalAmount(), active.getCalculationFormula(), false, null, Instant.now(), Instant.now());
    }

    private MeasurementItem persisted(MeasurementItem item) {
        if (item.getId() != null) return item;
        return MeasurementItem.rehydrate(UUID.randomUUID(), item.getCompanyId(), item.getMeasurementVersionId(),
                item.getItemNumber(), item.getServiceTitle(), item.getActivity(), item.getDescription(), item.getMeasuredOn(),
                item.getChargeType(), item.getAreaSquareMeters(), item.getLinearMeters(), item.getKilogramsPerSquareMeter(),
                item.getKilogramsPerLinearMeter(), item.getUnitQuantity(), item.getUnitPrice(), item.getTotalWeightKg(),
                item.getTotalAmount(), item.getCalculationFormula(), item.isActive(), item.getSourceItemId(), Instant.now(), Instant.now());
    }
}
