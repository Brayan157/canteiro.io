package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement_contract_composition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MeasurementContractCompositionJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "measurement_id", nullable = false, updatable = false) private UUID measurementId;
    @Column(name = "measurement_version_id", nullable = false, updatable = false) private UUID measurementVersionId;
    @Column(name = "measurement_service_id", nullable = false, updatable = false) private UUID measurementServiceId;
    @Column(name = "contract_id", nullable = false, updatable = false) private UUID contractId;
    @Column(name = "contract_service_id", nullable = false, updatable = false) private UUID contractServiceId;
    @Enumerated(EnumType.STRING) @Column(name = "origin_type", nullable = false, updatable = false, length = 40) private MeasurementCompositionOriginType originType;
    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal grossAmount;
    @Column(name = "net_amount", nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal netAmount;
    @Column(name = "total_weight_kg", updatable = false, precision = 18, scale = 4) private BigDecimal totalWeightKg;
    @Column(name = "item_count", nullable = false, updatable = false) private int itemCount;
    @Column(name = "justification", updatable = false, length = 2000) private String justification;
    @Column(name = "created_by_user_id", updatable = false) private UUID createdByUserId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "measurement_contract_composition_item", joinColumns = {
            @JoinColumn(name = "composition_id", referencedColumnName = "id"),
            @JoinColumn(name = "company_id", referencedColumnName = "company_id")
    })
    @Column(name = "measurement_item_id", nullable = false)
    private List<UUID> measurementItemIds = new ArrayList<>();

    MeasurementContractCompositionJpaEntity(MeasurementContractComposition composition) {
        this.companyId = composition.getCompanyId(); this.measurementId = composition.getMeasurementId();
        this.measurementVersionId = composition.getMeasurementVersionId(); this.measurementServiceId = composition.getMeasurementServiceId();
        this.contractId = composition.getContractId(); this.contractServiceId = composition.getContractServiceId();
        this.originType = composition.getOriginType(); this.grossAmount = composition.getGrossAmount(); this.netAmount = composition.getNetAmount();
        this.totalWeightKg = composition.getTotalWeightKg(); this.itemCount = composition.getItemCount();
        this.justification = composition.getJustification(); this.createdByUserId = composition.getCreatedByUserId();
        this.measurementItemIds.addAll(composition.getMeasurementItemIds());
    }
}
