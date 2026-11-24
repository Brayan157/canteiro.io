package com.renovar.canteiro.io.payables.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {
    Supplier save(Supplier supplier);
    Optional<Supplier> find(UUID company,UUID id);
    Page<Supplier> find(UUID company,String name,String document,Boolean active,Pageable pageable);
}
