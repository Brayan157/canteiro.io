package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record FinalCustomerChangeResult(FinalCustomer customer, ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
