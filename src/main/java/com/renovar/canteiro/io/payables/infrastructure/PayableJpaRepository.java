package com.renovar.canteiro.io.payables.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PayableJpaRepository extends JpaRepository<PayableJpaEntity, UUID> {

    Optional<PayableJpaEntity> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId);
}
