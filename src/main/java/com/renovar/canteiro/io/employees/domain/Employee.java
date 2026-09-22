package com.renovar.canteiro.io.employees.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Getter
public final class Employee {

    private final UUID id;
    private final UUID companyId;
    private final String fullName;
    private final String preferredName;
    private final String jobTitle;
    private final String department;
    private final String phone;
    private final String registrationNumber;
    private final EmployeeDocumentType documentType;
    private final String documentNumber;
    private final String pis;
    private final LocalDate birthDate;
    private final EmployeeEmploymentType employmentType;
    private final boolean currentlyEmployed;
    private final EmployeeGender gender;
    private final EmployeeEthnicity ethnicity;
    private final LocalDate employmentStartDate;
    private final LocalDate employmentEndDate;
    private final EmployeeCompensationFrequency compensationFrequency;
    private final BigDecimal compensationAmount;
    private final boolean active;
    private String photoStorageKey;
    private UUID userId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Employee(UUID id, UUID companyId, String fullName, String preferredName, String jobTitle, String department,
                     String phone, String registrationNumber, EmployeeDocumentType documentType, String documentNumber,
                     String pis, LocalDate birthDate, EmployeeEmploymentType employmentType, LocalDate employmentStartDate,
                     LocalDate employmentEndDate, boolean currentlyEmployed, EmployeeGender gender,
                     EmployeeEthnicity ethnicity, EmployeeCompensationFrequency compensationFrequency,
                     BigDecimal compensationAmount, boolean active, String photoStorageKey, UUID userId,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Employee company is required");
        this.fullName = requireText(fullName, "Employee full name is required");
        this.preferredName = normalize(preferredName);
        this.jobTitle = requireText(jobTitle, "Employee job title is required");
        this.department = normalize(department);
        this.phone = normalize(phone);
        this.registrationNumber = normalize(registrationNumber);
        this.documentType = documentType;
        this.documentNumber = normalizeDocumentNumber(documentNumber);
        validateDocument(documentType, this.documentNumber);
        this.pis = normalizePis(pis);
        this.birthDate = require(birthDate, "Employee birth date is required");
        this.employmentType = employmentType;
        this.currentlyEmployed = currentlyEmployed;
        this.gender = gender;
        this.ethnicity = ethnicity;
        this.employmentStartDate = require(employmentStartDate, "Employee employment start date is required");
        this.employmentEndDate = employmentEndDate;
        validateEmploymentDates(employmentStartDate, employmentEndDate, currentlyEmployed);
        this.compensationFrequency = compensationFrequency;
        this.compensationAmount = validateSalary(compensationAmount);
        this.active = active;
        this.photoStorageKey = normalize(photoStorageKey);
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Employee create(UUID companyId, String fullName, String preferredName, String jobTitle,
                                  String department, String phone, String registrationNumber,
                                  EmployeeDocumentType documentType, String documentNumber, String pis, LocalDate birthDate,
                                  EmployeeEmploymentType employmentType, LocalDate employmentStartDate,
                                  LocalDate employmentEndDate, boolean currentlyEmployed, EmployeeGender gender,
                                  EmployeeEthnicity ethnicity, EmployeeCompensationFrequency compensationFrequency,
                                  BigDecimal compensationAmount) {
        requireIdentificationDocument(documentType, documentNumber);
        return new Employee(null, companyId, fullName, preferredName, jobTitle, department, phone,
                registrationNumber, documentType, documentNumber, pis, birthDate, employmentType, employmentStartDate,
                employmentEndDate, currentlyEmployed, gender, ethnicity, compensationFrequency, compensationAmount,
                true, null, null, null, null);
    }

    public static Employee rehydrate(UUID id, UUID companyId, String fullName, String preferredName, String jobTitle,
                                     String department, String phone, String registrationNumber,
                                     EmployeeDocumentType documentType, String documentNumber, String pis, LocalDate birthDate,
                                     EmployeeEmploymentType employmentType, LocalDate employmentStartDate,
                                     LocalDate employmentEndDate, boolean currentlyEmployed, EmployeeGender gender,
                                     EmployeeEthnicity ethnicity, EmployeeCompensationFrequency compensationFrequency,
                                     BigDecimal compensationAmount, boolean active, String photoStorageKey,
                                     UUID userId, Instant createdAt, Instant updatedAt) {
        return new Employee(id, companyId, fullName, preferredName, jobTitle, department, phone,
                registrationNumber, documentType, documentNumber, pis, birthDate, employmentType, employmentStartDate,
                employmentEndDate, currentlyEmployed, gender, ethnicity, compensationFrequency, compensationAmount,
                active, photoStorageKey, userId, createdAt, updatedAt);
    }
    public Employee updateProfile(String fullName, String preferredName, String jobTitle, String department, String phone,
                                  EmployeeDocumentType documentType, String documentNumber, String pis, LocalDate birthDate,
                                  EmployeeEmploymentType employmentType, LocalDate employmentStartDate, LocalDate employmentEndDate,
                                  boolean currentlyEmployed, EmployeeGender gender, EmployeeEthnicity ethnicity,
                                  EmployeeCompensationFrequency compensationFrequency, BigDecimal compensationAmount) {
        requireIdentificationDocument(documentType, documentNumber);
        return new Employee(id, companyId, fullName, preferredName, jobTitle, department, phone, registrationNumber,
                documentType, documentNumber, pis, birthDate, employmentType, employmentStartDate, employmentEndDate,
                currentlyEmployed, gender, ethnicity, compensationFrequency, compensationAmount, active, photoStorageKey, userId, createdAt, updatedAt);
    }

    public String getDocumentNumberMasked() {
        if (documentNumber == null) {
            return null;
        }
        if (documentType == EmployeeDocumentType.CPF && documentNumber.length() == 11) {
            return "***.***.***-" + documentNumber.substring(9);
        }
        int visibleLength = Math.min(4, documentNumber.length());
        return "***" + documentNumber.substring(documentNumber.length() - visibleLength);
    }

    public String getPisMasked() {
        return pis == null ? null : "***.*****." + pis.substring(8, 10) + "-" + pis.substring(10);
    }

    public void linkUser(UUID userId) {
        require(userId, "Employee user is required");
        if (!active) {
            throw new IllegalStateException("An inactive employee cannot receive system access");
        }
        if (this.userId != null) {
            throw new IllegalStateException("Employee already has a system user");
        }
        this.userId = userId;
    }

    public void updatePhotoStorageKey(String photoStorageKey) {
        this.photoStorageKey = normalize(photoStorageKey);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeDocumentNumber(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("[\\s./-]", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizePis(String value) {
        String normalized = normalizeDocumentNumber(value);
        if (normalized == null) return null;
        if (!normalized.matches("\\d{11}")) throw new IllegalArgumentException("Employee PIS must contain 11 digits");
        return normalized;
    }

    private static void validateDocument(EmployeeDocumentType documentType, String documentNumber) {
        if ((documentType == null) != (documentNumber == null)) {
            throw new IllegalArgumentException("Employee document type and number must be informed together");
        }
        if (documentType == EmployeeDocumentType.CPF && !isValidCpf(documentNumber)) {
            throw new IllegalArgumentException("Employee CPF is invalid");
        }
    }

    private static void requireIdentificationDocument(EmployeeDocumentType documentType, String documentNumber) {
        if (documentType == null || normalizeDocumentNumber(documentNumber) == null) {
            throw new IllegalArgumentException("At least one employee identification document is required");
        }
    }

    private static boolean isValidCpf(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return cpfDigit(cpf, 9) == cpf.charAt(9) - '0'
                && cpfDigit(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int cpfDigit(String cpf, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += (cpf.charAt(index) - '0') * (length + 1 - index);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static void validateEmploymentDates(LocalDate employmentStartDate, LocalDate employmentEndDate,
                                                boolean currentlyEmployed) {
        if (employmentStartDate != null && employmentEndDate != null
                && employmentEndDate.isBefore(employmentStartDate)) {
            throw new IllegalArgumentException("Employee employment end date cannot precede the start date");
        }
        if (!currentlyEmployed && employmentEndDate == null) {
            throw new IllegalArgumentException("Employee employment end date is required when no longer employed");
        }
    }

    private static BigDecimal validateSalary(BigDecimal compensationAmount) {
        if (compensationAmount != null && compensationAmount.signum() <= 0) {
            throw new IllegalArgumentException("Employee compensation amount must be positive");
        }
        return compensationAmount;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
