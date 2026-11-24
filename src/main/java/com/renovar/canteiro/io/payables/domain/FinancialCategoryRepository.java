package com.renovar.canteiro.io.payables.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface FinancialCategoryRepository {
    FinancialCategory save(FinancialCategory category);
    Optional<FinancialCategory> find(UUID company,UUID id);
    boolean codeExists(UUID company,String code);
    Page<FinancialCategory> find(UUID company,String name,FinancialCategoryType type,Boolean active,Pageable pageable);
}
