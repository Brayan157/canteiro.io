package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableFilter;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.payables.domain.PayableSettlementRepository;
import com.renovar.canteiro.io.payables.domain.SupplierRepository;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service @RequiredArgsConstructor
public class PayableService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final PayableRepository payables;
    private final PayableSettlementRepository settlements;
    private final Clock clock;
    private final SupplierRepository suppliers;
    private final FinancialCategoryRepository categories;
    private final WorkRepository works;
    private final ContractRepository contracts;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final AuditEventRecorder audit;
    private final tools.jackson.databind.ObjectMapper json;

    @Transactional public Result create(PayableCreateCommand command){
        var mode=authorization.requireChangeAuthorization(AccessModule.PAYABLES,ChangeOperation.CREATE);
        UUID company=company();payables.lockCreation(company,command.operationId());
        String hash=fingerprint(command);
        var previous=payables.findCreation(company,command.operationId());
        if(previous.isPresent()){
            requireHash(previous.get().payloadHash(),hash);
            return new Result(previous.get().id(),null,ChangeAuthorizationMode.DIRECT,true);
        }
        var pending=requests.findPendingByEntity(company,"PayableCreation",command.operationId());
        if(!pending.isEmpty()){
            ChangeRequest request=pending.getFirst();
            requireHash((String)request.getSnapshot().proposedData().values().get("payloadHash"),hash);
            return new Result(null,request,ChangeAuthorizationMode.REQUEST_APPROVAL,true);
        }
        CurrencyCode currency=validate(company,command);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL){
            var proposal=proposal(command);proposal.put("payloadHash",hash);
            var request=changes.create(new CreateChangeRequestCommand(AuditModule.PAYABLES,ChangeRequestOperation.CREATE,
                    "PayableCreation",command.operationId(),0,new ChangeRequestSnapshot(null,new AuditPayload(proposal)),command.justification()));
            return new Result(null,request,mode,false);
        }
        return persist(company,command,currency,hash,null);
    }

    @Transactional public Result apply(ChangeRequest request){
        if(!company().equals(request.getCompanyId()))throw new TenantResourceNotFoundException("Payable proposal");
        var command=fromProposal(request.getSnapshot().proposedData().values());
        if(!command.operationId().equals(request.getEntityId()))throw new IllegalStateException("Payable proposal identity mismatch");
        payables.lockCreation(company(),command.operationId());
        String hash=fingerprint(command);requireHash((String)request.getSnapshot().proposedData().values().get("payloadHash"),hash);
        if(payables.findCreation(company(),command.operationId()).isPresent())throw new IllegalStateException("Payable creation was already applied");
        return persist(company(),command,validate(company(),command),hash,request.getId());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<PayablePosition> list(PayableFilter filter,Pageable page){
        authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);
        if(page.getOffset()>Integer.MAX_VALUE)throw new IllegalArgumentException("Page offset is too large");
        var sort=page.getSort().isSorted()?page.getSort():Sort.by("dueOn");
        UUID companyId = company();
        var titles = payables.find(companyId, filter, PageRequest.of(page.getPageNumber(), page.getPageSize(), sort.and(Sort.by("id"))));
        var totals = settlements.netPaid(companyId, titles.getContent().stream().map(Payable::getId).toList());
        var today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return titles.map(title -> PayablePosition.calculate(title, totals.getOrDefault(title.getId(), BigDecimal.ZERO), today));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PayablePosition detail(UUID id){
        authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);
        UUID companyId = company();
        var title = payables.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Payable"));
        var paid = settlements.netPaid(companyId, List.of(id)).getOrDefault(id, BigDecimal.ZERO);
        return PayablePosition.calculate(title, paid, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PayablePosition> createdGroupPositions(UUID groupId) {
        UUID companyId = company();
        var titles = payables.findByGroup(companyId, groupId);
        var totals = settlements.netPaid(companyId, titles.stream().map(Payable::getId).toList());
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return titles.stream().map(title -> PayablePosition.calculate(title, totals.getOrDefault(title.getId(), BigDecimal.ZERO), today)).toList();
    }

    private CurrencyCode validate(UUID company,PayableCreateCommand c){
        Payable.validateScope(c.financialScope(),c.workId(),c.contractId());
        if(c.workId()!=null)works.findByIdAndCompanyId(c.workId(),company).orElseThrow(()->new TenantResourceNotFoundException("Work"));
        CurrencyCode currency=CurrencyCode.defaultIfAbsent(c.currency());
        if(c.contractId()!=null){
            var contract=contracts.findByIdAndCompanyId(c.contractId(),company).orElseThrow(()->new TenantResourceNotFoundException("Contract"));
            if(!contract.getWorkId().equals(c.workId()))throw new IllegalArgumentException("Contract must belong to the informed work");
            if(c.currency()!=null)contract.getCurrency().requireSame(c.currency());
            currency=contract.getCurrency();
        }
        // Keep catalogue activation stable until commit, including approval and concurrent deactivation.
        payables.lockCatalogReferences(company,c.supplierId(),c.financialCategoryId());
        if(c.supplierId()!=null&&!suppliers.find(company,c.supplierId()).orElseThrow(()->new TenantResourceNotFoundException("Supplier")).active())throw new IllegalStateException("Supplier is inactive");
        if(c.financialCategoryId()!=null&&!categories.find(company,c.financialCategoryId()).orElseThrow(()->new TenantResourceNotFoundException("Financial category")).active())throw new IllegalStateException("Financial category is inactive");
        for(var title:c.titles())Payable.createOperational(company,c.financialScope(),c.workId(),c.contractId(),c.operationId(),c.supplierId(),
                c.financialCategoryId(),currency,title.reference(),title.description(),title.issuedOn(),title.dueOn(),title.originalAmount());
        return currency;
    }
    private Result persist(UUID company,PayableCreateCommand c,CurrencyCode currency,String hash,UUID requestId){
        UUID group=payables.createGroup(company,c.operationId(),hash);
        List<Payable> titles=c.titles().stream().map(t->payables.save(Payable.createOperational(company,c.financialScope(),c.workId(),c.contractId(),group,
                c.supplierId(),c.financialCategoryId(),currency,t.reference(),t.description(),t.issuedOn(),t.dueOn(),t.originalAmount()))).toList();
        for(Payable title:titles){
            Map<String,Object> after=Map.of("financialScope",title.getFinancialScope().name(),"currency",currency.name(),
                    "status",title.getStatus().name(),"hasSupplier",c.supplierId()!=null,"hasCategory",c.financialCategoryId()!=null);
            Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("origin",requestId==null?"direct":"approval");
            metadata.put("financialGroupId",group.toString());metadata.put("operationId",c.operationId().toString());
            if(requestId!=null)metadata.put("changeRequestId",requestId.toString());
            audit.recordDirectAction(AuditModule.PAYABLES,AuditAction.CREATE,"Payable",title.getId(),null,after,metadata);
        }
        return new Result(group,null,ChangeAuthorizationMode.DIRECT,false);
    }

    private Map<String,Object> proposal(PayableCreateCommand c){
        Map<String,Object> m=new LinkedHashMap<>();m.put("operationId",c.operationId().toString());m.put("financialScope",c.financialScope()==null?null:c.financialScope().name());
        m.put("workId",string(c.workId()));m.put("contractId",string(c.contractId()));m.put("supplierId",string(c.supplierId()));m.put("financialCategoryId",string(c.financialCategoryId()));
        m.put("currency",c.currency()==null?null:c.currency().name());m.put("justification",c.justification());
        m.put("titles",c.titles().stream().map(t->{Map<String,Object> line=new LinkedHashMap<>();line.put("reference",t.reference());line.put("description",t.description());
            line.put("issuedOn",string(t.issuedOn()));line.put("dueOn",string(t.dueOn()));line.put("originalAmount",t.originalAmount()==null?null:t.originalAmount().toPlainString());return line;}).toList());return m;
    }
    private String fingerprint(PayableCreateCommand command){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(proposal(command)).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
    }
    @SuppressWarnings("unchecked") private PayableCreateCommand fromProposal(Map<String,Object> p){
        return new PayableCreateCommand(uuid(p.get("operationId")),com.renovar.canteiro.io.payables.domain.PayableFinancialScope.valueOf((String)p.get("financialScope")),
                uuid(p.get("workId")),uuid(p.get("contractId")),uuid(p.get("supplierId")),uuid(p.get("financialCategoryId")),p.get("currency")==null?null:CurrencyCode.valueOf((String)p.get("currency")),
                ((List<Map<String,Object>>)p.get("titles")).stream().map(t->new PayableCreateCommand.Title((String)t.get("reference"),(String)t.get("description"),date(t.get("issuedOn")),date(t.get("dueOn")),new BigDecimal((String)t.get("originalAmount")))).toList(),(String)p.get("justification"));
    }
    private UUID company(){return tenants.requireCurrentTenant().companyId();}
    private static String string(Object v){return v==null?null:v.toString();}
    private static UUID uuid(Object v){return v==null?null:UUID.fromString(v.toString());}
    private static LocalDate date(Object v){return v==null?null:LocalDate.parse(v.toString());}
    private static void requireHash(String previous,String current){if(!current.equals(previous))throw new IllegalStateException("Operation ID was already used with different data");}
    public record Result(UUID financialGroupId,ChangeRequest changeRequest,ChangeAuthorizationMode mode,boolean replay) { }
}
