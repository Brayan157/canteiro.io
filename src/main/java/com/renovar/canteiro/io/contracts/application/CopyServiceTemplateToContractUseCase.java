package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplate;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CopyServiceTemplateToContractUseCase {

    private final ServiceTemplateRepository serviceTemplateRepository;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;

    @Transactional
    public ContractService copy(UUID companyId, UUID contractId, UUID serviceTemplateId) {
        contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        ServiceTemplate template = serviceTemplateRepository.findByIdAndCompanyId(serviceTemplateId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Service template"));
        if (!template.isActive()) {
            throw new IllegalStateException("An inactive service template cannot be copied");
        }
        return contractServiceRepository.save(ContractService.copyOf(companyId, contractId, template));
    }
}
