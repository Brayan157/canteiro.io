package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.payables.application.PayablePosition;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableFilter;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.payables.domain.PayableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadCashDueUseCase {
    public static final int DEFAULT_HORIZON_DAYS = 30;
    public static final int MAX_HORIZON_DAYS = 365;
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkRepository works;
    private final ReceivableRepository receivables;
    private final PayableRepository payables;
    private final PayableSettlementRepository settlements;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result read(UUID workId, int horizonDays) {
        boolean readReceipts = authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        boolean readPayments = authorization.hasPermission(AccessModule.PAYABLES, AccessAction.READ);
        return read(workId, horizonDays, readReceipts, readPayments);
    }

    Result read(UUID workId, int horizonDays, boolean readReceipts, boolean readPayments) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        if (!readReceipts && !readPayments) throw new AccessDeniedException("Cash forecast requires a financial read permission.");
        if (workId != null) {
            authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
            works.findByIdAndCompanyId(workId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Work"));
        }
        if (horizonDays < 1 || horizonDays > MAX_HORIZON_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, "horizonDays must be between 1 and 365.");
        }
        Instant calculatedAt = clock.instant();
        LocalDate asOf = calculatedAt.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate through = asOf.plusDays(horizonDays - 1L);

        var inflows = new EnumMap<CurrencyCode, DueTotals>(CurrencyCode.class);
        var workPayments = new EnumMap<CurrencyCode, DueTotals>(CurrencyCode.class);
        var companyPayments = new EnumMap<CurrencyCode, DueTotals>(CurrencyCode.class);
        if (readReceipts) receivables.scanCurrentDueBalances(companyId, workId, through,
                row -> inflows.computeIfAbsent(row.currency(), key -> new DueTotals())
                        .add(row.dueOn().isBefore(asOf), row.outstandingAmount()));
        if (readPayments) scanPayables(companyId, workId, asOf, through, workPayments, companyPayments);

        List<CurrencyPosition> positions = Arrays.stream(CurrencyCode.values()).map(currency -> {
            DueTotals work = workPayments.getOrDefault(currency, new DueTotals());
            DueTotals company = companyPayments.getOrDefault(currency, new DueTotals());
            return new CurrencyPosition(currency,
                    inflows.getOrDefault(currency, new DueTotals()).toComponent(readReceipts),
                    DueTotals.combined(work, company).toComponent(readPayments), work.toComponent(readPayments),
                    workId == null ? company.toComponent(readPayments) : null);
        }).toList();
        return new Result(workId, asOf, through, horizonDays, calculatedAt, positions);
    }

    private void scanPayables(UUID companyId, UUID workId, LocalDate asOf, LocalDate through,
                              EnumMap<CurrencyCode, DueTotals> workPayments, EnumMap<CurrencyCode, DueTotals> companyPayments) {
        var filter = new PayableFilter(workId == null ? null : PayableFinancialScope.WORK, workId,
                null, null, null, null, null, null, null, null, through, null);
        var page = PageRequest.of(0, 200, Sort.by("id"));
        while (true) {
            var batch = payables.find(companyId, filter, page);
            var titles = batch.getContent().stream().filter(title -> switch (title.getStatus()) {
                case DRAFT, CANCELLED, RENEGOTIATED -> false;
                default -> true;
            }).toList();
            var paid = settlements.netPaid(companyId, titles.stream().map(Payable::getId).toList());
            for (Payable title : titles) {
                var position = PayablePosition.calculate(title, paid.getOrDefault(title.getId(), BigDecimal.ZERO), asOf);
                var target = title.getFinancialScope() == PayableFinancialScope.WORK ? workPayments : companyPayments;
                target.computeIfAbsent(title.getCurrency(), key -> new DueTotals())
                        .add(title.getDueOn().isBefore(asOf), position.outstandingAmount());
            }
            if (!batch.hasNext()) return;
            page = page.next();
        }
    }

    private static final class DueTotals {
        private final BucketTotals overdue = new BucketTotals();
        private final BucketTotals upcoming = new BucketTotals();

        void add(boolean late, BigDecimal amount) {
            BucketTotals bucket = late ? overdue : upcoming;
            if (amount == null) bucket.unknown++;
            else if (amount.signum() > 0) {
                bucket.amount = bucket.amount.add(amount);
                bucket.count++;
            }
        }

        static DueTotals combined(DueTotals a, DueTotals b) {
            var result = new DueTotals();
            result.overdue.merge(a.overdue, b.overdue);
            result.upcoming.merge(a.upcoming, b.upcoming);
            return result;
        }

        Component toComponent(boolean allowed) {
            return new Component(overdue.toBucket(allowed), upcoming.toBucket(allowed));
        }
    }

    private static final class BucketTotals {
        private BigDecimal amount = BigDecimal.ZERO;
        private long count;
        private long unknown;

        void merge(BucketTotals a, BucketTotals b) {
            amount = a.amount.add(b.amount);
            count = a.count + b.count;
            unknown = a.unknown + b.unknown;
        }

        Bucket toBucket(boolean allowed) {
            if (!allowed) return new Bucket(Coverage.NO_PERMISSION, null, null, null);
            if (unknown == 0) return new Bucket(Coverage.OFFICIAL_RESIDUALS, amount, count, 0L);
            if (count == 0) return new Bucket(Coverage.UNAVAILABLE, null, 0L, unknown);
            return new Bucket(Coverage.PARTIAL, amount, count, unknown);
        }
    }

    public enum Coverage { OFFICIAL_RESIDUALS, PARTIAL, UNAVAILABLE, NO_PERMISSION }
    public record Bucket(Coverage coverage, BigDecimal amount, Long titleCount, Long unavailableTitleCount) { }
    public record Component(Bucket overdue, Bucket upcoming) { }
    public record CurrencyPosition(CurrencyCode currency, Component receivables, Component payables,
                                   Component workPayables, Component companyPayables) { }
    public record Result(UUID workId, LocalDate asOf, LocalDate dueThrough, int horizonDays,
                         Instant calculatedAt, List<CurrencyPosition> totalsByCurrency) { }
}
