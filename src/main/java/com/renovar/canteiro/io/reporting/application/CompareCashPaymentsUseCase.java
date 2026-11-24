package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository.Category;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository.CategoryMovementTotal;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository.MovementTotal;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CompareCashPaymentsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkRepository works;
    private final RealizedCashRepository source;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result compare(UUID workId, LocalDate from, LocalDate to) {
        var query = prepare(workId, from, to);
        var currentRows = source.payments(query.companyId(), workId, from, to);
        var previousRows = source.payments(query.companyId(), workId, query.previous().from(), query.previous().to());
        var totals = Arrays.stream(CurrencyCode.values())
                .map(currency -> position(workId, currentRows, previousRows, currency)).toList();
        return new Result(workId, query.current(), query.previous(), query.calculatedAt(), totals);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CategoryResult compareByCategory(UUID workId, LocalDate from, LocalDate to) {
        authorization.requirePermission(AccessModule.COSTS_EXPENSES, AccessAction.READ);
        var query = prepare(workId, from, to);
        var current = byCategory(source.paymentsByCategory(query.companyId(), workId, from, to));
        var previous = byCategory(source.paymentsByCategory(query.companyId(), workId,
                query.previous().from(), query.previous().to()));
        var order = Comparator.comparing(CategoryCurrency::currency)
                .thenComparing(key -> key.category().code(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(key -> key.category().id(), Comparator.nullsLast(Comparator.naturalOrder()));
        var categories = Stream.concat(current.keySet().stream(), previous.keySet().stream()).distinct().sorted(order)
                .map(key -> new CategoryPosition(key.category(), position(workId, current.getOrDefault(key, List.of()),
                        previous.getOrDefault(key, List.of()), key.currency()))).toList();
        return new CategoryResult(workId, query.current(), query.previous(), query.calculatedAt(), categories);
    }

    private Map<CategoryCurrency, List<MovementTotal>> byCategory(List<CategoryMovementTotal> rows) {
        return rows.stream().collect(Collectors.groupingBy(row -> new CategoryCurrency(row.category(), row.movement().currency()),
                Collectors.mapping(CategoryMovementTotal::movement, Collectors.toList())));
    }

    private Query prepare(UUID workId, LocalDate from, LocalDate to) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.PAYABLES, AccessAction.READ);
        if (workId != null) {
            authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
            works.findByIdAndCompanyId(workId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Work"));
        }

        Instant calculatedAt = clock.instant();
        LocalDate today = calculatedAt.atOffset(ZoneOffset.UTC).toLocalDate();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || to.isAfter(today) || from.toEpochDay() - days < LocalDate.of(1, 1, 1).toEpochDay()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER,
                    "Use an ordered period through today UTC with a previous period starting in year 1 or later.");
        }
        var current = new Period(from, to);
        var previous = new Period(from.minusDays(days), from.minusDays(1));
        return new Query(companyId, current, previous, calculatedAt);
    }

    private CurrencyPosition position(UUID workId, List<MovementTotal> current, List<MovementTotal> previous,
                                      CurrencyCode currency) {
        var work = comparison(current, previous, currency, PayableFinancialScope.WORK);
        var company = workId == null ? comparison(current, previous, currency, PayableFinancialScope.COMPANY) : null;
        var total = company == null ? work : compareTotals(work.current().plus(company.current()),
                work.previous().plus(company.previous()));
        return new CurrencyPosition(currency, total, work, company);
    }

    private Comparison comparison(List<MovementTotal> current, List<MovementTotal> previous,
                                  CurrencyCode currency, PayableFinancialScope scope) {
        return compareTotals(total(current, currency, scope), total(previous, currency, scope));
    }

    private PeriodTotal total(List<MovementTotal> rows, CurrencyCode currency, PayableFinancialScope scope) {
        return rows.stream().filter(row -> row.currency() == currency && row.scope() == scope).findFirst()
                .map(row -> new PeriodTotal(row.amount(), row.entries()))
                .orElseGet(() -> new PeriodTotal(BigDecimal.ZERO, 0));
    }

    private Comparison compareTotals(PeriodTotal current, PeriodTotal previous) {
        if (current.entryCount() == 0 || previous.entryCount() == 0) {
            return new Comparison(current, previous, ComparisonStatus.INSUFFICIENT_RECORDS, null, null);
        }
        BigDecimal difference = current.amount().subtract(previous.amount());
        if (previous.amount().signum() <= 0) {
            return new Comparison(current, previous, ComparisonStatus.NON_POSITIVE_BASE, difference, null);
        }
        BigDecimal percentage = difference.movePointRight(2).divide(previous.amount(), 2, RoundingMode.HALF_UP);
        return new Comparison(current, previous, ComparisonStatus.RECORDED_MOVEMENTS, difference, percentage);
    }

    public enum ComparisonStatus { RECORDED_MOVEMENTS, INSUFFICIENT_RECORDS, NON_POSITIVE_BASE }
    public record Period(LocalDate from, LocalDate to) { }
    public record PeriodTotal(BigDecimal amount, long entryCount) {
        PeriodTotal plus(PeriodTotal other) {
            return new PeriodTotal(amount.add(other.amount), entryCount + other.entryCount);
        }
    }
    public record Comparison(PeriodTotal current, PeriodTotal previous, ComparisonStatus status,
                             BigDecimal differenceAmount, BigDecimal percentageChange) { }
    public record CurrencyPosition(CurrencyCode currency, Comparison payments, Comparison workPayments,
                                   Comparison companyPayments) { }
    public record Result(UUID workId, Period currentPeriod, Period previousPeriod, Instant calculatedAt,
                         List<CurrencyPosition> totalsByCurrency) { }
    public record CategoryPosition(Category category, CurrencyPosition position) { }
    public record CategoryResult(UUID workId, Period currentPeriod, Period previousPeriod, Instant calculatedAt,
                                 List<CategoryPosition> categories) { }
    private record CategoryCurrency(Category category, CurrencyCode currency) { }
    private record Query(UUID companyId, Period current, Period previous, Instant calculatedAt) { }
}
