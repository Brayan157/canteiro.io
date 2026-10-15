package com.renovar.canteiro.io.receivables.application;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableCredit;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.api.response.ReceivableCreditResponse;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
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
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
@Service @RequiredArgsConstructor
public class ReceivableCreditService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final ReceivableRepository receivables;
    private final ReceivableOriginRepository origins;
    private final CalculateReceivableBalanceUseCase balances;
    private final ChangeRequestService requests;
    private final AuditEventRecorder audit;
    private final Clock clock;
    @Transactional
    public ReceivableCreditResponse create(UUID rid,BigDecimal amount,String reason,String justification){
        return change(rid,null,amount,reason,justification);
    }
    @Transactional
    public ReceivableCreditResponse reverse(UUID rid,UUID cid,String reason,String justification){
        return change(rid,cid,null,reason,justification);
    }
    private ReceivableCreditResponse change(UUID rid,UUID reversal,BigDecimal amount,String reason,String why){
        UUID company=tenants.requireCurrentTenant().companyId();
        Receivable r=require(company,rid);origins.lockInstallment(company,r.getBillingInstallmentId());
        r=receivables.findByIdAndCompanyIdWithSettlementLock(rid,company).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));
        BigDecimal effective=validate(company,r,reversal,amount,reason);
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.RECEIVABLES,reversal==null?ChangeOperation.UPDATE:ChangeOperation.CANCEL);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL){
            Map<String,Object> proposal=new HashMap<>();proposal.put("receivableId",rid.toString());proposal.put("creditId",reversal==null?null:reversal.toString());
            proposal.put("amount",effective.toPlainString());proposal.put("reason",reason);
            ChangeRequest cr=requests.createIdempotent(new CreateChangeRequestCommand(AuditModule.RECEIVABLES,
                reversal==null?ChangeRequestOperation.UPDATE:ChangeRequestOperation.CANCEL,"ReceivableCredit",rid,0,
                new ChangeRequestSnapshot(null,new AuditPayload(proposal)),why));
            return new ReceivableCreditResponse(null,null,null,null,cr.getId(),mode);
        }return persist(company,r,reversal,effective,reason,"direct",null);
    }
    @Transactional
    public void applyApproved(ChangeRequest cr){
        var p=cr.getSnapshot().proposedData().values();UUID rid=UUID.fromString(p.get("receivableId").toString());
        Receivable r=require(cr.getCompanyId(),rid);origins.lockInstallment(cr.getCompanyId(),r.getBillingInstallmentId());
        r=receivables.findByIdAndCompanyIdWithSettlementLock(rid,cr.getCompanyId()).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));
        UUID cid=p.get("creditId")==null?null:UUID.fromString(p.get("creditId").toString());
        BigDecimal value=validate(cr.getCompanyId(),r,cid,new BigDecimal(p.get("amount").toString()),p.get("reason").toString());
        persist(cr.getCompanyId(),r,cid,value,p.get("reason").toString(),"approval",cr.getId());
    }
    private BigDecimal validate(UUID company,Receivable r,UUID reversal,BigDecimal amount,String reason){
        if(reason==null||reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("A credit reason of 1 to 1000 characters is required");
        if(r.getStatus()==ReceivableStatus.CANCELLED||r.getStatus()==ReceivableStatus.RENEGOTIATED)conflict("This receivable cannot be adjusted");
        if(reversal!=null){
            ReceivableCredit old=origins.findCredit(company,reversal).filter(c->r.getId().equals(c.receivableId())).orElseThrow(()->new TenantResourceNotFoundException("Receivable credit"));
            if(old.reversalOfCreditId()!=null||origins.hasCreditReversal(company,old.id()))conflict("Credit already reversed");
            return old.amount();
        }
        if(amount==null||amount.signum()<=0||amount.scale()>2)throw new IllegalArgumentException("A positive two-place decimal credit is required");
        if(amount.compareTo(balances.calculate(company,r.getId()).outstandingAmount())>0)conflict("The credit exceeds the unpaid receivable balance");
        return amount.setScale(2);
    }
    private ReceivableCreditResponse persist(UUID company,Receivable r,UUID reversal,BigDecimal amount,String reason,String origin,UUID request){
        ReceivableCredit saved=origins.saveCredit(new ReceivableCredit(null,company,r.getId(),r.getContractId(),amount,reason,reversal,
            tenants.requireCurrentTenant().userId(),null));
        ReceivableBalance balance=balances.calculate(company,r.getId());
        receivables.updateStatus(r.withStatus(balance.statusOn(r,LocalDate.now(clock))));
        audit.recordDirectAction(AuditModule.RECEIVABLES,reversal==null?AuditAction.UPDATE:AuditAction.CANCEL,"ReceivableCredit",saved.id(),null,
            Map.of("receivableId",r.getId().toString(),"amount",amount.toPlainString(),"reversal",reversal!=null),
            request==null?Map.of("origin",origin):Map.of("origin",origin,"changeRequestId",request.toString()));
        return new ReceivableCreditResponse(saved.id(),r.getId(),reversal,amount.toPlainString(),null,ChangeAuthorizationMode.DIRECT);
    }
    private Receivable require(UUID company,UUID rid){return receivables.findByIdAndCompanyId(rid,company).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));}
    private void conflict(String message){throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,message);}
}

