package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.reporting.domain.WorkManagerialResultRepository;
import com.renovar.canteiro.io.reporting.domain.WorkManagerialResultRepository.Fact;
import com.renovar.canteiro.io.reporting.domain.WorkManagerialResultRepository.Kind;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class ReadWorkManagerialResultUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkforceRepository workforce;
    private final WorkManagerialResultRepository source;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result read(UUID work, UUID contract, UUID service, CurrencyCode currency, LocalDate from, LocalDate to) {
        var tenant = tenants.requireCurrentTenant();
        Instant asOf = clock.instant();
        if (!workforce.hasGrant(tenant.companyId(), work, tenant.userId(), WorkOperation.READ_FINANCIAL, asOf)) {
            throw new TenantResourceNotFoundException("Work");
        }
        authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
        authorization.requirePermission(AccessModule.REPORTING, AccessAction.READ);
        boolean payables = canRead(AccessModule.PAYABLES);
        boolean projected = payables && canRead(AccessModule.BILLING) && canRead(AccessModule.INVOICES);
        boolean cash = payables && canRead(AccessModule.RECEIVABLES);
        if (!projected && !cash) {
            throw new AccessDeniedException("Managerial result requires read access to the financial sources of a view.");
        }
        if (from.isAfter(to) || to.isAfter(asOf.atOffset(ZoneOffset.UTC).toLocalDate()) || service != null && contract == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER,
                    "Use an ordered period through today UTC and include the contract when selecting a service.");
        }
        UUID company = tenant.companyId();
        if (contract != null && !source.containsTarget(company, work, contract, service)) {
            throw new TenantResourceNotFoundException(service == null ? "Contract" : "Contract service");
        }
        View forecast = projected ? summarize(source.projected(company, work, currency, from, to), contract, service, true)
                : View.noPermission();
        View recorded = cash ? summarize(source.recordedCash(company, work, currency, from, to), contract, service, false)
                : View.noPermission();
        return new Result(work, contract, service, currency, from, to, asOf, forecast, recorded);
    }

    private boolean canRead(AccessModule module) {
        return authorization.hasPermission(module, AccessAction.READ);
    }

    private static View summarize(List<Fact> facts, UUID contract, UUID service, boolean projected) {
        Predicate<Fact> included = fact -> (contract == null || contract.equals(fact.contractId()))
                && (service == null || service.equals(fact.serviceId()));
        Predicate<Fact> parentResidue = fact -> contract != null && fact.contractId() == null
                || service != null && contract.equals(fact.contractId()) && fact.serviceId() == null;
        List<Fact> attributed = facts.stream().filter(included).toList();
        var reasons = new ArrayList<CoverageReason>();
        if (facts.stream().anyMatch(parentResidue)) {
            reasons.add(CoverageReason.PARENT_LEVEL_FACTS);
        }
        if (attributed.stream().anyMatch(fact -> fact.kind() == Kind.UNCERTAIN_EXPENSE)) {
            reasons.add(CoverageReason.UNRECONCILED_RENEGOTIATION);
        }
        var income = amount(attributed, projected ? Kind.BILLED : Kind.RECEIVED);
        var expense = amount(attributed, projected ? Kind.WORK_EXPENSE : Kind.PAID);
        var tax = projected ? amount(attributed, Kind.TAX) : null;
        var netAttributed = income.subtract(expense).subtract(tax == null ? BigDecimal.ZERO : tax);
        var total = reasons.isEmpty() ? netAttributed : null;
        BigDecimal margin = projected && total != null && income.signum() > 0
                ? total.movePointRight(2).divide(income, 2, RoundingMode.HALF_UP) : null;
        var directlyAssigned = attributed.stream()
                .filter(fact -> java.util.Objects.equals(contract, fact.contractId()) && java.util.Objects.equals(service, fact.serviceId()))
                .toList();
        return new View(reasons.isEmpty() ? Coverage.RECORDED : Coverage.PARTIAL, reasons,
                income, expense, tax, netAttributed, total, margin,
                amount(directlyAssigned, projected ? Kind.BILLED : Kind.RECEIVED),
                amount(directlyAssigned, projected ? Kind.WORK_EXPENSE : Kind.PAID),
                projected ? amount(directlyAssigned, Kind.TAX) : null);
    }

    private static BigDecimal amount(List<Fact> facts, Kind kind) {
        return facts.stream().filter(fact -> fact.kind() == kind).map(Fact::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public enum Coverage { RECORDED, PARTIAL, NO_PERMISSION }

    public enum CoverageReason { PARENT_LEVEL_FACTS, UNRECONCILED_RENEGOTIATION }

    public record View(Coverage coverage, List<CoverageReason> reasons, BigDecimal income, BigDecimal expense,
                       BigDecimal tax, BigDecimal attributedResult, BigDecimal result, BigDecimal margin,
                       BigDecimal directIncome, BigDecimal directExpense, BigDecimal directTax) {
        static View noPermission() {
            return new View(Coverage.NO_PERMISSION, List.of(), null, null, null, null, null, null, null, null, null);
        }
    }

    public record Result(UUID workId, UUID contractId, UUID serviceId, CurrencyCode currency, LocalDate from, LocalDate to,
                         Instant calculatedAt, View projected, View recordedCash) { }
}
