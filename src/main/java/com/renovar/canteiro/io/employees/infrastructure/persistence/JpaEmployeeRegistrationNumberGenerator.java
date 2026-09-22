package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.EmployeeRegistrationNumberGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.text.Normalizer;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaEmployeeRegistrationNumberGenerator implements EmployeeRegistrationNumberGenerator {

    private final EntityManager entityManager;

    @Override
    public String nextRegistrationNumber(UUID companyId, String companyName) {
        Number sequence = (Number) entityManager.createNativeQuery(
                """
                INSERT INTO employee_registration_sequence (company_id, next_number)
                VALUES (CAST(:companyId AS uuid), 2)
                ON CONFLICT (company_id) DO UPDATE SET next_number = employee_registration_sequence.next_number + 1
                RETURNING next_number - 1
                """
        ).setParameter("companyId", companyId.toString()).getSingleResult();
        return "%s-%03d".formatted(companyPrefix(companyName), sequence.longValue());
    }

    private String companyPrefix(String companyName) {
        String normalized = Normalizer.normalize(companyName == null ? "" : companyName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase();
        if (normalized.isEmpty()) {
            return "EMP";
        }
        return normalized.length() >= 3 ? normalized.substring(0, 3) : normalized;
    }
}
