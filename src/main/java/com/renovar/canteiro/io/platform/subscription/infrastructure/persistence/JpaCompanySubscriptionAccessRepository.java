package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCompanySubscriptionAccessRepository implements CompanySubscriptionAccessRepository {

    private final CompanySubscriptionAccessJpaRepository repository;
    private final CompanySubscriptionAccessPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public void lockCompanyId(UUID companyId) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
                )
                .setParameter("lockKey", "subscription-access:" + companyId)
                .getSingleResult();
    }

    @Override
    public CompanySubscriptionAccess save(CompanySubscriptionAccess access) {
        return repository.findById(access.getCompanyId())
                .map(entity -> {
                    mapper.updateJpaEntity(entity, access);
                    return mapper.toDomain(repository.save(entity));
                })
                .orElseGet(() -> mapper.toDomain(repository.save(mapper.toJpaEntity(access))));
    }

    @Override
    public Optional<CompanySubscriptionAccess> findByCompanyId(UUID companyId) {
        return repository.findById(companyId).map(mapper::toDomain);
    }

    @Override
    public List<CompanySubscriptionAccess> findAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }
}
