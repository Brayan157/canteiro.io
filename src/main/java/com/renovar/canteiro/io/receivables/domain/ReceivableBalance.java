package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableBalance(
        UUID receivableId,
        BigDecimal originalAmount,
        BigDecimal withheldAmount,
        BigDecimal confirmedSettlementAmount
) {

    public ReceivableBalance(UUID receivableId, BigDecimal originalAmount, BigDecimal confirmedSettlementAmount) {
        this(receivableId, originalAmount, BigDecimal.ZERO, confirmedSettlementAmount);
    }

    public ReceivableBalance {
        require(receivableId, "Receivable is required");
        originalAmount = positive(originalAmount, "Receivable original amount is required");
        withheldAmount = nonNegative(withheldAmount, "Withheld amount is required");
        confirmedSettlementAmount = nonNegative(confirmedSettlementAmount, "Confirmed settlement amount is required");
        if (withheldAmount.compareTo(originalAmount) > 0) {
            throw new IllegalArgumentException("Withheld amount must not exceed receivable original amount");
        }
        if (confirmedSettlementAmount.compareTo(originalAmount.subtract(withheldAmount)) > 0) {
            throw new IllegalArgumentException("Confirmed settlement amount must not exceed receivable net amount");
        }
    }

    public BigDecimal netReceivableAmount() {
        return originalAmount.subtract(withheldAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal outstandingAmount() {
        return netReceivableAmount().subtract(confirmedSettlementAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean canSettle(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.setScale(2, RoundingMode.HALF_UP)
                .compareTo(outstandingAmount()) <= 0;
    }

    public ReceivableBalance afterSettlement(BigDecimal amount) {
        if (!canSettle(amount)) {
            throw new IllegalArgumentException("Receivable settlement exceeds the outstanding amount");
        }
        return new ReceivableBalance(receivableId, originalAmount, withheldAmount, confirmedSettlementAmount.add(amount));
    }

    public ReceivableStatus statusOn(Receivable receivable, LocalDate currentDate) {
        require(receivable, "Receivable is required");
        require(currentDate, "Current date is required");
        if (receivable.getStatus() == ReceivableStatus.CANCELLED || receivable.getStatus() == ReceivableStatus.RENEGOTIATED) {
            return receivable.getStatus();
        }
        if (outstandingAmount().signum() == 0) {
            return ReceivableStatus.RECEIVED;
        }
        if (currentDate.isAfter(receivable.getDueOn())) {
            return ReceivableStatus.OVERDUE;
        }
        return confirmedSettlementAmount.signum() > 0
                ? ReceivableStatus.PARTIALLY_RECEIVED
                : ReceivableStatus.OPEN;
    }

    private static BigDecimal positive(BigDecimal value, String message) {
        BigDecimal normalized = require(value, message).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String message) {
        BigDecimal normalized = require(value, message).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(message);
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
