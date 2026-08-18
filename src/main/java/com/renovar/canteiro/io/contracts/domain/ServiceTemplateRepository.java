package com.renovar.canteiro.io.contracts.domain;

import java.util.Optional;
import java.util.UUID;

public interface ServiceTemplateRepository {

    ServiceTemplate save(ServiceTemplate serviceTemplate);

    Optional<ServiceTemplate> findByIdAndCompanyId(UUID id, UUID companyId);
}
