package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;

public record UpdateFinalCustomerCommand(
        FinalCustomerType customerType,
        String name,
        String document,
        String justification
) {
}
