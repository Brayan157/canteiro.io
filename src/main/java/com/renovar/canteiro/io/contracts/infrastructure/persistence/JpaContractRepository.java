package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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

    private Contract toDomain(ContractJpaEntity entity) {
        return Contract.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getWorkId(), entity.getReference(), entity.getName(),
                entity.getStatus(), entity.getStartedOn(), entity.getExpectedCompletionOn(), entity.getCompletedOn(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
