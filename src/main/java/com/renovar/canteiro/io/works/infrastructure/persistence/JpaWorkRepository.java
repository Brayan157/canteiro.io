package com.renovar.canteiro.io.works.infrastructure.persistence;
import com.renovar.canteiro.io.customers.infrastructure.persistence.FinalCustomerJpaEntity;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.application.WorkFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;
import java.util.UUID;
@Repository @RequiredArgsConstructor
public class JpaWorkRepository implements WorkRepository {
    private final WorkJpaRepository repository;
    public Work save(Work work) {
        if (work.getId() == null) {
            WorkJpaEntity created = new WorkJpaEntity(work.getCompanyId(), work.getFinalCustomerId(), work.getName(), work.getReference(),
                    work.getExecutionLocationType(), work.getExecutionAddress(), work.getStatus(), work.getStartedOn(),
                    work.getExpectedCompletionOn(), work.getCompletedOn());
            return map(repository.save(created));
        }
        WorkJpaEntity existing = repository.findById(work.getId())
                .orElseThrow(() -> new IllegalStateException("Work must exist before it can be updated"));
        existing.updateProfile(work.getName(), work.getReference(), work.getExecutionLocationType(), work.getExecutionAddress(),
                work.getStatus(), work.getStartedOn(), work.getExpectedCompletionOn(), work.getCompletedOn());
        return map(repository.save(existing));
    }
    public Optional<Work> findByIdAndCompanyId(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).map(this::map); }
    public Page<Work> findByCompanyId(UUID companyId, WorkFilter filter, Pageable pageable) {
        return repository.findAll(specification(companyId, filter), pageable).map(this::map);
    }
    private Specification<WorkJpaEntity> specification(UUID companyId, WorkFilter filter) {
        return (root, query, builder) -> {
            var predicate = builder.equal(root.get("companyId"), companyId);
            if (filter.normalizedName() != null) predicate = builder.and(predicate, builder.like(builder.lower(root.<String>get("name")), like(filter.normalizedName())));
            if (filter.normalizedReference() != null) predicate = builder.and(predicate, builder.like(builder.lower(root.<String>get("reference")), like(filter.normalizedReference())));
            if (filter.finalCustomerId() != null) predicate = builder.and(predicate, builder.equal(root.get("finalCustomerId"), filter.finalCustomerId()));
            if (filter.normalizedFinalCustomerName() != null || filter.normalizedFinalCustomerDocument() != null) {
                var customers = query.subquery(UUID.class);
                var customer = customers.from(FinalCustomerJpaEntity.class);
                var customerPredicate = builder.equal(customer.get("companyId"), companyId);
                if (filter.normalizedFinalCustomerName() != null) {
                    customerPredicate = builder.and(customerPredicate, builder.like(
                            builder.lower(customer.<String>get("name")), like(filter.normalizedFinalCustomerName())));
                }
                if (filter.normalizedFinalCustomerDocument() != null) {
                    customerPredicate = builder.and(customerPredicate, builder.like(
                            customer.<String>get("document"), "%" + filter.normalizedFinalCustomerDocument() + "%"));
                }
                customers.select(customer.<UUID>get("id")).where(customerPredicate);
                predicate = builder.and(predicate, root.<UUID>get("finalCustomerId").in(customers));
            }
            if (filter.status() != null) predicate = builder.and(predicate, builder.equal(root.get("status"), filter.status()));
            if (filter.executionLocationType() != null) predicate = builder.and(predicate, builder.equal(root.get("executionLocationType"), filter.executionLocationType()));
            if (filter.startedFrom() != null) predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("startedOn"), filter.startedFrom()));
            if (filter.startedTo() != null) predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("startedOn"), filter.startedTo()));
            if (filter.expectedCompletionFrom() != null) predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("expectedCompletionOn"), filter.expectedCompletionFrom()));
            if (filter.expectedCompletionTo() != null) predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("expectedCompletionOn"), filter.expectedCompletionTo()));
            return predicate;
        };
    }
    private String like(String value) { return "%" + value.toLowerCase(java.util.Locale.ROOT) + "%"; }
    private Work map(WorkJpaEntity e) { return Work.rehydrate(e.getId(), e.getCompanyId(), e.getFinalCustomerId(), e.getName(), e.getReference(), e.getExecutionLocationType(), e.getExecutionAddress(), e.getStatus(), e.getStartedOn(), e.getExpectedCompletionOn(), e.getCompletedOn(), e.getCreatedAt(), e.getUpdatedAt()); }
}
