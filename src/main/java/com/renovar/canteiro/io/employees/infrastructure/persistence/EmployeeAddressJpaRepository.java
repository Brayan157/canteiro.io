package com.renovar.canteiro.io.employees.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EmployeeAddressJpaRepository extends JpaRepository<EmployeeAddressJpaEntity, UUID> {
    List<EmployeeAddressJpaEntity> findByCompanyIdAndEmployeeIdAndActiveTrueOrderByPrimaryAddressDescCreatedAtAsc(UUID companyId, UUID employeeId);
    List<EmployeeAddressJpaEntity> findByCompanyIdAndEmployeeIdAndActiveTrueAndPrimaryAddressTrue(UUID companyId, UUID employeeId);
    Optional<EmployeeAddressJpaEntity> findByIdAndCompanyIdAndEmployeeId(UUID id, UUID companyId, UUID employeeId);
}
