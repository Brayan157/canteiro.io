package com.renovar.canteiro.io.employees.api.response;

import java.util.UUID;

public record EmployeeAddressResponse(UUID id, String label, String postalCode, String street, String number,
                                      String complement, String district, String city, String state, String country,
                                      boolean primaryAddress, boolean active) { }
