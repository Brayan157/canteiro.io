package com.renovar.canteiro.io.reporting.domain;

import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RealizedCashRepository {
    List<MovementTotal> receipts(UUID companyId, UUID workId, LocalDate from, LocalDate to);
    List<MovementTotal> payments(UUID companyId, UUID workId, LocalDate from, LocalDate to);
    List<CategoryMovementTotal> paymentsByCategory(UUID companyId, UUID workId, LocalDate from, LocalDate to);

    record MovementTotal(CurrencyCode currency, PayableFinancialScope scope, BigDecimal amount, long entries,
                         LocalDate firstEffectiveOn) { }
    record Category(UUID id, String code, String name, Boolean active) { }
    record CategoryMovementTotal(Category category, MovementTotal movement) { }
}
