package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.FinancialCategory;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity @Table(name="financial_category") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
class FinancialCategoryJpaEntity extends BaseJpaEntity {
    @Column(name="company_id",nullable=false,updatable=false) private UUID companyId;
    @Column(nullable=false,updatable=false,length=50) private String code;
    @Column(nullable=false,length=255) private String name;
    @Column(length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(name="category_type",nullable=false,updatable=false,length=30) private FinancialCategoryType type;
    @Column(nullable=false) private boolean active;
    @Version @Column(nullable=false) private Long version;
    FinancialCategoryJpaEntity(FinancialCategory value){companyId=value.companyId();code=value.code();type=value.type();apply(value);}
    void apply(FinancialCategory value){name=value.name();description=value.description();active=value.active();}
    FinancialCategory domain(){return new FinancialCategory(getId(),companyId,code,name,description,type,active,version==null?0:version,getCreatedAt(),getUpdatedAt());}
}
