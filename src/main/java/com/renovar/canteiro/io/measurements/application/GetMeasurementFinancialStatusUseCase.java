package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.application.ApprovedContractServiceBillingAmountProvider;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialStatusCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementOriginatedServiceFinancialPosition;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetMeasurementFinancialStatusUseCase {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final MeasurementItemContractServiceConversionRepository conversionRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final ApprovedContractServiceBillingAmountProvider billingAmountProvider;

    @Transactional(readOnly = true)
    public MeasurementFinancialStatus get(UUID measurementId, UUID measurementVersionId) {
        accessAuthorizationService.requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository.findByIdAndCompanyId(measurementVersionId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        List<MeasurementOriginatedServiceFinancialPosition> positions = conversionRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).stream()
                .map(conversion -> position(companyId, version, conversion))
                .toList();
        MeasurementFinancialStatusCalculator.Totals totals = MeasurementFinancialStatusCalculator.totals(positions);
        var measurementAmounts = MeasurementVersionAmountCalculator.calculate(
                measurementItemRepository.findByMeasurementVersionIdAndCompanyId(version.getId(), companyId),
                measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).orElse(null)
        );
        return new MeasurementFinancialStatus(measurement.getId(), version.getId(), totals.originatedServiceAmount(),
                measurementAmounts.discountAmount(), measurementAmounts.netAmount(), totals.billedAmount(),
                totals.balanceAmount(), positions);
    }

    private MeasurementOriginatedServiceFinancialPosition position(UUID companyId, MeasurementVersion version,
                                                                    MeasurementItemContractServiceConversion conversion) {
        if (!version.getId().equals(conversion.getMeasurementVersionId())) {
            throw new IllegalStateException("Measurement conversion belongs to a different version");
        }
        ContractService service = contractServiceRepository.findByIdAndCompanyId(conversion.getContractServiceId(), companyId)
                .orElseThrow(() -> new IllegalStateException("Measurement conversion references a missing contract service"));
        if (!conversion.getContractId().equals(service.getContractId())) {
            throw new IllegalStateException("Measurement conversion contract service is inconsistent");
        }
        BigDecimal billedAmount = billingAmountProvider.approvedNetAmount(companyId, service.getId());
        return MeasurementFinancialStatusCalculator.position(conversion.getMeasurementItemId(), service.getId(),
                service.getNetAmount(), billedAmount);
    }
}
