package com.renovar.canteiro.io.platform.support.api;

import com.renovar.canteiro.io.platform.support.api.response.PlatformSupportUserResponse;
import com.renovar.canteiro.io.platform.support.application.PlatformSupportUser;
import org.springframework.stereotype.Component;

@Component
public class PlatformSupportApiMapper {

    public PlatformSupportUserResponse toResponse(PlatformSupportUser platformSupportUser) {
        return new PlatformSupportUserResponse(
                platformSupportUser.platformUser().getId(),
                platformSupportUser.user().getId(),
                platformSupportUser.user().getEmail(),
                platformSupportUser.platformUser().getGlobalRole(),
                platformSupportUser.user().getStatus(),
                platformSupportUser.platformUser().getCreatedAt()
        );
    }
}
