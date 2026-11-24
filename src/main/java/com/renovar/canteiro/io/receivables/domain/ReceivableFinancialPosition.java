package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;

/** Official title position; withholding and credits are never described as cash. */
public record ReceivableFinancialPosition(Status status, BigDecimal withheldAmount, BigDecimal creditedAmount,
        BigDecimal netReceivableAmount, BigDecimal confirmedSettlementAmount, BigDecimal outstandingAmount) {
    public enum Status { OPEN, PARTIALLY_RECEIVED, RECEIVED, SETTLED_WITH_ADJUSTMENTS, CANCELLED, RENEGOTIATED }

    public boolean receivedWithWithholding() {
        return status == Status.SETTLED_WITH_ADJUSTMENTS && outstandingAmount.signum() == 0
                && withheldAmount.signum() > 0 && creditedAmount.signum() == 0 && confirmedSettlementAmount.signum() > 0;
    }

    public static ReceivableFinancialPosition from(ReceivableStatus persisted, ReceivableBalance balance) {
        Status status;
        if (persisted == ReceivableStatus.CANCELLED) status = Status.CANCELLED;
        else if (persisted == ReceivableStatus.RENEGOTIATED) status = Status.RENEGOTIATED;
        else if (balance.outstandingAmount().signum() > 0) {
            status = balance.confirmedSettlementAmount().signum() > 0 ? Status.PARTIALLY_RECEIVED : Status.OPEN;
        } else if (balance.withheldAmount().signum() > 0 || balance.creditedAmount().signum() > 0) {
            status = Status.SETTLED_WITH_ADJUSTMENTS;
        } else status = Status.RECEIVED;
        return new ReceivableFinancialPosition(status, balance.withheldAmount(), balance.creditedAmount(),
                balance.netReceivableAmount(), balance.confirmedSettlementAmount(), balance.outstandingAmount());
    }
}
