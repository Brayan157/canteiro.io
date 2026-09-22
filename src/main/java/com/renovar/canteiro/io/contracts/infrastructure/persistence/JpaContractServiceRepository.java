package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaContractServiceRepository implements ContractServiceRepository {

    private final ContractServiceJpaRepository contractServiceJpaRepository;

    @Override
    public ContractService save(ContractService contractService) {
        ContractServiceJpaEntity entity;
        if (contractService.getId() == null) {
            entity = new ContractServiceJpaEntity(contractService.getCompanyId(), contractService.getContractId(),
                    contractService.getSourceServiceTemplateId(), contractService.getName(), contractService.getDescription(),
                    contractService.getStatus(), contractService.getQuantity(), contractService.getUnitPrice(),
                    contractService.getGrossAmount(), contractService.getDiscountType(), contractService.getDiscountValue(),
                    contractService.getDiscountAmount(), contractService.getNetAmount());
        } else {
            entity = contractServiceJpaRepository.findByIdAndCompanyId(contractService.getId(), contractService.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("Contract service does not exist"));
            entity.update(contractService.getName(), contractService.getDescription(), contractService.getStatus(),
                    contractService.getQuantity(), contractService.getUnitPrice(), contractService.getGrossAmount(),
                    contractService.getDiscountType(), contractService.getDiscountValue(), contractService.getDiscountAmount(),
                    contractService.getNetAmount());
        }
        return toDomain(contractServiceJpaRepository.save(entity));
    }

    @Override
    public List<ContractService> findByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return contractServiceJpaRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ContractService> findByIdAndCompanyId(UUID id, UUID companyId) {
        return contractServiceJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    private ContractService toDomain(ContractServiceJpaEntity entity) {
        return ContractService.rehydrate(entity.getId(), entity.getCompanyId(), entity.getContractId(),
                entity.getServiceTemplateId(), entity.getName(), entity.getDescription(), entity.getStatus(),
                entity.getQuantity(), entity.getUnitPrice(), entity.getGrossAmount(), entity.getDiscountType(),
                entity.getDiscountValue(), entity.getDiscountAmount(), entity.getNetAmount(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
