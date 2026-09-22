package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddress;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record FinalCustomerAddressChangeResult(FinalCustomerAddress address, ChangeRequest changeRequest,
                                               ChangeAuthorizationMode mode) {
}
