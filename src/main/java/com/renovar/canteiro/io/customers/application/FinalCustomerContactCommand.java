package com.renovar.canteiro.io.customers.application;

public record FinalCustomerContactCommand(String name, String email, String phone, boolean primaryContact,
                                          String justification) {
}
