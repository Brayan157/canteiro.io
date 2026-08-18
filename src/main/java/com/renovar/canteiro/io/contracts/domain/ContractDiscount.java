package com.renovar.canteiro.io.contracts.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class ContractDiscount {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ContractDiscount(UUID id, UUID companyId, UUID contractId, DiscountType discountType, BigDecimal discountValue,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Contract discount company is required");
        this.contractId = require(contractId, "Contract discount contract is required");
        this.discountType = require(discountType, "Contract discount type is required");
        this.discountValue = requireDiscountValue(discountType, discountValue);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ContractDiscount create(UUID companyId, UUID contractId, DiscountType discountType, BigDecimal discountValue) {
        return new ContractDiscount(null, companyId, contractId, discountType, discountValue, null, null);
    }

    public static ContractDiscount rehydrate(UUID id, UUID companyId, UUID contractId, DiscountType discountType,
                                             BigDecimal discountValue, Instant createdAt, Instant updatedAt) {
        return new ContractDiscount(id, companyId, contractId, discountType, discountValue, createdAt, updatedAt);
    }

    private static BigDecimal requireDiscountValue(DiscountType type, BigDecimal value) {
        BigDecimal normalized = require(value, "Contract discount value is required");
        int scale = type == DiscountType.FIXED ? 2 : 4;
        normalized = normalized.setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Contract discount value must be positive");
        }
        if (type == DiscountType.PERCENTAGE && normalized.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new IllegalArgumentException("Contract percentage discount must not exceed 100");
        }
        return normalized;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
