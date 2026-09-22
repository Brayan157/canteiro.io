package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;

public record TaxObligationCreationResult(
        TaxObligation taxObligation,
        Payable payable,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode mode
) {
}
