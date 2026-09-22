package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.application.FinalCustomerFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFinalCustomerRepository implements FinalCustomerRepository {

    private final FinalCustomerJpaRepository finalCustomerJpaRepository;
    private final FinalCustomerPersistenceMapper finalCustomerPersistenceMapper;

    @Override
    public FinalCustomer save(FinalCustomer finalCustomer) {
        if (finalCustomer.getId() == null) {
            return finalCustomerPersistenceMapper.toDomain(
                    finalCustomerJpaRepository.save(finalCustomerPersistenceMapper.toJpaEntity(finalCustomer))
            );
        }
        FinalCustomerJpaEntity entity = finalCustomerJpaRepository.findById(finalCustomer.getId())
                .orElseThrow(() -> new IllegalStateException("Final customer must exist before it can be updated"));
        finalCustomerPersistenceMapper.updateJpaEntity(entity, finalCustomer);
        return finalCustomerPersistenceMapper.toDomain(finalCustomerJpaRepository.save(entity));
    }

    @Override
    public Optional<FinalCustomer> findByIdAndCompanyId(UUID id, UUID companyId) {
        return finalCustomerJpaRepository.findByIdAndCompanyId(id, companyId)
                .map(finalCustomerPersistenceMapper::toDomain);
    }

    @Override
    public Optional<FinalCustomer> findByCompanyIdAndDocument(UUID companyId, String document) {
        return finalCustomerJpaRepository.findByCompanyIdAndDocument(companyId, document.replaceAll("\\D", ""))
                .map(finalCustomerPersistenceMapper::toDomain);
    }

    @Override
    public Page<FinalCustomer> findByCompanyId(UUID companyId, FinalCustomerFilter filter, Pageable pageable) {
        return finalCustomerJpaRepository.findAll(specification(companyId, filter), pageable)
                .map(finalCustomerPersistenceMapper::toDomain);
    }

    private Specification<FinalCustomerJpaEntity> specification(UUID companyId, FinalCustomerFilter filter) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("companyId"), companyId);
            if (filter.normalizedName() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")), like(filter.normalizedName())));
            }
            if (filter.normalizedDocument() != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.like(root.get("document"), "%" + filter.normalizedDocument() + "%"));
            }
            if (filter.customerType() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("customerType"), filter.customerType()));
            }
            if (filter.active() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("active"), filter.active()));
            }
            return predicate;
        };
    }

    private String like(String value) {
        return "%" + value.toLowerCase(java.util.Locale.ROOT) + "%";
    }
}
