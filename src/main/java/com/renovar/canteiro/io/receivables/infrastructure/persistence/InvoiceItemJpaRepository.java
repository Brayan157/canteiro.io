package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

interface InvoiceItemJpaRepository extends JpaRepository<InvoiceItemJpaEntity, UUID> {

    List<InvoiceItemJpaEntity> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);

    @Query("""
            select coalesce(sum(item.allocatedAmount), 0)
            from InvoiceItemJpaEntity item, InvoiceJpaEntity invoice
            where item.invoiceId = invoice.id
              and item.companyId = :companyId
              and invoice.companyId = :companyId
              and item.billingAllocationId = :billingAllocationId
              and item.status = com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus.REGISTERED
              and invoice.status = :status
            """)
    BigDecimal sumAllocatedAmountByBillingAllocationIdAndCompanyIdAndInvoiceStatus(
            @Param("billingAllocationId") UUID billingAllocationId,
            @Param("companyId") UUID companyId,
            @Param("status") InvoiceStatus status
    );

    @Query("""
            select coalesce(sum(item.allocatedAmount), 0)
            from InvoiceItemJpaEntity item, InvoiceJpaEntity invoice
            where item.invoiceId = invoice.id
              and item.companyId = :companyId
              and invoice.companyId = :companyId
              and item.contractId = :contractId
              and invoice.contractId = :contractId
              and item.status = com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus.REGISTERED
              and invoice.status = :status
            """)
    BigDecimal sumAllocatedAmountByContractIdAndCompanyIdAndInvoiceStatus(
            @Param("contractId") UUID contractId,
            @Param("companyId") UUID companyId,
            @Param("status") InvoiceStatus status
    );
}
