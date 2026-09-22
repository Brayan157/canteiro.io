package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeEthnicity;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;

@Getter
@Entity
@Table(name = "employee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EmployeeJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "preferred_name", length = 255)
    private String preferredName;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "document_type", length = 30)
    private EmployeeDocumentType documentType;

    @Column(name = "document_number", length = 64)
    private String documentNumber;

    @Column(name = "pis", length = 11)
    private String pis;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private EmployeeEmploymentType employmentType;

    @Column(name = "currently_employed", nullable = false)
    private boolean currentlyEmployed;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "gender", length = 30)
    private EmployeeGender gender;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "ethnicity", length = 30)
    private EmployeeEthnicity ethnicity;

    @Column(name = "employment_start_date")
    private LocalDate employmentStartDate;

    @Column(name = "employment_end_date")
    private LocalDate employmentEndDate;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "compensation_frequency", length = 30)
    private EmployeeCompensationFrequency compensationFrequency;

    @Column(name = "compensation_amount", precision = 14, scale = 2)
    private BigDecimal compensationAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "photo_storage_key", length = 255)
    private String photoStorageKey;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    EmployeeJpaEntity(UUID companyId, String fullName, String preferredName, String jobTitle, String department,
                      String phone, String registrationNumber, EmployeeDocumentType documentType, String documentNumber,
                      String pis, LocalDate birthDate, EmployeeEmploymentType employmentType, LocalDate employmentStartDate,
                      LocalDate employmentEndDate, boolean currentlyEmployed, EmployeeGender gender,
                      EmployeeEthnicity ethnicity, EmployeeCompensationFrequency compensationFrequency,
                      BigDecimal compensationAmount, boolean active, String photoStorageKey, UUID userId) {
        this.companyId = companyId;
        this.fullName = fullName;
        this.preferredName = preferredName;
        this.jobTitle = jobTitle;
        this.department = department;
        this.phone = phone;
        this.registrationNumber = registrationNumber;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.pis = pis;
        this.birthDate = birthDate;
        this.employmentType = employmentType;
        this.employmentStartDate = employmentStartDate;
        this.employmentEndDate = employmentEndDate;
        this.currentlyEmployed = currentlyEmployed;
        this.gender = gender;
        this.ethnicity = ethnicity;
        this.compensationFrequency = compensationFrequency;
        this.compensationAmount = compensationAmount;
        this.active = active;
        this.photoStorageKey = photoStorageKey;
        this.userId = userId;
    }

    void linkUser(UUID userId) {
        this.userId = userId;
    }
    void updatePhotoStorageKey(String photoStorageKey) { this.photoStorageKey = photoStorageKey; }
    void updateProfile(EmployeeJpaEntity source) { this.fullName=source.fullName; this.preferredName=source.preferredName; this.jobTitle=source.jobTitle; this.department=source.department; this.phone=source.phone; this.documentType=source.documentType; this.documentNumber=source.documentNumber; this.pis=source.pis; this.birthDate=source.birthDate; this.employmentType=source.employmentType; this.currentlyEmployed=source.currentlyEmployed; this.gender=source.gender; this.ethnicity=source.ethnicity; this.employmentStartDate=source.employmentStartDate; this.employmentEndDate=source.employmentEndDate; this.compensationFrequency=source.compensationFrequency; this.compensationAmount=source.compensationAmount; }
}
