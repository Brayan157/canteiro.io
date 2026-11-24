package com.renovar.canteiro.io.measurements.infrastructure.persistence;

/** Shared by the billing command guard and the selectable-origin query. Alias c is a composition. */
public final class MeasurementCompositionEligibilitySql {
    public static final String UNRECONCILED_REDUCTION = """
            exists (
                select 1 from measurement_service source_group
                where source_group.id = c.measurement_service_id and source_group.company_id = c.company_id
                and coalesce((select sum(r.net_amount) from measurement_item_recognition r
                    where r.company_id = c.company_id and r.continuous_service_id = source_group.continuous_service_id
                    and r.active = true and not exists (
                        select 1 from measurement_item_recognition n where n.company_id = r.company_id
                        and n.root_item_id = r.root_item_id and n.version_number > r.version_number)), 0)
                < (select coalesce(sum(p.net_amount), 0) from measurement_contract_composition p
                    join measurement_service pg on pg.id = p.measurement_service_id and pg.company_id = p.company_id
                    where p.company_id = c.company_id and pg.continuous_service_id = source_group.continuous_service_id
                    and not exists (select 1 from measurement_composition_adjustment a
                        where a.company_id = p.company_id and a.composition_id = p.id))
            )
            """;

    private MeasurementCompositionEligibilitySql() {
    }
}
