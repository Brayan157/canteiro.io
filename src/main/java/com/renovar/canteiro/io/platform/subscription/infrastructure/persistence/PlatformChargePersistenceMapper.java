package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import org.springframework.stereotype.Component;

@Component
public class PlatformChargePersistenceMapper {

    public PlatformChargeJpaEntity toJpaEntity(PlatformCharge charge) {
        return new PlatformChargeJpaEntity(
                charge.getCompanyId(), charge.getSubscriptionId(), charge.getProvider().value(),
                charge.getIdempotencyKey(), charge.getExternalCustomerId(), charge.getExternalChargeId(),
                charge.getBillingMethod(), charge.getAmount(), charge.getDueDate(), charge.getStatus()
        );
    }

    public PlatformCharge toDomain(PlatformChargeJpaEntity entity) {
        return PlatformCharge.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getSubscriptionId(),
                new PaymentGatewayProviderCode(entity.getProvider()),
                entity.getIdempotencyKey(), entity.getExternalCustomerId(), entity.getExternalChargeId(),
                entity.getBillingMethod(), entity.getAmount(), entity.getDueDate(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getLastGatewayEventAt()
        );
    }
}
