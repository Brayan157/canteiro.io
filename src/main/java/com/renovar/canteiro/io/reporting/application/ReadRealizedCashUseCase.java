package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository.MovementTotal;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReadRealizedCashUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkRepository works;
    private final RealizedCashRepository source;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result read(UUID workId, LocalDate from, LocalDate to) {
        boolean readReceipts = authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        boolean readPayments = authorization.hasPermission(AccessModule.PAYABLES, AccessAction.READ);
        return read(workId, from, to, readReceipts, readPayments);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result readAccumulatedWork(UUID workId) {
        boolean readReceipts = authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        boolean readPayments = authorization.hasPermission(AccessModule.PAYABLES, AccessAction.READ);
        return read(workId, null, null, readReceipts, readPayments);
    }

    Result read(UUID workId, LocalDate from, LocalDate to, boolean readReceipts, boolean readPayments) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        if (!readReceipts && !readPayments) throw new AccessDeniedException("Cash movement requires a financial read permission.");
        if (workId != null) {
            authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
            works.findByIdAndCompanyId(workId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Work"));
        }
        Instant calculatedAt = clock.instant();
        LocalDate today = calculatedAt.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to == null ? today : to;
        if ((from != null && from.isAfter(end)) || end.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER,
                    "The period must be ordered and end no later than today UTC.");
        }

        var receipts = readReceipts ? source.receipts(companyId, workId, from, end) : List.<MovementTotal>of();
        var payments = readPayments ? source.payments(companyId, workId, from, end) : List.<MovementTotal>of();
        List<CurrencyPosition> totals = Arrays.stream(CurrencyCode.values()).map(currency -> {
            var received = component(readReceipts, receipts, currency, PayableFinancialScope.WORK);
            var workPaid = component(readPayments, payments, currency, PayableFinancialScope.WORK);
            var companyPaid = workId == null ? component(readPayments, payments, currency, PayableFinancialScope.COMPANY) : null;
            var paid = readPayments
                    ? new Component(Coverage.RECORDED_MOVEMENTS,
                        companyPaid == null ? workPaid.amount() : workPaid.amount().add(companyPaid.amount()),
                        companyPaid == null ? workPaid.entryCount() : workPaid.entryCount() + companyPaid.entryCount())
                    : new Component(Coverage.NO_PERMISSION, null, null);
            BigDecimal difference = readReceipts && readPayments ? received.amount().subtract(paid.amount()) : null;
            BigDecimal cashResultPercentage = workId != null && difference != null && received.amount().signum() > 0
                    ? difference.movePointRight(2).divide(received.amount(), 2, RoundingMode.HALF_UP) : null;
            return new CurrencyPosition(currency, received, paid, workPaid, companyPaid, difference, cashResultPercentage);
        }).toList();
        LocalDate start = from == null ? Stream.concat(receipts.stream(), payments.stream())
                .map(MovementTotal::firstEffectiveOn).min(Comparator.naturalOrder()).orElse(null) : from;
        return new Result(workId, start, end, calculatedAt, totals);
    }

    private Component component(boolean allowed, List<MovementTotal> rows, CurrencyCode currency, PayableFinancialScope scope) {
        if (!allowed) return new Component(Coverage.NO_PERMISSION, null, null);
        return rows.stream().filter(row -> row.currency() == currency && row.scope() == scope).findFirst()
                .map(row -> new Component(Coverage.RECORDED_MOVEMENTS, row.amount(), row.entries()))
                .orElseGet(() -> new Component(Coverage.RECORDED_MOVEMENTS, BigDecimal.ZERO, 0L));
    }

    public enum Coverage { RECORDED_MOVEMENTS, NO_PERMISSION }
    public record Component(Coverage coverage, BigDecimal amount, Long entryCount) { }
    public record CurrencyPosition(CurrencyCode currency, Component receipts, Component payments, Component workPayments,
                                   Component companyPayments, BigDecimal netMovement, BigDecimal cashResultPercentage) { }
    public record Result(UUID workId, LocalDate from, LocalDate to, Instant calculatedAt, List<CurrencyPosition> totalsByCurrency) { }
}
