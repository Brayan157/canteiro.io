package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ServiceTemplate;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaServiceTemplateRepository implements ServiceTemplateRepository {

    private final ServiceTemplateJpaRepository serviceTemplateJpaRepository;

    @Override
    public ServiceTemplate save(ServiceTemplate serviceTemplate) {
        return toDomain(serviceTemplateJpaRepository.save(new ServiceTemplateJpaEntity(
                serviceTemplate.getCompanyId(), serviceTemplate.getName(), serviceTemplate.getDescription(),
                serviceTemplate.isActive()
        )));
    }

    @Override
    public Optional<ServiceTemplate> findByIdAndCompanyId(UUID id, UUID companyId) {
        return serviceTemplateJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    private ServiceTemplate toDomain(ServiceTemplateJpaEntity entity) {
        return ServiceTemplate.rehydrate(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getDescription(),
                entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
