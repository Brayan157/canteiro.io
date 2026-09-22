package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record ContractServiceCommand(UUID sourceServiceTemplateId, String name, String description, ContractServiceStatus status, BigDecimal quantity,
                                     BigDecimal unitPrice, DiscountType discountType, BigDecimal discountValue,
                                     String justification) {
}
