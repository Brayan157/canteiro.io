package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustmentRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustment;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvertAcceptedMeasurementItemToContractServiceUseCaseTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private MeasurementRepository measurementRepository;
    @Mock private MeasurementVersionRepository measurementVersionRepository;
    @Mock private MeasurementItemRepository measurementItemRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private MeasurementItemContractServiceConversionRepository conversionRepository;
    @Mock private MeasurementDiscountRepository measurementDiscountRepository;
    @Mock private MeasurementContractAdjustmentRepository adjustmentRepository;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void convertsAnAcceptedItemOnlyOnceAndReusesTheOriginalService() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID measurementId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID contractServiceId = UUID.randomUUID();
        Measurement measurement = Measurement.rehydrate(measurementId, companyId, workId, contractId, "M-01", null,
                LocalDate.of(2026, 8, 19), MeasurementStatus.ACCEPTED, 0, Instant.now(), Instant.now());
        MeasurementVersion version = MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.ACCEPTED, 0, LocalDate.of(2026, 8, 19), null, Instant.now(), Instant.now());
        MeasurementItem item = rehydrateItem(itemId, companyId, versionId);
        Contract contract = Contract.rehydrate(contractId, companyId, workId, "C-01", "Contrato", ContractStatus.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
        AtomicReference<MeasurementItemContractServiceConversion> savedConversion = new AtomicReference<>();
        ContractService savedService = ContractService.rehydrate(contractServiceId, companyId, contractId, null,
                item.getActivity(), item.getDescription(), ContractServiceStatus.ACTIVE, item.getAreaSquareMeters(),
                item.getUnitPrice(), item.getTotalAmount(), null, null, BigDecimal.ZERO.setScale(2), item.getTotalAmount(),
                Instant.now(), Instant.now());

        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(userId, companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement));
        when(measurementVersionRepository.findWithLockByIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(version));
        when(measurementItemRepository.findWithLockByIdAndCompanyId(itemId, companyId)).thenReturn(Optional.of(item));
        when(contractRepository.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        when(conversionRepository.findByMeasurementItemIdAndCompanyId(itemId, companyId))
                .thenAnswer(invocation -> Optional.ofNullable(savedConversion.get()));
        when(contractServiceRepository.save(any())).thenReturn(savedService);
        when(contractServiceRepository.findByIdAndCompanyId(contractServiceId, companyId)).thenReturn(Optional.of(savedService));
        when(conversionRepository.save(any())).thenAnswer(invocation -> {
            MeasurementItemContractServiceConversion conversion = invocation.getArgument(0);
            savedConversion.set(conversion);
            return conversion;
        });
        ConvertAcceptedMeasurementItemToContractServiceUseCase useCase = new ConvertAcceptedMeasurementItemToContractServiceUseCase(
                tenantContextHolder, accessAuthorizationService, measurementRepository, measurementVersionRepository,
                measurementItemRepository, contractRepository, contractServiceRepository, conversionRepository,
                measurementDiscountRepository, adjustmentRepository, changeRequestService, auditEventRecorder
        );
        ConvertAcceptedMeasurementItemCommand command = new ConvertAcceptedMeasurementItemCommand(measurementId, versionId,
                itemId, null);

        MeasurementItemConversionResult first = useCase.convert(command);
        MeasurementItemConversionResult second = useCase.convert(command);

        assertFalse(first.alreadyConverted());
        assertTrue(second.alreadyConverted());
        assertEquals(contractServiceId, first.contractService().getId());
        assertEquals(contractServiceId, second.contractService().getId());
        ArgumentCaptor<ContractService> serviceCaptor = ArgumentCaptor.forClass(ContractService.class);
        verify(contractServiceRepository, times(1)).save(serviceCaptor.capture());
        assertEquals(ContractServiceStatus.ACTIVE, serviceCaptor.getValue().getStatus());
        assertEquals(item.getAreaSquareMeters(), serviceCaptor.getValue().getQuantity());
        verify(conversionRepository, times(1)).save(any());
    }

    @Test
    void convertsAnAcceptedPreviousVersionAndPersistsItsHeaderAdjustmentOnce() {
        UUID companyId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID measurementId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Measurement measurement = Measurement.rehydrate(measurementId, companyId, workId, contractId, "M-02", null,
                LocalDate.now(), MeasurementStatus.DRAFT, 1, Instant.now(), Instant.now());
        MeasurementVersion version = MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.ACCEPTED, 1, LocalDate.now(), "Accepted before the revision", Instant.now(), Instant.now());
        MeasurementItem item = rehydrateItem(itemId, companyId, versionId);
        Contract contract = Contract.rehydrate(contractId, companyId, workId, "C-02", "Contrato", ContractStatus.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
        ContractService service = ContractService.rehydrate(UUID.randomUUID(), companyId, contractId, null,
                item.getActivity(), item.getDescription(), ContractServiceStatus.ACTIVE, item.getAreaSquareMeters(),
                item.getUnitPrice(), item.getTotalAmount(), null, null, BigDecimal.ZERO.setScale(2), item.getTotalAmount(),
                Instant.now(), Instant.now());
        AtomicReference<MeasurementItemContractServiceConversion> savedConversion = new AtomicReference<>();

        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement));
        when(measurementVersionRepository.findWithLockByIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(version));
        when(measurementItemRepository.findWithLockByIdAndCompanyId(itemId, companyId)).thenReturn(Optional.of(item));
        when(measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(List.of(item));
        when(contractRepository.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        when(contractServiceRepository.save(any())).thenReturn(service);
        when(conversionRepository.findByMeasurementItemIdAndCompanyId(itemId, companyId)).thenReturn(Optional.empty());
        when(conversionRepository.save(any())).thenAnswer(invocation -> {
            MeasurementItemContractServiceConversion conversion = invocation.getArgument(0);
            savedConversion.set(conversion);
            return conversion;
        });
        when(conversionRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId))
                .thenAnswer(invocation -> savedConversion.get() == null ? List.of() : List.of(savedConversion.get()));
        when(measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(
                Optional.of(MeasurementDiscount.create(companyId, versionId, MeasurementDiscountType.FIXED,
                        new BigDecimal("10.00")))
        );
        when(adjustmentRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(Optional.empty());
        ConvertAcceptedMeasurementItemToContractServiceUseCase useCase = new ConvertAcceptedMeasurementItemToContractServiceUseCase(
                tenantContextHolder, accessAuthorizationService, measurementRepository, measurementVersionRepository,
                measurementItemRepository, contractRepository, contractServiceRepository, conversionRepository,
                measurementDiscountRepository, adjustmentRepository, changeRequestService, auditEventRecorder
        );

        useCase.convert(new ConvertAcceptedMeasurementItemCommand(measurementId, versionId, itemId, null));

        ArgumentCaptor<MeasurementContractAdjustment> adjustmentCaptor = ArgumentCaptor.forClass(MeasurementContractAdjustment.class);
        verify(adjustmentRepository).save(adjustmentCaptor.capture());
        assertEquals(contractId, adjustmentCaptor.getValue().getContractId());
        assertEquals(new BigDecimal("10.00"), adjustmentCaptor.getValue().getAmount());
    }

    private MeasurementItem rehydrateItem(UUID itemId, UUID companyId, UUID versionId) {
        MeasurementItem created = MeasurementItem.createSquareMeter(companyId, versionId, 1, "Telhado", "Área coberta",
                new BigDecimal("10.0000"), new BigDecimal("25.00"));
        return MeasurementItem.rehydrate(itemId, companyId, versionId, created.getItemNumber(), created.getActivity(),
                created.getDescription(), created.getChargeType(), created.getAreaSquareMeters(), created.getLinearMeters(),
                created.getKilogramsPerSquareMeter(), created.getKilogramsPerLinearMeter(), created.getUnitPrice(),
                created.getTotalWeightKg(), created.getTotalAmount(), created.getCalculationFormula(), Instant.now(), Instant.now());
    }
}
