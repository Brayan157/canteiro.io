package com.renovar.canteiro.io.receivables.infrastructure.contracts;

import com.renovar.canteiro.io.contracts.application.ContractClosureFinancialProvider;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.receivables.application.CalculateReceivableBalanceUseCase;
import com.renovar.canteiro.io.receivables.application.NetBillingCapacityService;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceWithholdingCapacity;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContractClosureFinancialAdapter implements ContractClosureFinancialProvider, com.renovar.canteiro.io.contracts.application.ContractDiscountRestrictionProvider {
    private final JdbcTemplate jdbc;
    private final BillingAllocationRepository allocations;
    private final ReceivableRepository titles;
    private final InvoiceSettlementRepository ledger;
    private final CalculateReceivableBalanceUseCase balances;
    private final NetBillingCapacityService capacities;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository settlements;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInputs(UUID company, UUID contract) {
        // Caller already holds the contract lock. Same order as receipt writes; also serializes tax/credit writers.
        for (String table : List.of("invoice", "billing_installment", "receivable"))
            jdbc.queryForList("select id from " + table + " where company_id=? and contract_id=? order by id for update", company, contract);
    }

    @Override
    @Transactional(readOnly = true)
    public String restriction(UUID company, UUID contract, BigDecimal proposedNet, boolean grantingDiscount) {
        if (grantingDiscount) {
            var filter = new com.renovar.canteiro.io.receivables.domain.ReceivableFilter(null,null,null,null,null,null,contract,null,null,null,null,null,null);
            int page = 0;
            org.springframework.data.domain.Page<com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection> found;
            do {
                found = titles.findByCompanyId(company, filter, PageRequest.of(page++, 100));
                for (var title : found) {
                    var position = title.financialPosition();
                    if (position.status() != com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status.CANCELLED
                            && position.status() != com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status.RENEGOTIATED
                            && position.outstandingAmount().signum() > 0) return "CONTRACT_HAS_OPEN_CHARGES";
                }
            } while (found.hasNext());
        }
        BigDecimal received = settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(contract), company).getOrDefault(contract, BigDecimal.ZERO);
        return proposedNet.compareTo(received) < 0 ? "CONTRACT_NET_BELOW_RECEIVED_AMOUNT" : null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasContractCommitments(UUID company, UUID contract) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from billing_allocation where company_id=? and contract_id=? and status='ACTIVE')
                  or exists(select 1 from invoice where company_id=? and contract_id=? and status='REGISTERED')
                  or exists(select 1 from receivable where company_id=? and contract_id=? and status<>'CANCELLED')
                """, Boolean.class, company, contract, company, contract, company, contract));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Position> positions(UUID company, UUID contract, List<ContractService> projected) {
        var active = allocations.findByContractIdAndCompanyId(contract, company).stream()
                .filter(a -> a.getStatus() == BillingAllocationStatus.ACTIVE).toList();
        Set<UUID> committed = new HashSet<>(jdbc.queryForList("""
                select s.id from contract_service s where s.company_id=? and s.contract_id=? and (
                  exists(select 1 from billing_allocation a where a.company_id=s.company_id and a.contract_id=s.contract_id and a.contract_service_id=s.id and a.status='ACTIVE')
                  or exists(select 1 from invoice_item ii join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
                    where ii.company_id=s.company_id and ii.contract_id=s.contract_id and ii.contract_service_id=s.id and ii.status='REGISTERED' and i.status='REGISTERED')
                  or exists(select 1 from receivable r join billing_allocation a on a.company_id=r.company_id and a.contract_id=r.contract_id and a.billing_installment_id=r.billing_installment_id
                    where a.company_id=s.company_id and a.contract_id=s.contract_id and a.contract_service_id=s.id and r.status<>'CANCELLED')
                )
                """, UUID.class, company, contract));
        Map<UUID, State> states = new HashMap<>();
        for (var service : projected) states.put(service.getId(), new State());
        for (var allocation : active) if (states.containsKey(allocation.getContractServiceId()) && allocation.getType() == BillingAllocationType.SERVICE)
            states.get(allocation.getContractServiceId()).allocated = states.get(allocation.getContractServiceId()).allocated.add(allocation.getAllocatedAmount());
        try {
            var net = capacities.calculateWithServices(company, contract, projected);
            for (var position : net.positions()) {
                var state = states.get(position.contractServiceId());
                state.free = state.free.add(position.netAvailableAmount());
                if (!position.eligible()) state.reason = "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED";
            }
            if (active.stream().anyMatch(a -> a.getType() == BillingAllocationType.CONTRACT_DISCOUNT))
                states.values().forEach(s -> s.reason = "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED");
        } catch (IllegalStateException | IllegalArgumentException inconsistent) {
            states.values().forEach(s -> s.reason = "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED");
        }
        Map<UUID, String> invoiceHistory = new HashMap<>();
        for (UUID installment : active.stream().map(BillingAllocation::getBillingInstallmentId).distinct().toList()) {
            Set<UUID> serviceIds = active.stream().filter(a -> a.getBillingInstallmentId().equals(installment) && a.getType() == BillingAllocationType.SERVICE)
                    .map(BillingAllocation::getContractServiceId).collect(java.util.stream.Collectors.toSet());
            var receivables = titles.findAllByBillingInstallmentIdAndCompanyId(installment, company);
            if (receivables.size() != 1 || Boolean.TRUE.equals(jdbc.queryForObject("select renegotiation_source_receivable_id is not null from receivable where company_id=? and id=?", Boolean.class, company, receivables.getFirst().getId()))
                    || receivables.getFirst().getStatus() == ReceivableStatus.RENEGOTIATED || receivables.getFirst().getStatus() == ReceivableStatus.CANCELLED) {
                mark(states, serviceIds, "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED"); continue;
            }
            var title = receivables.getFirst();
            if (ledger.hasUnprovenHistory(company, title.getId())) {
                mark(states, serviceIds, "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED"); continue;
            }
            try {
                if (balances.calculate(company, title.getId()).creditedAmount().signum() > 0) {
                    mark(states, serviceIds, "SERVICE_CREDIT_REQUIRES_RECONCILIATION"); continue;
                }
            } catch (IllegalArgumentException inconsistent) {
                mark(states, serviceIds, "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED"); continue;
            }
            int page = 0;
            org.springframework.data.domain.Page<InvoiceSettlementRepository.Candidate> candidates;
            do {
                candidates = ledger.candidates(company, title.getId(), PageRequest.of(page++, 100));
                var bases = InvoiceWithholdingCapacity.calculate(ledger.withholdingBasis(company, title.getId(),
                        candidates.stream().map(InvoiceSettlementRepository.Candidate::invoiceId).distinct().toList()));
                for (var item : candidates) {
                    var state = states.get(item.contractServiceId());
                    if (state == null) continue;
                    String history = invoiceHistory.computeIfAbsent(item.invoiceId(), id -> ledger.hasUnprovenInvoiceHistory(company, id) ? "UNPROVEN" : "PROVEN");
                    var basis = bases.get(item.invoiceId());
                    var capacity = basis == null ? null : basis.item(item);
                    if (!history.equals("PROVEN") || capacity == null || capacity.unavailableReason() != null) {
                        state.reason = "SERVICE_FINANCIAL_RECONCILIATION_REQUIRED"; continue;
                    }
                    state.invoiced = state.invoiced.add(item.invoicedItemAmount());
                    state.cash = state.cash.add(item.itemReceivedAmount());
                    state.withheld = state.withheld.add(capacity.withheldAmount());
                }
            } while (candidates.hasNext());
        }
        Map<UUID, Position> result = new HashMap<>();
        for (var service : projected) {
            var state = states.get(service.getId());
            boolean hasCommitment = service.getMeasurementCompositionAmount().signum() > 0
                    || service.getId() != null && committed.contains(service.getId());
            BigDecimal commercial = state.allocated.add(state.free);
            String completion = state.reason;
            if (completion == null && commercial.signum() == 0 && hasCommitment) completion = "SERVICE_HAS_FINANCIAL_COMMITMENTS";
            else if (completion == null && (state.free.signum() != 0 || state.invoiced.compareTo(state.allocated) != 0
                    || state.cash.add(state.withheld).compareTo(commercial) != 0)) completion = "SERVICE_NOT_FULLY_SETTLED";
            result.put(service.getId(), new Position(completion, hasCommitment ? "SERVICE_HAS_FINANCIAL_COMMITMENTS" : null));
        }
        return result;
    }
    private void mark(Map<UUID, State> states, Set<UUID> services, String reason) {
        services.stream().map(states::get).filter(Objects::nonNull).forEach(s -> s.reason = reason);
    }
    private static final class State {
        BigDecimal allocated = BigDecimal.ZERO, free = BigDecimal.ZERO, invoiced = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO, withheld = BigDecimal.ZERO;
        String reason;
    }
}
