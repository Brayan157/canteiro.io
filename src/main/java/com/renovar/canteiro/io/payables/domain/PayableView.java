package com.renovar.canteiro.io.payables.domain;

import java.util.Set;

public enum PayableView {
    OUTSTANDING(Set.of(PayableStatus.OPEN, PayableStatus.OVERDUE, PayableStatus.PARTIALLY_PAID)),
    HISTORY(Set.of(PayableStatus.PAID, PayableStatus.CANCELLED, PayableStatus.RENEGOTIATED, PayableStatus.DRAFT));

    private final Set<PayableStatus> statuses;

    PayableView(Set<PayableStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<PayableStatus> statuses() {
        return statuses;
    }
}
