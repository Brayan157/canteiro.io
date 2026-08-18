package com.renovar.canteiro.io.contracts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ContractNetAmountCalculator {

    private ContractNetAmountCalculator() {
    }

    public static ContractNetAmount calculate(List<ContractService> services, ContractDiscount contractDiscount) {
        BigDecimal subtotal = services.stream()
                .filter(service -> service.getStatus() != ContractServiceStatus.CANCELLED)
                .map(ContractService::getNetAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = calculateDiscountAmount(subtotal, contractDiscount);
        BigDecimal netAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        if (netAmount.signum() < 0) {
            throw new IllegalArgumentException("Contract net amount must not be negative");
        }
        return new ContractNetAmount(subtotal, discountAmount, netAmount);
    }

    private static BigDecimal calculateDiscountAmount(BigDecimal subtotal, ContractDiscount contractDiscount) {
        if (contractDiscount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal amount = contractDiscount.getDiscountType() == DiscountType.FIXED
                ? contractDiscount.getDiscountValue()
                : subtotal.multiply(contractDiscount.getDiscountValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Contract discount must not exceed the service subtotal");
        }
        return amount;
    }
}
