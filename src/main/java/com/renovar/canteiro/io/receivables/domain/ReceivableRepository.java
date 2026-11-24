package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
    void scanCurrentDueBalances(UUID companyId, UUID workId, java.time.LocalDate dueThrough,
                                java.util.function.Consumer<DueBalance> consumer);

    record DueBalance(com.renovar.canteiro.io.shared.domain.CurrencyCode currency, java.time.LocalDate dueOn,
                      java.math.BigDecimal outstandingAmount) { }

    org.springframework.data.domain.Page<ReceivableReadProjection> findByCompanyId(
            UUID companyId, ReceivableFilter filter, org.springframework.data.domain.Pageable pageable);

    Optional<ReceivableReadProjection> findReadByIdAndCompanyId(UUID id, UUID companyId);

    java.util.Map<UUID, ReceivableFinancialPosition> findOriginalPositionsByBillingInstallmentIdsAndCompanyId(
            java.util.List<UUID> installmentIds, UUID companyId);

    Receivable save(Receivable receivable);

    Optional<Receivable> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Receivable> findByIdAndCompanyIdWithSettlementLock(UUID id, UUID companyId);

    Optional<Receivable> findByIdAndCompanyIdWithCancellationCheckLock(UUID id, UUID companyId);

    Receivable updateStatus(Receivable receivable);

    Optional<Receivable> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);
    Receivable saveRenegotiated(Receivable receivable, UUID sourceReceivableId);
    java.util.List<Receivable> findAllByBillingInstallmentIdAndCompanyId(UUID installmentId, UUID companyId);
    java.util.List<Receivable> findRenegotiatedChildren(UUID sourceReceivableId, UUID companyId);
}
