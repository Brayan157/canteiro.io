package com.renovar.canteiro.io.works.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
interface WorkJpaRepository extends JpaRepository<WorkJpaEntity, UUID>, JpaSpecificationExecutor<WorkJpaEntity> {
    Optional<WorkJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
