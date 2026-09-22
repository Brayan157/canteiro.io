package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplate;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplateRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTemplateQueryService {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ServiceTemplateRepository serviceTemplateRepository;

    @Transactional(readOnly = true)
    public List<ServiceTemplate> findAll(boolean includeInactive) {
        accessAuthorizationService.requirePermission(AccessModule.SERVICES, AccessAction.READ);
        return serviceTemplateRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), !includeInactive);
    }
}
