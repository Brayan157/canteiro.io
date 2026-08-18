package com.renovar.canteiro.io.employees.application;

public record CreateEmployeeCommand(String fullName, String jobTitle, String phone, String justification) {
}
