package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaContractDiscountRepository implements ContractDiscountRepository {

    private final ContractDiscountJpaRepository contractDiscountJpaRepository;

    @Override
    public ContractDiscount save(ContractDiscount contractDiscount) {
        ContractDiscountJpaEntity entity = contractDiscountJpaRepository.save(new ContractDiscountJpaEntity(
                contractDiscount.getCompanyId(), contractDiscount.getContractId(), contractDiscount.getDiscountType(),
                contractDiscount.getDiscountValue()
        ));
        return toDomain(entity);
    }

    @Override
    public Optional<ContractDiscount> findByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return contractDiscountJpaRepository.findByContractIdAndCompanyId(contractId, companyId).map(this::toDomain);
    }

    private ContractDiscount toDomain(ContractDiscountJpaEntity entity) {
        return ContractDiscount.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getDiscountType(),
                entity.getDiscountValue(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
