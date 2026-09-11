package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

interface TaxObligationJpaRepository extends JpaRepository<TaxObligationJpaEntity, UUID> {

    Optional<TaxObligationJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<TaxObligationJpaEntity> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);

    @Query("""
            select coalesce(sum(tax.amount), 0)
            from TaxObligationJpaEntity tax
            where tax.receivableId = :receivableId
              and tax.companyId = :companyId
              and tax.treatment = :treatment
              and tax.status = :status
            """)
    BigDecimal sumWithheldAmountByReceivableIdAndCompanyId(
            @Param("receivableId") UUID receivableId,
            @Param("companyId") UUID companyId,
            @Param("treatment") TaxTreatment treatment,
            @Param("status") TaxObligationStatus status
    );
}
