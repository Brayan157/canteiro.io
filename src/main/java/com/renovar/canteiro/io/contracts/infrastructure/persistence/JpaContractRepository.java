package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaContractRepository implements ContractRepository {

    private final ContractJpaRepository contractJpaRepository;

    @Override
    public Contract save(Contract contract) {
        ContractJpaEntity entity = new ContractJpaEntity(
                contract.getCompanyId(), contract.getWorkId(), contract.getReference(), contract.getName(),
                contract.getStatus(), contract.getStartedOn(), contract.getExpectedCompletionOn(), contract.getCompletedOn()
        );
        return toDomain(contractJpaRepository.save(entity));
    }

    @Override
    public Optional<Contract> findByIdAndCompanyId(UUID id, UUID companyId) {
        return contractJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Contract> findByIdAndCompanyIdWithBillingLock(UUID id, UUID companyId) {
        return contractJpaRepository.findByIdAndCompanyIdWithBillingLock(id, companyId).map(this::toDomain);
    }

    @Override
    public Page<Contract> findByCompanyId(UUID companyId, Pageable pageable) {
        return contractJpaRepository.findByCompanyId(companyId, pageable).map(this::toDomain);
    }

    @Override
    public Page<Contract> findBillingControl(UUID companyId,
                                             com.renovar.canteiro.io.contracts.domain.ContractBillingControlFilter filter,
                                             Pageable pageable) {
        Specification<ContractJpaEntity> specification = (root, query, builder) -> builder.equal(root.get("companyId"), companyId);
        if (filter.contractId() != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("id"), filter.contractId()));
        if (filter.status() != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), filter.status()));
        if (filter.normalizedQuery() != null) {
            String pattern = "%" + filter.normalizedQuery() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("reference")), pattern)
            ));
        }
        return contractJpaRepository.findAll(specification, pageable).map(this::toDomain);
    }

    private Contract toDomain(ContractJpaEntity entity) {
        return Contract.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getWorkId(), entity.getReference(), entity.getName(),
                entity.getStatus(), entity.getStartedOn(), entity.getExpectedCompletionOn(), entity.getCompletedOn(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
