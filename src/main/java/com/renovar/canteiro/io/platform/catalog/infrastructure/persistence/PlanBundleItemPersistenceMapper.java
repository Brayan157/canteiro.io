package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItem;
import org.springframework.stereotype.Component;

@Component
public class PlanBundleItemPersistenceMapper {

    public PlanBundleItemJpaEntity toJpaEntity(PlanBundleItem item) {
        return new PlanBundleItemJpaEntity(item.getPlanBundleId(), item.getPlanId(), item.isActive());
    }

    public void updateJpaEntity(PlanBundleItemJpaEntity entity, PlanBundleItem item) {
        entity.update(item.isActive());
    }

    public PlanBundleItem toDomain(PlanBundleItemJpaEntity entity) {
        return PlanBundleItem.rehydrate(
                entity.getId(),
                entity.getPlanBundleId(),
                entity.getPlanId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
