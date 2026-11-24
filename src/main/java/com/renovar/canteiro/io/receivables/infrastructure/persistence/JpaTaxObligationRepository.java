package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaTaxObligationRepository implements TaxObligationRepository {

    private final TaxObligationJpaRepository taxObligationJpaRepository;

    @Override
    public org.springframework.data.domain.Page<TaxObligation> findPageByInvoiceIdAndCompanyIdAndContractId(
            UUID invoiceId, UUID companyId, UUID contractId, TaxObligationStatus status, TaxTreatment treatment,
            org.springframework.data.domain.Pageable pageable) {
        return taxObligationJpaRepository.findAll((org.springframework.data.jpa.domain.Specification<TaxObligationJpaEntity>) (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("companyId"), companyId));
            predicates.add(builder.equal(root.get("invoiceId"), invoiceId));
            predicates.add(builder.equal(root.get("contractId"), contractId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (treatment != null) predicates.add(builder.equal(root.get("treatment"), treatment));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable).map(this::toDomain);
    }

    @Override
    public TaxObligation save(TaxObligation taxObligation) {
        return toDomain(taxObligationJpaRepository.save(new TaxObligationJpaEntity(
                taxObligation.getCompanyId(), taxObligation.getInvoiceId(), taxObligation.getContractId(),
                taxObligation.getReceivableId(), taxObligation.getName(), taxObligation.getDescription(),
                taxObligation.getDueOn(), taxObligation.getAmount(), taxObligation.getTreatment(), taxObligation.getStatus()
        )));
    }

    @Override
    public Optional<TaxObligation> findByIdAndCompanyId(UUID id, UUID companyId) {
        return taxObligationJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public BigDecimal registeredWithheldAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId) {
        return taxObligationJpaRepository.sumWithheldAmountByReceivableIdAndCompanyId(
                receivableId, companyId, TaxTreatment.WITHHELD_BY_CUSTOMER, TaxObligationStatus.REGISTERED
        );
    }

    @Override
    public List<TaxObligation> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId) {
        return taxObligationJpaRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public TaxObligation updateStatus(TaxObligation tax) {
        TaxObligationJpaEntity entity = taxObligationJpaRepository.findByIdAndCompanyId(tax.getId(), tax.getCompanyId()).orElseThrow();
        entity.setStatus(tax.getStatus());
        return toDomain(taxObligationJpaRepository.save(entity));
    }

    private TaxObligation toDomain(TaxObligationJpaEntity entity) {
        return TaxObligation.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getInvoiceId(), entity.getContractId(), entity.getReceivableId(),
                entity.getName(), entity.getDescription(), entity.getDueOn(), entity.getAmount(), entity.getTreatment(),
                entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
