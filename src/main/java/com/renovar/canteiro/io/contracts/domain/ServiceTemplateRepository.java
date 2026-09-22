package com.renovar.canteiro.io.contracts.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTemplateRepository {

    ServiceTemplate save(ServiceTemplate serviceTemplate);

    Optional<ServiceTemplate> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ServiceTemplate> findByCompanyId(UUID companyId, boolean activeOnly);
}
