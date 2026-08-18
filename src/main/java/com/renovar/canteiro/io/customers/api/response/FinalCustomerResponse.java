package com.renovar.canteiro.io.customers.api.response;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import java.util.UUID;

public record FinalCustomerResponse(UUID id, FinalCustomerType customerType, String name, String document, boolean active) { }
