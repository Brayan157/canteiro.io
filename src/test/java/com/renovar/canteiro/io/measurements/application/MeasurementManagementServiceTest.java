package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
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
import com.renovar.canteiro.io.works.domain.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeasurementManagementServiceTest {

    private final TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
    private final AccessAuthorizationService authorizationService = mock(AccessAuthorizationService.class);
    private final MeasurementRepository measurementRepository = mock(MeasurementRepository.class);
    private final MeasurementVersionRepository versionRepository = mock(MeasurementVersionRepository.class);
    private final MeasurementItemRepository itemRepository = mock(MeasurementItemRepository.class);
    private final MeasurementDiscountRepository discountRepository = mock(MeasurementDiscountRepository.class);
    private MeasurementManagementService service;
    private UUID companyId;
    private UUID measurementId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MeasurementManagementService(tenantContextHolder, authorizationService,
                mock(WorkRepository.class), mock(ContractRepository.class), measurementRepository, versionRepository,
                itemRepository, discountRepository, mock(ChangeRequestService.class), mock(AuditEventRecorder.class),
                mock(MeasurementContinuousServiceManagementService.class),
                mock(com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository.class));
        companyId = UUID.randomUUID();
        measurementId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement()));
        when(versionRepository.findLatestByMeasurementIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(version()));
        when(discountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(Optional.empty());
    }

    @Test
    void hidesLogicallyRemovedItemsFromOperationalDetailsAndTotals() {
        MeasurementItem active = item(1, true, "100.00");
        MeasurementItem removed = item(2, false, "50.00");
        when(itemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(List.of(active, removed));

        MeasurementDetails details = service.find(measurementId);

        assertEquals(List.of(active), details.items());
        assertEquals(new BigDecimal("100.00"), details.amounts().grossAmount());
        assertEquals(1, details.services().getFirst().itemCount());
        verify(authorizationService).requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        verify(itemRepository).findByMeasurementVersionIdAndCompanyId(versionId, companyId);
    }

    @Test
    void returnsTheVersionTimelineInDescendingOrderWithEachVersionsOfficialTotals() {
        UUID acceptedVersionId = UUID.randomUUID();
        MeasurementVersion accepted = MeasurementVersion.rehydrate(acceptedVersionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.ACCEPTED, 0, LocalDate.of(2026, 10, 2), null, Instant.now(), Instant.now());
        MeasurementVersion draft = MeasurementVersion.rehydrate(versionId, companyId, measurementId, 2, acceptedVersionId,
                MeasurementVersionStatus.DRAFT, 0, null, null, Instant.now(), Instant.now());
        when(versionRepository.findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(measurementId, companyId))
                .thenReturn(List.of(draft, accepted));
        when(itemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId))
                .thenReturn(List.of(item(1, true, "25.00")));
        when(itemRepository.findByMeasurementVersionIdAndCompanyId(acceptedVersionId, companyId))
                .thenReturn(List.of(item(1, true, "100.00")));
        when(discountRepository.findByMeasurementVersionIdAndCompanyId(acceptedVersionId, companyId)).thenReturn(Optional.empty());

        List<MeasurementVersionHistory> history = service.findVersionHistory(measurementId);

        assertEquals(List.of(versionId, acceptedVersionId), history.stream().map(value -> value.version().getId()).toList());
        assertEquals(acceptedVersionId, history.getFirst().version().getPreviousVersionId());
        assertEquals(new BigDecimal("25.00"), history.getFirst().amounts().grossAmount());
        assertEquals(new BigDecimal("100.00"), history.get(1).amounts().grossAmount());
        verify(authorizationService).requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
    }

    @Test
    void returnsTheRequestedHistoricalVersionInsteadOfTheLatestDraft() {
        UUID acceptedVersionId = UUID.randomUUID();
        MeasurementVersion accepted = MeasurementVersion.rehydrate(acceptedVersionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.ACCEPTED, 0, LocalDate.of(2026, 10, 2), null, Instant.now(), Instant.now());
        when(versionRepository.findByIdAndCompanyId(acceptedVersionId, companyId)).thenReturn(Optional.of(accepted));
        when(itemRepository.findByMeasurementVersionIdAndCompanyId(acceptedVersionId, companyId))
                .thenReturn(List.of(item(1, true, "100.00")));
        when(discountRepository.findByMeasurementVersionIdAndCompanyId(acceptedVersionId, companyId)).thenReturn(Optional.empty());

        MeasurementVersionDetails details = service.findVersion(measurementId, acceptedVersionId);

        assertEquals(acceptedVersionId, details.version().getId());
        assertEquals(MeasurementVersionStatus.ACCEPTED, details.version().getStatus());
        assertEquals(1, details.items().size());
        assertEquals(new BigDecimal("100.00"), details.amounts().grossAmount());
    }

    private Measurement measurement() {
        return Measurement.rehydrate(measurementId, companyId, null, null, "Medição", "MED-1", null,
                LocalDate.of(2026, 10, 1), MeasurementStatus.DRAFT, 0, Instant.now(), Instant.now());
    }

    private MeasurementVersion version() {
        return MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.DRAFT, 0, null, null, Instant.now(), Instant.now());
    }

    private MeasurementItem item(int itemNumber, boolean active, String totalAmount) {
        return MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, itemNumber, "Serviço", "Atividade", null,
                LocalDate.of(2026, 10, 1), MeasurementChargeType.LINEAR_METER, null, new BigDecimal("1.0000"),
                null, null, null, new BigDecimal(totalAmount), null, new BigDecimal(totalAmount),
                "linear_meters × unit_price", active, null, Instant.now(), Instant.now());
    }
}
