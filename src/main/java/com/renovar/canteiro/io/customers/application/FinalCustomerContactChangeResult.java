package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContact;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record FinalCustomerContactChangeResult(FinalCustomerContact contact, ChangeRequest changeRequest,
                                               ChangeAuthorizationMode mode) {
}
