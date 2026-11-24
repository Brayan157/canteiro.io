package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.receivables.domain.WorkBillingReportRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.Work;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateWorkBillingPdfService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ReadWorkBillingControlUseCase control;
    private final WorkBillingReportRepository works;
    private final WorkBillingPdfGenerator generator;
    private final PlatformTransactionManager transactionManager;
    private final AuditEventRecorder audit;

    public Generated generate(UUID workId) {
        var tenant = tenants.requireCurrentTenant();
        var work = readTransaction().execute(status -> requireExportableWork(tenant.companyId(), workId));
        String safe = work.getName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").trim();
        String filename = "controle-faturamento-obra-" + safe.substring(0, Math.min(100, safe.length())) + ".pdf";
        String correlation = MDC.get("X-Correlation-Id");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return new Generated(filename, output -> {
            var previous = SecurityContextHolder.getContext();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            tenants.setCurrentTenant(tenant);
            if (correlation != null) MDC.put("X-Correlation-Id", correlation);
            try {
                var result = readTransaction().execute(status -> write(tenant.companyId(), workId, output));
                var transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> audit.recordDirectAction(AuditModule.BILLING, AuditAction.EXPORT,
                        "Work", workId, null, null, Map.of("format", "WORK_BILLING_CONTROL_PDF", "pages", result.pages(),
                                "contractRows", result.contractRows(), "streamed", true)));
            } catch (UncheckedIOException failure) {
                throw failure.getCause();
            } finally {
                tenants.clear();
                MDC.remove("X-Correlation-Id");
                SecurityContextHolder.setContext(previous);
            }
        });
    }

    private Work requireExportableWork(UUID companyId, UUID workId) {
        authorization.requirePermission(AccessModule.BILLING, AccessAction.EXPORT);
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.EXPORT);
        var work = control.requireWork(companyId, workId);
        works.validateInvoiceOrigins(companyId, workId);
        return work;
    }

    private WorkBillingPdfGenerator.ExportResult write(UUID companyId, UUID workId, OutputStream output) {
        var work = requireExportableWork(companyId, workId);
        try {
            return generator.writeWork(new WorkBillingPdfGenerator.Header(work.getName(), work.getReference()),
                    consumer -> control.summarize(companyId, workId, consumer), output);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private TransactionTemplate readTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    public record Generated(String filename, Writer writer) { }

    @FunctionalInterface
    public interface Writer {
        void write(OutputStream output) throws IOException;
    }
}
