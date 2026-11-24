package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.FinancialCategory;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryRepository;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class JpaFinancialCategoryRepository implements FinancialCategoryRepository {
    private final FinancialCategoryJpaRepository repository;
    @Override public FinancialCategory save(FinancialCategory value) {
        var entity=value.id()==null?new FinancialCategoryJpaEntity(value):repository.findByIdAndCompanyId(value.id(),value.companyId()).orElseThrow();
        if(value.id()!=null&&entity.getVersion()!=value.version())throw new OptimisticLockingFailureException("Financial category changed concurrently");
        entity.apply(value);
        try{return repository.saveAndFlush(entity).domain();}
        catch(DataIntegrityViolationException e){throw new com.renovar.canteiro.io.payables.domain.PayableCatalogConflictException("Financial category code is already registered or conflicts with catalog integrity",e);}
    }
    @Override public Optional<FinancialCategory> find(UUID company,UUID id){return repository.findByIdAndCompanyId(id,company).map(FinancialCategoryJpaEntity::domain);}
    @Override public boolean codeExists(UUID company,String code){return repository.existsByCompanyIdAndCode(company,code);}
    @Override public Page<FinancialCategory> find(UUID company,String name,FinancialCategoryType type,Boolean active,Pageable pageable) {
        Specification<FinancialCategoryJpaEntity> filter=(root,query,builder)->builder.equal(root.get("companyId"),company);
        if(name!=null)filter=filter.and((root,query,builder)->builder.like(builder.lower(root.get("name")),literal(name),'!'));
        if(type!=null)filter=filter.and((root,query,builder)->builder.equal(root.get("type"),type));
        if(active!=null)filter=filter.and((root,query,builder)->builder.equal(root.get("active"),active));
        return repository.findAll(filter,pageable).map(FinancialCategoryJpaEntity::domain);
    }
    private String literal(String value){return "%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
