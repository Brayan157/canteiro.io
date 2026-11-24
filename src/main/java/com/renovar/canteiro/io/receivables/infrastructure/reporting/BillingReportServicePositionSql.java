package com.renovar.canteiro.io.receivables.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.infrastructure.persistence.MeasurementCompositionEligibilitySql;

/** Bounded projection equivalent to NetBillingDistribution: floor, then reverse-key residual. */
final class BillingReportServicePositionSql {
    private BillingReportServicePositionSql() { }
    static final String QUERY = """
        with active as (
            select * from billing_allocation where company_id=:company and contract_id=:contract and status='ACTIVE'
        ), used as (
            select contract_service_id,measurement_contract_composition_id,
                sum(coalesce(net_billing_gross_amount,allocated_amount)) gross,sum(allocated_amount) net
            from active where allocation_type='SERVICE' group by contract_service_id,measurement_contract_composition_id
        ), totals as (
            select coalesce(sum(allocated_amount),0) net,
                coalesce(sum(case when allocation_type='SERVICE' then coalesce(net_billing_gross_amount,allocated_amount) else 0 end),0) gross,
                coalesce(bool_or(allocation_type<>'SERVICE'),false) legacy from active
        ), positions as (
            select s.id service_id,('BASE/'||s.id::text) collate "C" key,s.net_amount-coalesce(u.gross,0) weight,
                (ct.status<>'CANCELLED' and not exists(select 1 from measurement_item_contract_service_conversion x
                    where x.company_id=s.company_id and x.contract_id=s.contract_id and x.contract_service_id=s.id)) eligible
            from contract_service s join contract ct on ct.id=s.contract_id and ct.company_id=s.company_id
            left join used u on u.contract_service_id=s.id and u.measurement_contract_composition_id is null
            where s.company_id=:company and s.contract_id=:contract and s.status<>'CANCELLED'
            union all
            select s.id,('COMPOSITION/'||s.id::text||'/'||c.id::text) collate "C",c.net_amount-coalesce(u.gross,0),
                (ct.status<>'CANCELLED' and v.status='ACCEPTED' and m.status<>'CANCELLED' and not (
        """ + MeasurementCompositionEligibilitySql.UNRECONCILED_REDUCTION + """
                )) from measurement_contract_composition c
            join contract_service s on s.id=c.contract_service_id and s.company_id=c.company_id and s.contract_id=c.contract_id
            join contract ct on ct.id=c.contract_id and ct.company_id=c.company_id
            join measurement_version v on v.id=c.measurement_version_id and v.company_id=c.company_id
            join measurement m on m.id=c.measurement_id and m.company_id=c.company_id
            left join used u on u.contract_service_id=s.id and u.measurement_contract_composition_id=c.id
            where c.company_id=:company and c.contract_id=:contract and s.status<>'CANCELLED'
                and not exists(select 1 from measurement_composition_adjustment a where a.company_id=c.company_id and a.composition_id=c.id)
        ), weights as (
            select coalesce(sum(weight),0) total,coalesce(min(weight),0) minimum from positions
        ), floors as (
            select p.*,case when w.total=0 then 0 else trunc(p.weight*(:net-t.net)/nullif(w.total,0),2) end share
            from positions p cross join weights w cross join totals t
        ), residuals as (
            select f.*,(:net-t.net)-sum(share) over() residual,
                coalesce(sum(weight-share) over(order by key desc rows between unbounded preceding and 1 preceding),0) above
            from floors f cross join totals t
        ), distributed as (
            select service_id,sum(share+least(weight-share,greatest(residual-above,0))) net,
                bool_and(eligible or weight=0) eligible from residuals group by service_id
        ), checks as (
            select not t.legacy and w.minimum>=0 and w.total=:subtotal-t.gross
                and :discount-(t.gross-t.net)>=0 and :net-t.net>=0 and :net-t.net<=w.total valid
            from totals t cross join weights w
        ), issued as (
            select ii.contract_service_id,sum(ii.allocated_amount) amount
            from invoice_item ii join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
            join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=ii.company_id and a.contract_id=ii.contract_id
                and a.contract_service_id is not distinct from ii.contract_service_id
            where ii.company_id=:company and ii.contract_id=:contract and ii.status='REGISTERED' and i.status='REGISTERED' and a.status='ACTIVE'
            group by ii.contract_service_id
        )
        select s.id,s.name,s.description,s.status,
            case when ck.valid and coalesce(d.eligible,s.status<>'CANCELLED') then coalesce(u.net,0)+coalesce(d.net,0) end commercial,
            coalesce(i.amount,0) issued
        from contract_service s cross join checks ck left join distributed d on d.service_id=s.id
        left join (select contract_service_id,sum(net) net from used group by contract_service_id) u on u.contract_service_id=s.id
        left join issued i on i.contract_service_id=s.id
        where s.company_id=:company and s.contract_id=:contract and (cast(:after as uuid) is null or s.id>cast(:after as uuid))
        order by s.id limit :limit
        """;
}
