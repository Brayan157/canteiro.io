package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaInvoiceRepository implements InvoiceRepository {

    private final InvoiceJpaRepository invoiceJpaRepository;

    @Override
    public Invoice save(Invoice invoice) {
        return toDomain(invoiceJpaRepository.save(new InvoiceJpaEntity(
                invoice.getCompanyId(), invoice.getContractId(), invoice.getNumber(), invoice.getSeries(),
                invoice.getAccessKey(), invoice.getIssuedOn(), invoice.getTotalAmount(), invoice.getStatus()
        )));
    }

    @Override
    public Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId) {
        return invoiceJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Invoice> findByIdAndCompanyIdWithLock(UUID id, UUID companyId) {
        return invoiceJpaRepository.findByIdAndCompanyIdWithLock(id, companyId).map(this::toDomain);
    }

    @Override
    public Invoice updateStatus(Invoice invoice) {
        InvoiceJpaEntity entity = invoiceJpaRepository.findByIdAndCompanyId(invoice.getId(), invoice.getCompanyId()).orElseThrow();
        entity.setStatus(invoice.getStatus());
        return toDomain(invoiceJpaRepository.save(entity));
    }

    @Override
    public boolean existsRegisteredByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId) {
        return invoiceJpaRepository.existsRegisteredByBillingInstallmentIdAndCompanyId(billingInstallmentId, companyId);
    }

    private Invoice toDomain(InvoiceJpaEntity entity) {
        return Invoice.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getNumber(), entity.getSeries(),
                entity.getAccessKey(), entity.getIssuedOn(), entity.getTotalAmount(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
