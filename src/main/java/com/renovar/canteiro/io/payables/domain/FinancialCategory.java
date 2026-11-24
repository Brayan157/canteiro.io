package com.renovar.canteiro.io.payables.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FinancialCategory(UUID id,UUID companyId,String code,String name,String description,FinancialCategoryType type,
        boolean active,long version,Instant createdAt,Instant updatedAt) {
    public FinancialCategory {
        Objects.requireNonNull(companyId,"Company is required");Objects.requireNonNull(type,"Category classification is required");
        code=PayableCatalogValues.code(code);name=PayableCatalogValues.text(name,255,true);description=PayableCatalogValues.text(description,1000,false);
        if(version<0)throw new IllegalArgumentException("Version must be non-negative");
    }
    public static FinancialCategory create(UUID company,String code,String name,String description,FinancialCategoryType type){
        return new FinancialCategory(null,company,code,name,description,type,true,0,null,null);
    }
    public FinancialCategory update(String name,String description){requireActive();return new FinancialCategory(id,companyId,code,name,description,type,true,version,createdAt,updatedAt);}
    public FinancialCategory deactivate(){requireActive();return new FinancialCategory(id,companyId,code,name,description,type,false,version,createdAt,updatedAt);}
    private void requireActive(){if(!active)throw new IllegalStateException("Inactive category cannot be changed");}
}
