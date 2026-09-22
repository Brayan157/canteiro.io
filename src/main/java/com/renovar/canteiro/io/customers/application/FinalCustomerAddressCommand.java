package com.renovar.canteiro.io.customers.application;

public record FinalCustomerAddressCommand(String label, String postalCode, String street, String number,
                                          String complement, String district, String city, String state, String country,
                                          boolean primaryAddress, String justification) {
}
