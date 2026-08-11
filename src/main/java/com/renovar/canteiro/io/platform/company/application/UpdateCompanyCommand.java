package com.renovar.canteiro.io.platform.company.application;

public record UpdateCompanyCommand(
        String corporateName,
        String tradeName,
        String document,
        String email,
        String phone,
        String address,
        String logo
) {
}
