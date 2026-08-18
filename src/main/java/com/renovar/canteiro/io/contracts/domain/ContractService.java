package com.renovar.canteiro.io.contracts.domain;

import lombok.Getter;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter
public final class ContractService {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final UUID sourceServiceTemplateId;
    private final String name;
    private final String description;
    private final ContractServiceStatus status;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal grossAmount;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal discountAmount;
    private final BigDecimal netAmount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ContractService(UUID id, UUID companyId, UUID contractId, UUID sourceServiceTemplateId, String name,
                            String description, ContractServiceStatus status, BigDecimal quantity, BigDecimal unitPrice,
                            BigDecimal grossAmount, DiscountType discountType, BigDecimal discountValue,
                            BigDecimal discountAmount, BigDecimal netAmount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Contract service company is required");
        this.contractId = require(contractId, "Contract service contract is required");
        this.sourceServiceTemplateId = sourceServiceTemplateId;
        this.name = requireText(name, "Contract service name is required");
        this.description = normalize(description);
        this.status = require(status, "Contract service status is required");
        this.quantity = requirePositive(quantity, "Contract service quantity must be positive", 4);
        this.unitPrice = requireNonNegative(unitPrice, "Contract service unit price must not be negative", 2);
        this.grossAmount = calculateGrossAmount(this.quantity, this.unitPrice);
        if (grossAmount != null && this.grossAmount.compareTo(requireNonNegative(grossAmount,
                "Contract service gross amount must not be negative", 2)) != 0) {
            throw new IllegalArgumentException("Contract service gross amount must equal quantity times unit price");
        }
        this.discountType = discountType;
        this.discountValue = discountType == null ? null : requireDiscountValue(discountType, discountValue);
        this.discountAmount = calculateDiscountAmount(this.grossAmount, this.discountType, this.discountValue);
        this.netAmount = this.grossAmount.subtract(this.discountAmount).setScale(2, RoundingMode.HALF_UP);
        if (discountAmount != null && this.discountAmount.compareTo(requireNonNegative(discountAmount,
                "Contract service discount amount must not be negative", 2)) != 0) {
            throw new IllegalArgumentException("Contract service discount amount is inconsistent");
        }
        if (netAmount != null && this.netAmount.compareTo(requireNonNegative(netAmount,
                "Contract service net amount must not be negative", 2)) != 0) {
            throw new IllegalArgumentException("Contract service net amount is inconsistent");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ContractService create(UUID companyId, UUID contractId, String name, String description,
                                         ContractServiceStatus status, BigDecimal quantity, BigDecimal unitPrice) {
        return new ContractService(null, companyId, contractId, null, name, description, status, quantity, unitPrice,
                null, null, null, null, null, null, null);
    }

    public static ContractService create(UUID companyId, UUID contractId, String name, String description,
                                         ContractServiceStatus status, BigDecimal quantity, BigDecimal unitPrice,
                                         DiscountType discountType, BigDecimal discountValue) {
        return new ContractService(null, companyId, contractId, null, name, description, status, quantity, unitPrice,
                null, discountType, discountValue, null, null, null, null);
    }

    public static ContractService copyOf(UUID companyId, UUID contractId, ServiceTemplate template) {
        return new ContractService(null, companyId, contractId, template.getId(), template.getName(),
                template.getDescription(), ContractServiceStatus.DRAFT, BigDecimal.ONE, BigDecimal.ZERO, null, null, null,
                null, null, null, null);
    }

    public static ContractService rehydrate(UUID id, UUID companyId, UUID contractId, UUID sourceServiceTemplateId,
                                            String name, String description, ContractServiceStatus status, BigDecimal quantity,
                                            BigDecimal unitPrice, BigDecimal grossAmount, DiscountType discountType,
                                            BigDecimal discountValue, BigDecimal discountAmount, BigDecimal netAmount,
                                            Instant createdAt, Instant updatedAt) {
        return new ContractService(id, companyId, contractId, sourceServiceTemplateId, name, description, status, quantity,
                unitPrice, grossAmount, discountType, discountValue, discountAmount, netAmount, createdAt, updatedAt);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String message, int scale) {
        BigDecimal normalized = require(value, message).setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message, int scale) {
        BigDecimal normalized = require(value, message).setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static BigDecimal calculateGrossAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal requireDiscountValue(DiscountType type, BigDecimal value) {
        BigDecimal normalized = require(value, "Contract service discount value is required");
        if (type == DiscountType.FIXED) {
            return requirePositive(normalized, "Contract service fixed discount must be positive", 2);
        }
        BigDecimal percentage = requirePositive(normalized, "Contract service percentage discount must be positive", 4);
        if (percentage.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new IllegalArgumentException("Contract service percentage discount must not exceed 100");
        }
        return percentage;
    }

    private static BigDecimal calculateDiscountAmount(BigDecimal grossAmount, DiscountType type, BigDecimal value) {
        if (type == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal amount = type == DiscountType.FIXED
                ? value
                : grossAmount.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (amount.compareTo(grossAmount) > 0) {
            throw new IllegalArgumentException("Contract service discount must not exceed gross amount");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
