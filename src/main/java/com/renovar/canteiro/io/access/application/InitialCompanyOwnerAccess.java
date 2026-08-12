package com.renovar.canteiro.io.access.application;

import java.util.List;
import java.util.UUID;

public record InitialCompanyOwnerAccess(
        UUID roleId,
        String roleName,
        List<String> permissionCodes
) {

    public InitialCompanyOwnerAccess {
        permissionCodes = List.copyOf(permissionCodes);
    }
}
