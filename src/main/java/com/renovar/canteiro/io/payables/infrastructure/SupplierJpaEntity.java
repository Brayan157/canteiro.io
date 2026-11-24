package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.Supplier;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity @Table(name="supplier") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
class SupplierJpaEntity extends BaseJpaEntity {
    @Column(name="company_id",nullable=false,updatable=false) private UUID companyId;
    @Column(nullable=false,length=255) private String name;
    @Column(length=50) private String document;
    @Column(name="contact_name",length=120) private String contactName;
    @Column(name="contact_phone",length=40) private String contactPhone;
    @Column(name="contact_email",length=254) private String contactEmail;
    @Column(nullable=false) private boolean active;
    @Version @Column(nullable=false) private Long version;
    SupplierJpaEntity(Supplier value){companyId=value.companyId();apply(value);}
    void apply(Supplier value){name=value.name();document=value.document();active=value.active();contactName=value.contactName();contactPhone=value.contactPhone();contactEmail=value.contactEmail();}
    Supplier domain(){return new Supplier(getId(),companyId,name,document,contactName,contactPhone,contactEmail,active,version==null?0:version,getCreatedAt(),getUpdatedAt());}
}
