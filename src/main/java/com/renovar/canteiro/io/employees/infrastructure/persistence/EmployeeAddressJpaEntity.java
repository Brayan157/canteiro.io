package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter @Entity @Table(name = "employee_address") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeAddressJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "label") private String label;
    @Column(name = "postal_code") private String postalCode;
    @Column(name = "street", nullable = false) private String street;
    @Column(name = "number") private String number;
    @Column(name = "complement") private String complement;
    @Column(name = "district") private String district;
    @Column(name = "city", nullable = false) private String city;
    @Column(name = "state") private String state;
    @Column(name = "country", nullable = false) private String country;
    @Column(name = "primary_address", nullable = false) private boolean primaryAddress;
    @Column(nullable = false) private boolean active;
    public EmployeeAddressJpaEntity(UUID companyId, UUID employeeId, String label, String postalCode, String street, String number,
                             String complement, String district, String city, String state, String country, boolean primaryAddress) {
        this.companyId=companyId; this.employeeId=employeeId; this.label=label; this.postalCode=postalCode; this.street=street;
        this.number=number; this.complement=complement; this.district=district; this.city=city; this.state=state; this.country=country; this.primaryAddress=primaryAddress; this.active=true;
    }
    public void update(String label, String postalCode, String street, String number, String complement, String district,
                       String city, String state, String country, boolean primaryAddress) {
        this.label=label; this.postalCode=postalCode; this.street=street; this.number=number; this.complement=complement;
        this.district=district; this.city=city; this.state=state; this.country=country; this.primaryAddress=primaryAddress;
    }
    public void setPrimaryAddress(boolean primaryAddress) { this.primaryAddress=primaryAddress; }
    public void deactivate() { this.active=false; }
}
