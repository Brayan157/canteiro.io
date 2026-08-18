package com.renovar.canteiro.io.customers.api.request;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateFinalCustomerRequest(@NotNull FinalCustomerType customerType, @NotBlank String name, @NotBlank String document,
                                         List<@Valid ContactRequest> contacts, List<@Valid AddressRequest> addresses, String justification) {
    public record ContactRequest(@NotBlank String name, String email, String phone, boolean primaryContact) { }
    public record AddressRequest(String label, String postalCode, @NotBlank String street, String number, String complement,
                                 String district, @NotBlank String city, @NotBlank String state, String country, boolean primaryAddress) { }
}
