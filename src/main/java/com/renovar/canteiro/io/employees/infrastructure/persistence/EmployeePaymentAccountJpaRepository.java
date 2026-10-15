package com.renovar.canteiro.io.employees.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EmployeePaymentAccountJpaRepository extends JpaRepository<EmployeePaymentAccountJpaEntity, UUID> {
    List<EmployeePaymentAccountJpaEntity> findByCompanyIdAndEmployeeIdAndActiveTrueOrderByCreatedAtAsc(UUID companyId, UUID employeeId);
    Optional<EmployeePaymentAccountJpaEntity> findByIdAndCompanyIdAndEmployeeId(UUID id, UUID companyId, UUID employeeId);
    List<EmployeePaymentAccountJpaEntity> findByEncryptionKeyVersion(String encryptionKeyVersion);
}
