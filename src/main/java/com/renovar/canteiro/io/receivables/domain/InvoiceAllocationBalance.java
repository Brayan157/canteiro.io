package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record InvoiceAllocationBalance(
        UUID billingAllocationId,
        BigDecimal allocatedAmount,
        BigDecimal registeredInvoiceAmount
) {

    public InvoiceAllocationBalance {
        require(billingAllocationId, "Billing allocation is required");
        allocatedAmount = normalize(allocatedAmount, "Billing allocation amount is required");
        registeredInvoiceAmount = normalize(registeredInvoiceAmount, "Registered invoice amount is required");
    }

    public static InvoiceAllocationBalance from(BillingAllocation allocation, BigDecimal registeredInvoiceAmount) {
        require(allocation, "Billing allocation is required");
        return new InvoiceAllocationBalance(
                allocation.getId(), allocation.getAllocatedAmount(), registeredInvoiceAmount
        );
    }

    public BigDecimal availableInvoiceAmount() {
        return allocatedAmount.subtract(registeredInvoiceAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean canRegister(BigDecimal amount) {
        BigDecimal normalized = normalize(amount, "Invoice item amount is required");
        if (normalized.signum() == 0 || normalized.signum() != allocatedAmount.signum()) {
            return false;
        }
        BigDecimal resultingAmount = registeredInvoiceAmount.add(normalized);
        return normalized.signum() > 0
                ? resultingAmount.compareTo(allocatedAmount) <= 0
                : resultingAmount.compareTo(allocatedAmount) >= 0;
    }

    private static BigDecimal normalize(BigDecimal value, String message) {
        return require(value, message).setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
