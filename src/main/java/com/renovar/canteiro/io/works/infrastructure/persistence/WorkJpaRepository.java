package com.renovar.canteiro.io.works.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
interface WorkJpaRepository extends JpaRepository<WorkJpaEntity, UUID> { Optional<WorkJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId); }
