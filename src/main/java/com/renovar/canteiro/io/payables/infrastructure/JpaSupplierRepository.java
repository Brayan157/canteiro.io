package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.Supplier;
import com.renovar.canteiro.io.payables.domain.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class JpaSupplierRepository implements SupplierRepository {
    private final SupplierJpaRepository repository;
    @Override public Supplier save(Supplier value) {
        var entity=value.id()==null?new SupplierJpaEntity(value):repository.findByIdAndCompanyId(value.id(),value.companyId()).orElseThrow();
        if(value.id()!=null&&entity.getVersion()!=value.version())throw new OptimisticLockingFailureException("Supplier changed concurrently");
        entity.apply(value);return repository.saveAndFlush(entity).domain();
    }
    @Override public Optional<Supplier> find(UUID company,UUID id){return repository.findByIdAndCompanyId(id,company).map(SupplierJpaEntity::domain);}
    @Override public Page<Supplier> find(UUID company,String name,String document,Boolean active,Pageable pageable) {
        Specification<SupplierJpaEntity> filter=(root,query,builder)->builder.equal(root.get("companyId"),company);
        if(name!=null)filter=filter.and((root,query,builder)->builder.like(builder.lower(root.get("name")),literal(name),'!'));
        if(document!=null)filter=filter.and((root,query,builder)->builder.equal(root.get("document"),document));
        if(active!=null)filter=filter.and((root,query,builder)->builder.equal(root.get("active"),active));
        return repository.findAll(filter,pageable).map(SupplierJpaEntity::domain);
    }
    private String literal(String value){return "%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
