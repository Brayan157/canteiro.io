package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;

import java.util.List;

public record InvoiceRegistrationResult(
        Invoice invoice,
        List<InvoiceItem> items,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode mode
) {
}
