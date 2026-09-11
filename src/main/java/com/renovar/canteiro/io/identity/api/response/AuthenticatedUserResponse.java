package com.renovar.canteiro.io.identity.api.response;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        String email,
        UserType userType,
        UserStatus status,
        AuthenticationContextType contextType,
        CompanySummary company,
        PlatformUserRole platformRole,
        List<RoleSummary> roles,
        List<String> permissions,
        AccessStatus access
) {

    public record CompanySummary(UUID id, String corporateName, String tradeName, String logoUrl, boolean active) {
    }

    public record RoleSummary(UUID id, String name) {
    }

    public record AccessStatus(SubscriptionAccessLevel level) {
    }
}
