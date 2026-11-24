package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.WorkBillingReportRepository;
import com.renovar.canteiro.io.receivables.domain.WorkBillingReportRepository.ContractPosition;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ReadWorkBillingControlUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final WorkRepository works;
    private final WorkBillingReportRepository source;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Position read(UUID workId, Pageable pageable) {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        Work work = requireWork(companyId, workId);
        source.validateInvoiceOrigins(companyId, workId);

        var items = new ArrayList<ContractPosition>(pageable.getPageSize());
        long[] index = {0};
        var summary = summarize(companyId, workId, contract -> {
            if (index[0]++ >= pageable.getOffset() && items.size() < pageable.getPageSize()) items.add(contract);
        });
        return new Position(work, summary, new PageImpl<>(items, pageable, index[0]));
    }

    Work requireWork(UUID companyId, UUID workId) {
        authorization.requirePermission(AccessModule.WORKS, AccessAction.READ);
        authorization.requirePermission(AccessModule.BILLING, AccessAction.READ);
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        return works.findByIdAndCompanyId(workId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Work"));
    }

    List<CurrencyTotal> summarize(UUID companyId, UUID workId, Consumer<ContractPosition> onContract) {
        var totals = new EnumMap<CurrencyCode, CurrencyTotal>(CurrencyCode.class);
        source.scan(companyId, workId, contract -> {
            var next = new CurrencyTotal(contract.currency(), 1, contract.commercial().netAmount(),
                    contract.invoicedAmount(), contract.documentaryBalance());
            totals.merge(contract.currency(), next, CurrencyTotal::plus);
            onContract.accept(contract);
        });
        return List.copyOf(totals.values());
    }

    public record Position(Work work, List<CurrencyTotal> totalsByCurrency, Page<ContractPosition> contracts) { }

    public record CurrencyTotal(CurrencyCode currency, long contractCount, BigDecimal contractNetAmount,
                                BigDecimal invoicedAmount, BigDecimal documentaryBalance) {
        private CurrencyTotal plus(CurrencyTotal other) {
            return new CurrencyTotal(currency, contractCount + other.contractCount,
                    contractNetAmount.add(other.contractNetAmount), invoicedAmount.add(other.invoicedAmount),
                    documentaryBalance.add(other.documentaryBalance));
        }
    }
}
