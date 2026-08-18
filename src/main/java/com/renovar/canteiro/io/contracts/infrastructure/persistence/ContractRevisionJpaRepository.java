package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface ContractRevisionJpaRepository extends JpaRepository<ContractRevisionJpaEntity, UUID> {

    @Query("select coalesce(max(r.revisionNumber), 0) from ContractRevisionJpaEntity r "
            + "where r.contractId = :contractId and r.companyId = :companyId")
    int maxRevisionNumber(@Param("contractId") UUID contractId, @Param("companyId") UUID companyId);
}
