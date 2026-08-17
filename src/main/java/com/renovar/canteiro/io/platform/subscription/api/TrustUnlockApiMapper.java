package com.renovar.canteiro.io.platform.subscription.api;

import com.renovar.canteiro.io.platform.subscription.api.response.TrustUnlockResponse;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import org.springframework.stereotype.Component;

@Component
public class TrustUnlockApiMapper {

    public TrustUnlockResponse toResponse(TrustUnlock trustUnlock) {
        return new TrustUnlockResponse(
                trustUnlock.getId(), trustUnlock.getCompanyId(), trustUnlock.getChargeId(),
                trustUnlock.getGrantedByUserId(), trustUnlock.getReason(), trustUnlock.getStartsAt(),
                trustUnlock.getExpiresAt()
        );
    }
}
