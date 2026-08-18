package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractRevision;
import com.renovar.canteiro.io.contracts.domain.ContractRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaContractRevisionRepository implements ContractRevisionRepository {

    private final ContractRevisionJpaRepository contractRevisionJpaRepository;

    @Override
    public ContractRevision save(ContractRevision revision) {
        ContractRevisionJpaEntity entity = contractRevisionJpaRepository.save(new ContractRevisionJpaEntity(
                revision.getCompanyId(), revision.getContractId(), revision.getRevisionNumber(),
                revision.getPreviousNetAmount(), revision.getProposedNetAmount(), revision.getApprovedBilledAmount(),
                revision.getReason()
        ));
        return ContractRevision.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getRevisionNumber(),
                entity.getPreviousNetAmount(), entity.getProposedNetAmount(), entity.getApprovedBilledAmount(),
                entity.getReason(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    @Override
    public int nextRevisionNumber(UUID contractId, UUID companyId) {
        return contractRevisionJpaRepository.maxRevisionNumber(contractId, companyId) + 1;
    }
}
