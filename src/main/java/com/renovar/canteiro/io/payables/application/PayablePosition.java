package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayablePosition(Payable payable, BigDecimal paidAmount, BigDecimal outstandingAmount,
                              BigDecimal overdueAmount, LocalDate asOf) {
    public static PayablePosition calculate(Payable payable, BigDecimal netPaid, LocalDate today) {
        var status = payable.getStatus();
        BigDecimal original = payable.getOriginalAmount();
        boolean consistent = switch (status) {
            case OPEN, OVERDUE, DRAFT, CANCELLED -> netPaid.signum() == 0;
            case PARTIALLY_PAID -> netPaid.signum() > 0 && netPaid.compareTo(original) < 0;
            case PAID -> netPaid.compareTo(original) == 0;
            case RENEGOTIATED -> false;
        };
        if (!consistent) return new PayablePosition(payable, null, null, null, today);
        BigDecimal outstanding = payable.acceptsSettlements() ? original.subtract(netPaid) : BigDecimal.ZERO;
        BigDecimal overdue = payable.getDueOn().isBefore(today) ? outstanding : BigDecimal.ZERO;
        return new PayablePosition(payable, netPaid, outstanding, overdue, today);
    }

    public boolean available() {
        return paidAmount != null;
    }
}
