package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.payables.domain.Supplier;
import com.renovar.canteiro.io.payables.domain.SupplierRepository;
import com.renovar.canteiro.io.payables.domain.FinancialCategory;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryRepository;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import com.renovar.canteiro.io.payables.domain.PayableCatalogValues;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PayableCatalogService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final SupplierRepository suppliers;
    private final FinancialCategoryRepository categories;
    private final ChangeRequestService changes;
    private final AuditEventRecorder audit;

    @Transactional(readOnly=true) public Supplier supplier(UUID id){
        authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);return requireSupplier(company(),id);
    }
    @Transactional(readOnly=true) public FinancialCategory category(UUID id){
        authorization.requirePermission(AccessModule.COSTS_EXPENSES,AccessAction.READ);return requireCategory(company(),id);
    }
    @Transactional(readOnly=true) public Page<Supplier> suppliers(String name,String document,Boolean active,Pageable page){
        authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);
        return suppliers.find(company(),PayableCatalogValues.text(name,255,false),PayableCatalogValues.document(document),active,ordered(page));
    }
    @Transactional(readOnly=true) public Page<FinancialCategory> categories(String name,FinancialCategoryType type,Boolean active,Pageable page){
        authorization.requirePermission(AccessModule.COSTS_EXPENSES,AccessAction.READ);
        return categories.find(company(),PayableCatalogValues.text(name,255,false),type,active,ordered(page));
    }
    @Transactional public Result changeSupplier(UUID id,ChangeOperation operation,long expectedVersion,String name,String document,String justification){
        return changeSupplier(id,operation,expectedVersion,name,document,null,null,null,justification);
    }
    @Transactional public Result changeSupplier(UUID id,ChangeOperation operation,long expectedVersion,String name,String document,
            String contactName,String contactPhone,String contactEmail,String justification){
        var mode=authorization.requireChangeAuthorization(AccessModule.PAYABLES,operation);
        justification=PayableCatalogValues.text(justification,1000,false);
        UUID company=company();Supplier current=operation==ChangeOperation.CREATE?null:requireSupplier(company,id);
        if(current!=null)requireVersion(expectedVersion,current.version());
        Supplier proposed=operation==ChangeOperation.CREATE?Supplier.create(company,name,document):
                operation==ChangeOperation.CANCEL?current.deactivate():current.update(name,document);
        if(operation!=ChangeOperation.CANCEL)proposed=proposed.withContact(contactName,contactPhone,contactEmail);
        var data=supplierProposal(proposed);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL)return pending(AuditModule.PAYABLES,"Supplier",id,expectedVersion,operation,current==null?null:supplierAudit(current),data,justification);
        return saveSupplier(current,proposed,"direct",null);
    }
    @Transactional public Result changeCategory(UUID id,ChangeOperation operation,long expectedVersion,String code,String name,String description,FinancialCategoryType type,String justification){
        var mode=authorization.requireChangeAuthorization(AccessModule.COSTS_EXPENSES,operation);
        justification=PayableCatalogValues.text(justification,1000,false);
        UUID company=company();FinancialCategory current=operation==ChangeOperation.CREATE?null:requireCategory(company,id);
        if(current!=null)requireVersion(expectedVersion,current.version());
        FinancialCategory proposed=operation==ChangeOperation.CREATE?FinancialCategory.create(company,code,name,description,type):
                operation==ChangeOperation.CANCEL?current.deactivate():current.update(name,description);
        if(current==null&&categories.codeExists(company,proposed.code()))throw new IllegalStateException("Category code is already registered in this company");
        var data=categoryData(proposed);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL)return pending(AuditModule.COSTS_EXPENSES,"FinancialCategory",id,expectedVersion,operation,current==null?null:categoryAudit(current),data,justification);
        return saveCategory(current,proposed,"direct",null);
    }
    @Transactional public Result apply(ChangeRequest request){
        UUID company=company();if(!company.equals(request.getCompanyId()))throw new TenantResourceNotFoundException("Catalog proposal");
        Map<String,Object> p=request.getSnapshot().proposedData().values();
        if("Supplier".equals(request.getEntityType())) {
            Supplier current=request.getOperation()==ChangeRequestOperation.CREATE?null:requireSupplier(company,request.getEntityId());
            if(current!=null)requireVersion(request.getEntityVersion(),current.version());
            Supplier proposed=current==null?Supplier.create(company,(String)p.get("name"),(String)p.get("document")):
                    request.getOperation()==ChangeRequestOperation.CANCEL?current.deactivate():current.update((String)p.get("name"),(String)p.get("document"));
            if(request.getOperation()!=ChangeRequestOperation.CANCEL)proposed=proposed.withContact((String)p.get("contactName"),(String)p.get("contactPhone"),(String)p.get("contactEmail"));
            return saveSupplier(current,proposed,"approval",request.getId());
        }
        FinancialCategory current=request.getOperation()==ChangeRequestOperation.CREATE?null:requireCategory(company,request.getEntityId());
        if(current!=null)requireVersion(request.getEntityVersion(),current.version());
        FinancialCategory proposed=current==null?FinancialCategory.create(company,(String)p.get("code"),(String)p.get("name"),(String)p.get("description"),FinancialCategoryType.valueOf((String)p.get("type"))):
                request.getOperation()==ChangeRequestOperation.CANCEL?current.deactivate():current.update((String)p.get("name"),(String)p.get("description"));
        if(current==null&&categories.codeExists(company,proposed.code()))throw new IllegalStateException("Category code is already registered in this company");
        return saveCategory(current,proposed,"approval",request.getId());
    }
    private Result pending(AuditModule module,String entity,UUID id,long version,ChangeOperation op,Map<String,Object> before,Map<String,Object> proposed,String justification){
        var request=changes.create(new CreateChangeRequestCommand(module,ChangeRequestOperation.valueOf(op.name()),entity,id,version,
                new ChangeRequestSnapshot(before==null?null:new AuditPayload(before),new AuditPayload(proposed)),justification));
        return new Result(null,null,request,ChangeAuthorizationMode.REQUEST_APPROVAL);
    }
    private Result saveSupplier(Supplier before,Supplier proposed,String origin,UUID request){
        Supplier saved=suppliers.save(proposed);
        var after=new LinkedHashMap<>(supplierAudit(saved));after.put("documentChanged",before!=null&&!java.util.Objects.equals(before.document(),saved.document()));
        audit.recordDirectAction(AuditModule.PAYABLES,action(before==null,saved.active()),"Supplier",saved.id(),before==null?null:supplierAudit(before),after,metadata(origin,request));
        return new Result(saved,null,null,ChangeAuthorizationMode.DIRECT);
    }
    private Result saveCategory(FinancialCategory before,FinancialCategory proposed,String origin,UUID request){
        FinancialCategory saved=categories.save(proposed);
        audit.recordDirectAction(AuditModule.COSTS_EXPENSES,action(before==null,saved.active()),"FinancialCategory",saved.id(),before==null?null:categoryAudit(before),categoryAudit(saved),metadata(origin,request));
        return new Result(null,saved,null,ChangeAuthorizationMode.DIRECT);
    }
    private AuditAction action(boolean created,boolean active){return created?AuditAction.CREATE:active?AuditAction.UPDATE:AuditAction.CANCEL;}
    private Map<String,Object> supplierProposal(Supplier value){var data=new LinkedHashMap<>(supplierAudit(value));data.put("document",value.document());data.put("contactName",value.contactName());data.put("contactPhone",value.contactPhone());data.put("contactEmail",value.contactEmail());return data;}
    private Map<String,Object> supplierAudit(Supplier value){return Map.of("name",value.name(),"active",value.active(),"hasDocument",value.document()!=null,"hasContact",value.contactName()!=null||value.contactPhone()!=null||value.contactEmail()!=null,"version",value.version());}
    private Map<String,Object> categoryData(FinancialCategory value){
        Map<String,Object> data=new LinkedHashMap<>();data.put("code",value.code());data.put("name",value.name());data.put("description",value.description());
        data.put("type",value.type().name());data.put("active",value.active());data.put("version",value.version());return data;
    }
    private Map<String,Object> categoryAudit(FinancialCategory value){return Map.of("code",value.code(),"name",value.name(),"type",value.type().name(),"active",value.active(),"version",value.version(),"hasDescription",value.description()!=null);}
    private Map<String,Object> metadata(String origin,UUID request){return request==null?Map.of("origin",origin):Map.of("origin",origin,"changeRequestId",request.toString());}
    private UUID company(){return tenants.requireCurrentTenant().companyId();}
    private Supplier requireSupplier(UUID company,UUID id){return suppliers.find(company,id).orElseThrow(()->new TenantResourceNotFoundException("Supplier"));}
    private FinancialCategory requireCategory(UUID company,UUID id){return categories.find(company,id).orElseThrow(()->new TenantResourceNotFoundException("Financial category"));}
    private void requireVersion(long expected,long current){if(expected<0)throw new IllegalArgumentException("Version must be non-negative");if(expected!=current)throw new IllegalStateException("Catalog record changed; reload its current version");}
    private Pageable ordered(Pageable page){
        if(page.getOffset()>Integer.MAX_VALUE)throw new IllegalArgumentException("Page offset is too large");
        var sort=page.getSort().isSorted()?page.getSort():Sort.by("name");
        return PageRequest.of(page.getPageNumber(),page.getPageSize(),sort.and(Sort.by("id")));
    }
    public record Result(Supplier supplier,FinancialCategory category,ChangeRequest changeRequest,ChangeAuthorizationMode mode) { }
}
