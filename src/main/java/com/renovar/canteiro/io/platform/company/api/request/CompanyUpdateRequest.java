package com.renovar.canteiro.io.platform.company.api.request;

public record CompanyUpdateRequest(
        String corporateName,
        String tradeName,
        String document,
        String email,
        String phone,
        String address,
        String logo
) {
}
