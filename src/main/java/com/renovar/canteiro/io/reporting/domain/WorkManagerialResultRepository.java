package com.renovar.canteiro.io.reporting.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkManagerialResultRepository {
    boolean containsTarget(UUID company, UUID work, UUID contract, UUID service);

    List<Fact> projected(UUID company, UUID work, CurrencyCode currency, LocalDate from, LocalDate to);

    List<Fact> recordedCash(UUID company, UUID work, CurrencyCode currency, LocalDate from, LocalDate to);

    enum Kind { BILLED, WORK_EXPENSE, TAX, UNCERTAIN_EXPENSE, RECEIVED, PAID }

    record Fact(UUID contractId, UUID serviceId, Kind kind, BigDecimal amount) { }
}
