package com.renovar.canteiro.io.customers.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import java.util.UUID;

public record FinalCustomerChangeResponse(FinalCustomerResponse customer, UUID changeRequestId, ChangeAuthorizationMode mode) { }
