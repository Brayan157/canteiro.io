package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;

import java.util.List;

public record CreateFinalCustomerCommand(
        FinalCustomerType customerType, String name, String document, List<Contact> contacts, List<Address> addresses,
        String justification
) {
    public record Contact(String name, String email, String phone, boolean primaryContact) {
    }

    public record Address(String label, String postalCode, String street, String number, String complement, String district,
                          String city, String state, String country, boolean primaryAddress) {
    }
}
