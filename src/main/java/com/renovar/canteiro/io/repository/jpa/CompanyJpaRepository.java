package com.renovar.canteiro.io.repository.jpa;

import com.renovar.canteiro.io.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyJpaRepository extends JpaRepository<Company, UUID> {
}
