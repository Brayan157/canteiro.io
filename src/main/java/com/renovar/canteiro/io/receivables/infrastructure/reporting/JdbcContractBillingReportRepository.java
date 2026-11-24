package com.renovar.canteiro.io.receivables.infrastructure.reporting;

import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcContractBillingReportRepository implements ContractBillingReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableRepository receivables;
    private static final String ITEMS = """
        from invoice_item ii join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
        join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=ii.company_id and a.contract_id=ii.contract_id
          and a.contract_service_id is not distinct from ii.contract_service_id
        where ii.company_id=:company and ii.contract_id=:contract and ii.status='REGISTERED' and i.status='REGISTERED' and a.status='ACTIVE'
        """;
    private Map<String,Object> params(UUID company, UUID contract) { return new HashMap<>(Map.of("company",company,"contract",contract)); }
    @Override public BigDecimal serviceSubtotal(UUID company, UUID contract) {
        return jdbc.queryForObject("select coalesce(sum(net_amount+measurement_composition_amount),0) from contract_service where company_id=:company and contract_id=:contract and status<>'CANCELLED'",params(company,contract),BigDecimal.class);
    }
    @Override public BigDecimal invoicedAmount(UUID company, UUID contract) {
        return jdbc.queryForObject("select coalesce(sum(total_amount),0) from invoice where company_id=:company and contract_id=:contract and status='REGISTERED'",params(company,contract),BigDecimal.class);
    }
    @Override public BigDecimal unassignedInvoicedAmount(UUID company, UUID contract) {
        return jdbc.queryForObject("select coalesce(sum(ii.allocated_amount),0) "+ITEMS+" and ii.contract_service_id is null",params(company,contract),BigDecimal.class);
    }
    static final String INVALID_INVOICE_ORIGINS = """
            (i.total_amount<>(select coalesce(sum(ii.allocated_amount),0) from invoice_item ii
              join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=ii.company_id and a.contract_id=ii.contract_id
               and a.contract_service_id is not distinct from ii.contract_service_id and a.status='ACTIVE'
              where ii.invoice_id=i.id and ii.company_id=i.company_id and ii.contract_id=i.contract_id and ii.status='REGISTERED')
            or exists(select 1 from invoice_item ii where ii.invoice_id=i.id and ii.company_id=i.company_id and ii.status='REGISTERED'
              and not exists(select 1 from billing_allocation a where a.id=ii.billing_allocation_id and a.company_id=i.company_id
               and a.contract_id=i.contract_id and a.contract_service_id is not distinct from ii.contract_service_id and a.status='ACTIVE')))
            """;
    @Override public void validateInvoiceOrigins(UUID company, UUID contract) {
        Boolean invalid=jdbc.queryForObject("""
            select exists (select 1 from invoice i where i.company_id=:company and i.contract_id=:contract and i.status='REGISTERED' and
            """ + INVALID_INVOICE_ORIGINS + ")",params(company,contract),Boolean.class);
        requireReconciled(Boolean.TRUE.equals(invalid));
    }
    static void requireReconciled(boolean invalid) {
        if(invalid) throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,
            "Invoice origins require reconciliation before this report can be generated.",List.of(),"BILLING_REPORT_RECONCILIATION_REQUIRED");
    }
    @Override public List<Column> invoicePage(UUID company, UUID contract, int offset, int limit, boolean history) {
        var p=params(company,contract);p.put("offset",offset);p.put("limit",limit);
        return jdbc.query("select id,number,series,issued_on,status,total_amount from invoice where company_id=:company and contract_id=:contract "+
            (history?"":"and status='REGISTERED' ")+"order by issued_on,id limit :limit offset :offset",p,
            (r,n)->new Column(r.getObject("id",UUID.class),r.getString("number"),r.getString("series"),r.getDate("issued_on").toLocalDate(),r.getString("status"),r.getBigDecimal("total_amount")));
    }
    @Override public List<ServiceRow> servicePage(UUID company, UUID contract, UUID after, int limit,
            com.renovar.canteiro.io.contracts.domain.ContractNetAmount official) {
        var p=params(company,contract);p.put("after",after);p.put("limit",limit);
        p.put("net",official.netAmount());p.put("subtotal",official.serviceSubtotal());p.put("discount",official.contractDiscountAmount());
        return jdbc.query(BillingReportServicePositionSql.QUERY,p,(r,n)-> {
            var commercial=r.getBigDecimal("commercial");var issued=r.getBigDecimal("issued");
            return new ServiceRow(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),r.getString("status"),
                commercial,issued,commercial==null?null:commercial.subtract(issued));
        });
    }
    @Override public Map<CellKey,BigDecimal> cells(UUID company, UUID contract, List<UUID> invoices, List<UUID> services) {
        if(invoices.isEmpty()) return Map.of();
        var p=params(company,contract);p.put("invoices",invoices);
        String selection=" and ii.contract_service_id is null";
        if(!services.isEmpty()){p.put("services",services);selection=" and (ii.contract_service_id is null or ii.contract_service_id in (:services))";}
        Map<CellKey,BigDecimal> result=new HashMap<>();
        jdbc.query("select ii.contract_service_id,ii.invoice_id,sum(ii.allocated_amount) amount "+ITEMS+
            " and ii.invoice_id in (:invoices)"+selection+" group by ii.contract_service_id,ii.invoice_id",p,r->{
                result.put(new CellKey(r.getObject("contract_service_id",UUID.class),r.getObject("invoice_id",UUID.class)),r.getBigDecimal("amount"));
            });
        return Map.copyOf(result);
    }
    @Override public List<NoteRow> invoiceHistoryPage(UUID company, UUID contract, int offset, int limit) {
        var p=params(company,contract);p.put("offset",offset);p.put("limit",limit);
        return jdbc.query("""
            select distinct i.id,i.number,i.series,i.issued_on,i.status,i.total_amount,b.id installment_id,b.reference,snap.observed_at,snap.unreceived_cash_amount
            from invoice i left join invoice_financial_snapshot snap on snap.invoice_id=i.id and snap.company_id=i.company_id and snap.contract_id=i.contract_id
            left join invoice_item ii on ii.invoice_id=i.id and ii.company_id=i.company_id and ii.contract_id=i.contract_id
            left join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=ii.company_id and a.contract_id=ii.contract_id
            left join billing_installment b on b.id=a.billing_installment_id and b.company_id=a.company_id and b.contract_id=a.contract_id
            where i.company_id=:company and i.contract_id=:contract order by i.issued_on,i.id,b.id limit :limit offset :offset
            """,p,(r,n)->new NoteRow(new Column(r.getObject("id",UUID.class),r.getString("number"),r.getString("series"),r.getDate("issued_on").toLocalDate(),r.getString("status"),r.getBigDecimal("total_amount")),r.getString("reference"),r.getTimestamp("observed_at")==null?null:r.getTimestamp("observed_at").toInstant(),r.getBigDecimal("unreceived_cash_amount")));
    }
    @Override public List<OpenCharge> openChargePage(UUID company,UUID contract,int page,int size) {
        var filter=new com.renovar.canteiro.io.receivables.domain.ReceivableFilter(null,null,null,null,null,null,
            contract,null,null,null,null,null,null,com.renovar.canteiro.io.receivables.domain.ReceivableFinancialView.OUTSTANDING);
        var rows=receivables.findByCompanyId(company,filter,org.springframework.data.domain.PageRequest.of(page,size));
        if(rows.isEmpty()) return List.of();
        var p=params(company,contract);p.put("installments",rows.stream().map(r->r.billingInstallmentId()).distinct().toList());
        Map<UUID,Long> counts=new HashMap<>();
        jdbc.query("""
            select a.billing_installment_id,count(distinct i.id) count
            from billing_allocation a join invoice_item ii on ii.billing_allocation_id=a.id and ii.company_id=a.company_id and ii.contract_id=a.contract_id
            join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
            where a.company_id=:company and a.contract_id=:contract and a.billing_installment_id in (:installments)
                and a.status='ACTIVE' and ii.status='REGISTERED' and i.status='REGISTERED' group by a.billing_installment_id
            """,p,r->{counts.put(r.getObject("billing_installment_id",UUID.class),r.getLong("count"));});
        return rows.stream().map(r->new OpenCharge(r.reference(),r.dueOn(),r.originalAmount(),
            r.financialPosition().confirmedSettlementAmount(),r.financialPosition().outstandingAmount(),
            r.financialPosition().status().name(),r.renegotiationSourceReceivableId()==null?counts.getOrDefault(r.billingInstallmentId(),0L):null)).toList();
    }
}
