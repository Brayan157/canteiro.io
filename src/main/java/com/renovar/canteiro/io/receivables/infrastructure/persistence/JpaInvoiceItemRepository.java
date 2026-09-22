package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaInvoiceItemRepository implements InvoiceItemRepository {

    private final InvoiceItemJpaRepository invoiceItemJpaRepository;

    @Override
    public InvoiceItem save(InvoiceItem invoiceItem) {
        return toDomain(invoiceItemJpaRepository.save(new InvoiceItemJpaEntity(
                invoiceItem.getCompanyId(), invoiceItem.getInvoiceId(), invoiceItem.getContractId(),
                invoiceItem.getBillingAllocationId(), invoiceItem.getContractServiceId(), invoiceItem.getDescription(),
                invoiceItem.getAllocatedAmount(), invoiceItem.getStatus()
        )));
    }

    @Override
    public List<InvoiceItem> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId) {
        return invoiceItemJpaRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public BigDecimal registeredAmountByBillingAllocationIdAndCompanyId(UUID billingAllocationId, UUID companyId) {
        return invoiceItemJpaRepository.sumAllocatedAmountByBillingAllocationIdAndCompanyIdAndInvoiceStatus(
                billingAllocationId, companyId, InvoiceStatus.REGISTERED
        );
    }

    @Override
    public BigDecimal registeredAmountByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return invoiceItemJpaRepository.sumAllocatedAmountByContractIdAndCompanyIdAndInvoiceStatus(
                contractId, companyId, InvoiceStatus.REGISTERED
        );
    }

    @Override
    public InvoiceItem updateStatus(InvoiceItem item) {
        InvoiceItemJpaEntity entity = invoiceItemJpaRepository.findById(item.getId()).orElseThrow();
        if (!entity.getCompanyId().equals(item.getCompanyId())) throw new IllegalArgumentException("Invoice item tenant mismatch");
        entity.setStatus(item.getStatus());
        return toDomain(invoiceItemJpaRepository.save(entity));
    }

    private InvoiceItem toDomain(InvoiceItemJpaEntity entity) {
        return InvoiceItem.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getInvoiceId(), entity.getContractId(),
                entity.getBillingAllocationId(), entity.getContractServiceId(), entity.getDescription(),
                entity.getAllocatedAmount(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
