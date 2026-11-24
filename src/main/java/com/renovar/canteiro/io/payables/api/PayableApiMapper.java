package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.application.PayablePosition;
import com.renovar.canteiro.io.payables.domain.SupplierRepository;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryRepository;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.payables.api.PayableResponse.ReferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class PayableApiMapper {
    private final TenantContextHolder tenants;
    private final WorkRepository works;
    private final ContractRepository contracts;
    private final SupplierRepository suppliers;
    private final FinancialCategoryRepository categories;
    @Transactional(readOnly=true) public PayableResponse from(PayablePosition position){
        Payable p = position.payable();
        UUID company=tenants.requireCurrentTenant().companyId();
        if(!company.equals(p.getCompanyId()))throw new TenantResourceNotFoundException("Payable");
        var work=p.getWorkId()==null?null:works.findByIdAndCompanyId(p.getWorkId(),company).map(w->ref(w.getId(),w.getName(),w.getReference())).orElse(null);
        var contract=p.getContractId()==null?null:contracts.findByIdAndCompanyId(p.getContractId(),company).map(c->ref(c.getId(),c.getName(),c.getContractIdentifier()==null?c.getReference():c.getContractIdentifier())).orElse(null);
        var supplier=p.getSupplierId()==null?null:suppliers.find(company,p.getSupplierId()).map(s->ref(s.id(),s.name(),null)).orElse(null);
        var category=p.getFinancialCategoryId()==null?null:categories.find(company,p.getFinancialCategoryId()).map(c->ref(c.id(),c.name(),c.code())).orElse(null);
        return new PayableResponse(p.getId(),p.getFinancialGroupId(),p.getFinancialScope(),p.getWorkId(),p.getContractId(),p.getSupplierId(),p.getFinancialCategoryId(),p.getTaxObligationId(),
                p.getReference(),p.getDescription(),p.getIssuedOn(),p.getDueOn(),p.getCurrency(),p.getOriginalAmount(),p.getStatus(),
                position.overdueAmount() != null && position.overdueAmount().signum() > 0,
                p.getVersion(),p.getCreatedAt(),p.getUpdatedAt(),work,contract,supplier,category, PayableBalanceResponse.from(position));
    }
    private ReferenceResponse ref(UUID id,String name,String reference){return new ReferenceResponse(id,safe(name),safe(reference));}
    private String safe(String value){if(value==null)return null;String clean=value.replaceAll("[\\p{Cntrl}]"," ").trim();return clean.length()>255?clean.substring(0,255):clean;}
}
