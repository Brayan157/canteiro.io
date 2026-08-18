package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "employee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EmployeeJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    EmployeeJpaEntity(UUID companyId, String fullName, String jobTitle, String phone, boolean active, UUID userId) {
        this.companyId = companyId;
        this.fullName = fullName;
        this.jobTitle = jobTitle;
        this.phone = phone;
        this.active = active;
        this.userId = userId;
    }

    void linkUser(UUID userId) {
        this.userId = userId;
    }
}
