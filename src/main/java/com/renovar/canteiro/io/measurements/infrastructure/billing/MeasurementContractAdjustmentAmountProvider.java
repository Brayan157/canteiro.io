package com.renovar.canteiro.io.measurements.infrastructure.billing;

import com.renovar.canteiro.io.contracts.application.ContractMeasurementAdjustmentAmountProvider;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MeasurementContractAdjustmentAmountProvider implements ContractMeasurementAdjustmentAmountProvider {

    private final MeasurementContractAdjustmentRepository adjustmentRepository;

    @Override
    public BigDecimal approvedAmount(UUID companyId, UUID contractId) {
        return adjustmentRepository.sumByContractIdAndCompanyId(contractId, companyId);
    }
}
