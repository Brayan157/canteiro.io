package com.renovar.canteiro.io.platform.company.application;

import java.util.List;
import java.util.UUID;

public record CompanyOnboardingCommand(
        String corporateName,
        String tradeName,
        String document,
        String email,
        String phone,
        String address,
        String logo,
        String ownerEmail,
        List<UUID> planIds
) {
}
