package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.application.ContractFilter;
import com.renovar.canteiro.io.customers.infrastructure.persistence.FinalCustomerJpaEntity;
import com.renovar.canteiro.io.works.infrastructure.persistence.WorkJpaEntity;
import jakarta.persistence.criteria.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaContractRepository implements ContractRepository {

    private final ContractJpaRepository contractJpaRepository;

    @Override
    public Contract save(Contract contract) {
        if (contract.getId() != null) {
            ContractJpaEntity entity = contractJpaRepository.findById(contract.getId())
                    .orElseThrow(() -> new IllegalStateException("Contract must exist before it can be updated"));
            entity.getCurrency().requireSame(contract.getCurrency());
            String reference = Objects.equals(Contract.normalizeReference(entity.getReference()), contract.getReference())
                    ? entity.getReference() : contract.getReference();
            entity.updateProfile(contract.getContractIdentifier(), reference, contract.getName(), contract.getStatus(), contract.getStartedOn(),
                    contract.getExpectedCompletionOn(), contract.getCompletedOn());
            return toDomain(contractJpaRepository.save(entity));
        }
        ContractJpaEntity entity = new ContractJpaEntity(contract.getCompanyId(), contract.getWorkId(), contract.getContractIdentifier(), contract.getPricingMode(), contract.getReference(),
                contract.getName(), contract.getStatus(), contract.getStartedOn(), contract.getExpectedCompletionOn(), contract.getCompletedOn(), contract.getCurrency());
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
    public Optional<Contract> findByIdAndCompanyIdWithCancellationCheckLock(UUID id, UUID companyId) {
        return contractJpaRepository.findByIdAndCompanyIdWithCancellationCheckLock(id, companyId).map(this::toDomain);
    }

    @Override
    public boolean hasConflictingReferenceUnderWorkLock(UUID companyId, UUID workId, UUID contractId,
                                                        String previousReference, String reference) {
        // Separate statements let the duplicate check see commits made by the previous lock holder.
        contractJpaRepository.lockWorkForReference(companyId, workId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Work"));
        return contractJpaRepository.existsConflictingReference(companyId, workId, contractId, previousReference, reference);
    }

    @Override
    public Page<Contract> findByCompanyId(UUID companyId, Pageable pageable) {
        return contractJpaRepository.findByCompanyId(companyId, pageable).map(this::toDomain);
    }

    @Override
    public Page<Contract> findByCompanyId(UUID companyId, ContractFilter filter, Pageable pageable) {
        Specification<ContractJpaEntity> specification = (root, query, builder) -> builder.equal(root.get("companyId"), companyId);
        if (filter.normalizedName() != null) specification = specification.and((root, query, builder) ->
                builder.like(builder.lower(root.<String>get("name")), like(filter.normalizedName())));
        if (filter.normalizedReference() != null) specification = specification.and((root, query, builder) ->
                builder.like(builder.lower(root.<String>get("reference")), like(filter.normalizedReference())));
        if (filter.workId() != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("workId"), filter.workId()));
        if (filter.normalizedWorkName() != null || filter.normalizedFinalCustomerName() != null) {
            specification = specification.and((root, query, builder) -> {
                var works = query.subquery(UUID.class);
                var work = works.from(WorkJpaEntity.class);
                var workPredicate = builder.equal(work.get("companyId"), companyId);
                if (filter.normalizedWorkName() != null) {
                    workPredicate = builder.and(workPredicate, builder.like(
                            builder.lower(work.<String>get("name")), like(filter.normalizedWorkName())));
                }
                if (filter.normalizedFinalCustomerName() != null) {
                    var customers = query.subquery(UUID.class);
                    var customer = customers.from(FinalCustomerJpaEntity.class);
                    customers.select(customer.<UUID>get("id")).where(builder.and(
                            builder.equal(customer.get("companyId"), companyId),
                            builder.like(builder.lower(customer.<String>get("name")), like(filter.normalizedFinalCustomerName()))
                    ));
                    workPredicate = builder.and(workPredicate, work.<UUID>get("finalCustomerId").in(customers));
                }
                works.select(work.<UUID>get("id")).where(workPredicate);
                return root.<UUID>get("workId").in(works);
            });
        }
        if (filter.status() != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), filter.status()));
        if (filter.startedFrom() != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("startedOn"), filter.startedFrom()));
        if (filter.startedTo() != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("startedOn"), filter.startedTo()));
        if (filter.expectedCompletionFrom() != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("expectedCompletionOn"), filter.expectedCompletionFrom()));
        if (filter.expectedCompletionTo() != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("expectedCompletionOn"), filter.expectedCompletionTo()));
        if (filter.completedFrom() != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("completedOn"), filter.completedFrom()));
        if (filter.completedTo() != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("completedOn"), filter.completedTo()));
        return findPage(specification, pageable);
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
        return findPage(specification, pageable);
    }

    private Page<Contract> findPage(Specification<ContractJpaEntity> filters, Pageable pageable) {
        Sort.Order priority = pageable.getSort().getOrderFor("openFirst");
        if (priority == null) {
            return contractJpaRepository.findAll(filters, pageable).map(this::toDomain);
        }

        Sort secondarySort = Sort.by(pageable.getSort().stream()
                .filter(order -> !order.getProperty().equals("openFirst")).toList());
        if (secondarySort.getOrderFor("createdAt") == null) {
            secondarySort = secondarySort.and(Sort.by(Sort.Order.desc("createdAt")));
        }
        Sort withinGroup = secondarySort.and(Sort.by("id"));
        Specification<ContractJpaEntity> ordered = filters.and((root, query, builder) -> {
            if (query.getResultType() == ContractJpaEntity.class) {
                var rank = builder.<Integer>selectCase()
                        .when(root.get("status").in(ContractStatus.OPEN, ContractStatus.ACTIVE), 0)
                        .otherwise(1);
                var orders = new ArrayList<Order>();
                orders.add(priority.isAscending() ? builder.asc(rank) : builder.desc(rank));
                orders.addAll(QueryUtils.toOrders(withinGroup, root, builder));
                query.orderBy(orders);
            }
            return null;
        });
        // The CASE expression lives in the criteria query, before OFFSET/LIMIT.
        var slice = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return contractJpaRepository.findAll(ordered, slice).map(this::toDomain);
    }

    private Contract toDomain(ContractJpaEntity entity) {
        return Contract.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getWorkId(), entity.getContractIdentifier(), entity.getPricingMode(), entity.getReference(), entity.getName(),
                entity.getStatus(), entity.getStartedOn(), entity.getExpectedCompletionOn(), entity.getCompletedOn(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCurrency()
        );
    }

    private String like(String value) {
        return "%" + value.toLowerCase(java.util.Locale.ROOT) + "%";
    }
}
