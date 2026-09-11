package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<InvoiceJpaEntity, UUID> {

    Optional<InvoiceJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from InvoiceJpaEntity invoice where invoice.id = :id and invoice.companyId = :companyId")
    Optional<InvoiceJpaEntity> findByIdAndCompanyIdWithLock(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
            select (count(invoice) > 0)
            from InvoiceJpaEntity invoice, InvoiceItemJpaEntity item, BillingAllocationJpaEntity allocation
            where invoice.id = item.invoiceId
              and item.billingAllocationId = allocation.id
              and allocation.billingInstallmentId = :billingInstallmentId
              and invoice.companyId = :companyId
              and item.companyId = :companyId
              and allocation.companyId = :companyId
              and invoice.status = com.renovar.canteiro.io.receivables.domain.InvoiceStatus.REGISTERED
              and item.status = com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus.REGISTERED
            """)
    boolean existsRegisteredByBillingInstallmentIdAndCompanyId(@Param("billingInstallmentId") UUID billingInstallmentId,
                                                               @Param("companyId") UUID companyId);
}
