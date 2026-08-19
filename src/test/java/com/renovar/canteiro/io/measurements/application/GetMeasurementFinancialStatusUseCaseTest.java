package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.application.ApprovedContractServiceBillingAmountProvider;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMeasurementFinancialStatusUseCaseTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private MeasurementRepository measurementRepository;
    @Mock private MeasurementVersionRepository measurementVersionRepository;
    @Mock private MeasurementItemRepository measurementItemRepository;
    @Mock private MeasurementDiscountRepository measurementDiscountRepository;
    @Mock private MeasurementItemContractServiceConversionRepository conversionRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private ApprovedContractServiceBillingAmountProvider billingAmountProvider;

    @Test
    void sumsOnlyServicesOriginatedByTheRequestedMeasurementVersion() {
        UUID companyId = UUID.randomUUID();
        UUID measurementId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID contractServiceId = UUID.randomUUID();
        Measurement measurement = Measurement.rehydrate(measurementId, companyId, UUID.randomUUID(), contractId, null, null,
                null, MeasurementStatus.ACCEPTED, 0, Instant.now(), Instant.now());
        MeasurementVersion version = MeasurementVersion.rehydrate(versionId, companyId, measurementId, 1, null,
                MeasurementVersionStatus.ACCEPTED, 0, java.time.LocalDate.now(), null, Instant.now(), Instant.now());
        MeasurementItemContractServiceConversion conversion = MeasurementItemContractServiceConversion.rehydrate(
                UUID.randomUUID(), companyId, versionId, itemId, contractId, contractServiceId, Instant.now());
        ContractService service = ContractService.rehydrate(contractServiceId, companyId, contractId, null, "Telhado", null,
                ContractServiceStatus.ACTIVE, new BigDecimal("10.0000"), new BigDecimal("25.00"),
                new BigDecimal("250.00"), null, null, new BigDecimal("0.00"), new BigDecimal("250.00"),
                Instant.now(), Instant.now());
        stubTenant(companyId);
        when(measurementRepository.findByIdAndCompanyId(measurementId, companyId)).thenReturn(Optional.of(measurement));
        when(measurementVersionRepository.findByIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(version));
        when(measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(List.of(
                MeasurementItem.createSquareMeter(companyId, versionId, 1, "Telhado", null,
                        new BigDecimal("10.0000"), new BigDecimal("25.00"))
        ));
        when(measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(Optional.of(
                MeasurementDiscount.create(companyId, versionId, MeasurementDiscountType.FIXED, new BigDecimal("10.00"))
        ));
        when(conversionRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)).thenReturn(List.of(conversion));
        when(contractServiceRepository.findByIdAndCompanyId(contractServiceId, companyId)).thenReturn(Optional.of(service));
        when(billingAmountProvider.approvedNetAmount(companyId, contractServiceId)).thenReturn(new BigDecimal("100.00"));
        GetMeasurementFinancialStatusUseCase useCase = useCase();

        MeasurementFinancialStatus result = useCase.get(measurementId, versionId);

        assertEquals(new BigDecimal("250.00"), result.originatedServiceAmount());
        assertEquals(new BigDecimal("10.00"), result.headerDiscountAmount());
        assertEquals(new BigDecimal("240.00"), result.netMeasurementAmount());
        assertEquals(new BigDecimal("100.00"), result.billedAmount());
        assertEquals(new BigDecimal("150.00"), result.balanceAmount());
        assertEquals(List.of(contractServiceId), result.services().stream()
                .map(position -> position.contractServiceId()).toList());
    }

    @Test
    void doesNotRevealMeasurementFromAnotherTenant() {
        UUID companyId = UUID.randomUUID();
        UUID foreignMeasurementId = UUID.randomUUID();
        stubTenant(companyId);
        when(measurementRepository.findByIdAndCompanyId(foreignMeasurementId, companyId)).thenReturn(Optional.empty());

        assertThrows(TenantResourceNotFoundException.class, () -> useCase().get(foreignMeasurementId, UUID.randomUUID()));

        verify(measurementVersionRepository, never()).findByIdAndCompanyId(any(), any());
        verify(conversionRepository, never()).findByMeasurementVersionIdAndCompanyId(any(), any());
    }

    private void stubTenant(UUID companyId) {
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
    }

    private GetMeasurementFinancialStatusUseCase useCase() {
        return new GetMeasurementFinancialStatusUseCase(tenantContextHolder, accessAuthorizationService, measurementRepository,
                measurementVersionRepository, measurementItemRepository, measurementDiscountRepository, conversionRepository,
                contractServiceRepository, billingAmountProvider);
    }
}
