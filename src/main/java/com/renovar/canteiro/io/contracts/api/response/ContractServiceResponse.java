package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record ContractServiceResponse(
        UUID id,
        UUID contractId,
        UUID sourceServiceTemplateId,
        String name,
        String description,
        ContractServiceStatus status,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        BigDecimal netAmount
) {
}
