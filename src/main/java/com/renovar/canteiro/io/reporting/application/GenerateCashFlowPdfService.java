package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.reporting.application.CashFlowPdfGenerator.Data;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateCashFlowPdfService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkRepository works;
    private final CompanyRepository companies;
    private final ReadRealizedCashUseCase realized;
    private final ReadCashDueUseCase due;
    private final CompareCashPaymentsUseCase comparison;
    private final CashFlowPdfGenerator generator;
    private final AuditEventRecorder audit;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Prepared generate(UUID workId, LocalDate from, LocalDate to, int horizonDays, CurrencyCode currency) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.REPORTING, AccessAction.EXPORT);
        boolean receipts = canExport(AccessModule.RECEIVABLES);
        boolean payments = canExport(AccessModule.PAYABLES);
        if (!receipts && !payments) {
            throw new AccessDeniedException("Cash report requires read and export permissions for a financial source.");
        }
        String workName = null;
        if (workId != null) {
            authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
            workName = works.findByIdAndCompanyId(workId, companyId)
                    .orElseThrow(() -> new TenantResourceNotFoundException("Work")).getName();
        }

        var paymentComparison = payments ? comparison.compare(workId, from, to) : null;
        var realizedCash = realized.read(workId, from, to, receipts, payments);
        var dueCash = due.read(workId, horizonDays, receipts, payments);
        String companyName = companies.findById(companyId)
                .map(company -> company.getTradeName() == null || company.getTradeName().isBlank()
                        ? company.getCorporateName() : company.getTradeName())
                .orElse("Empresa");
        var data = new Data(companyName, workName, currency, from, to, dueCash.asOf(), dueCash.dueThrough(),
                realizedCash.totalsByCurrency().stream().filter(row -> row.currency() == currency).findFirst().orElseThrow(),
                dueCash.totalsByCurrency().stream().filter(row -> row.currency() == currency).findFirst().orElseThrow(),
                paymentComparison == null ? null : paymentComparison.previousPeriod(),
                paymentComparison == null ? null : paymentComparison.totalsByCurrency().stream()
                        .filter(row -> row.currency() == currency).findFirst().orElseThrow());
        audit.recordDirectAction(AuditModule.REPORTING, AuditAction.EXPORT, workId == null ? "Company" : "Work",
                workId == null ? companyId : workId, null, null, Map.of("format", "CASH_FLOW_PDF",
                        "from", from.toString(), "to", to.toString(), "horizonDays", horizonDays,
                        "currency", currency.name(), "receiptsIncluded", receipts, "paymentsIncluded", payments));
        String scope = workId == null ? "empresa" : "obra";
        return new Prepared("relatorio-financeiro-" + scope + "-" + currency.name() + ".pdf", data);
    }

    public void write(Prepared report, OutputStream output) throws IOException {
        generator.write(report.data(), output);
    }

    private boolean canExport(AccessModule source) {
        return authorization.hasPermission(source, AccessAction.READ) && authorization.hasPermission(source, AccessAction.EXPORT);
    }

    public record Prepared(String filename, Data data) { }
}
