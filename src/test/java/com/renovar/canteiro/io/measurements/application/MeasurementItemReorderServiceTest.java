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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementItemReorderServiceTest {

    private final TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
    private final AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
    private final MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
    private final MeasurementVersionRepository versionRepository = mock(MeasurementVersionRepository.class);
    private final MeasurementItemRepository itemRepository = mock(MeasurementItemRepository.class);
    private final MeasurementDiscountRepository discountRepository = mock(MeasurementDiscountRepository.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final AuditEventRecorder auditEventRecorder = mock(AuditEventRecorder.class);
    private MeasurementItemReorderService service;
    private UUID companyId;
    private UUID measurementId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MeasurementItemReorderService(tenantContextHolder, authorizationService, measurementRepository,
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
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void atomicallyReordersEveryActiveItemAndMovesItsServiceGrouping() {
        List<MeasurementItem> active = List.of(item(1, "Serviço A"), item(2, "Serviço B"), item(3, "Serviço C"));
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(active);
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE))
                .thenReturn(ChangeAuthorizationMode.DIRECT);

        MeasurementItemReorderResult result = service.reorder(command(active.get(0), active.get(2), active.get(1)));

        assertEquals(List.of(1, 2, 3), result.items().stream().map(MeasurementItem::getItemNumber).toList());
        assertEquals(List.of("Linha de vinhaça", "Linha de vinhaça", "Linha de álcool"),
                result.items().stream().map(MeasurementItem::getServiceTitle).toList());
        assertEquals(new BigDecimal("75.00"), result.amounts().grossAmount());
        ArgumentCaptor<MeasurementItem> saved = ArgumentCaptor.forClass(MeasurementItem.class);
        verify(itemRepository, times(6)).save(saved.capture());
        assertEquals(List.of(7, 8, 9), saved.getAllValues().subList(0, 3).stream()
                .map(MeasurementItem::getItemNumber).toList());
        assertEquals(List.of(1, 2, 3), saved.getAllValues().subList(3, 6).stream()
                .map(MeasurementItem::getItemNumber).toList());
        verify(itemRepository, times(2)).flush();
        verify(auditEventRecorder).recordDirectAction(any(), any(), eq("MeasurementItemReorder"), any(), any(), any(), any());
    }

    @Test
    void approvalRequestDoesNotPersistAnyOrderOrServiceChange() {
        List<MeasurementItem> active = List.of(item(1, "Serviço A"), item(2, "Serviço B"));
        ChangeRequest changeRequest = mock(ChangeRequest.class);
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(active);
        when(authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE))
                .thenReturn(ChangeAuthorizationMode.REQUEST_APPROVAL);
        when(changeRequestService.create(any(CreateChangeRequestCommand.class))).thenReturn(changeRequest);

        MeasurementItemReorderResult result = service.reorder(command(active.get(1), active.get(0)));

        assertSame(changeRequest, result.changeRequest());
        verify(itemRepository, never()).save(any());
        verify(itemRepository, never()).flush();
        verify(auditEventRecorder, never()).recordDirectAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsARequestThatOmitsAnActiveItem() {
        List<MeasurementItem> active = List.of(item(1, "Serviço A"), item(2, "Serviço B"));
        when(itemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(active);

        assertThrows(IllegalArgumentException.class, () -> service.reorder(new ReorderMeasurementItemsCommand(
                measurementId, versionId, List.of(new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(
                active.get(0).getId(), "Serviço A")), null)));
    }

    private ReorderMeasurementItemsCommand command(MeasurementItem first, MeasurementItem second, MeasurementItem third) {
        return new ReorderMeasurementItemsCommand(measurementId, versionId, List.of(
                new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(first.getId(), "Linha de vinhaça"),
                new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(second.getId(), "Linha de vinhaça"),
                new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(third.getId(), "Linha de álcool")
        ), "Ajustar sequência dos serviços");
    }

    private ReorderMeasurementItemsCommand command(MeasurementItem first, MeasurementItem second) {
        return new ReorderMeasurementItemsCommand(measurementId, versionId, List.of(
                new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(first.getId(), "Linha de vinhaça"),
                new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(second.getId(), "Linha de álcool")
        ), null);
    }

    private Measurement measurement() {
        return Measurement.rehydrate(measurementId, companyId, null, null, "Medição", "MED-1", null,
                LocalDate.of(2026, 10, 1), MeasurementStatus.DRAFT, 0, Instant.now(), Instant.now());
    }

    private MeasurementVersion version() {
        return MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.DRAFT, 0, null, null, Instant.now(), Instant.now());
    }

    private MeasurementItem item(int number, String serviceTitle) {
        return MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, number, serviceTitle,
                "Atividade " + number, null, LocalDate.of(2026, 10, 1), MeasurementChargeType.LINEAR_METER,
                null, new BigDecimal("1.0000"), null, null, new BigDecimal("25.00"), null,
                new BigDecimal("25.00"), "linear_meters × unit_price", true, null, Instant.now(), Instant.now());
    }
}
