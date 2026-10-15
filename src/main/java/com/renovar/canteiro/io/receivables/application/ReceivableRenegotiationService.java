package com.renovar.canteiro.io.receivables.application;

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
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.api.request.RenegotiateReceivableRequest;
import com.renovar.canteiro.io.receivables.api.response.ReceivableRenegotiationResponse;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;

/** Reschedules unpaid titles only. It creates neither billing capacity nor fictitious cash. */
@Service @RequiredArgsConstructor
public class ReceivableRenegotiationService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final ReceivableRepository titles;
    private final ReceivableOriginRepository origins;
    private final CalculateReceivableBalanceUseCase balances;
    private final ChangeRequestService requests;
    private final AuditEventRecorder audit;
    private final Clock clock;

    @Transactional(readOnly=true)
    public ReceivableRenegotiationResponse list(UUID rid) {
        auth.requirePermission(AccessModule.RECEIVABLES,AccessAction.READ);
        UUID company=tenants.requireCurrentTenant().companyId();
        require(company,rid);
        return response(rid,titles.findRenegotiatedChildren(rid,company));
    }
    @Transactional
    public ReceivableRenegotiationResponse change(UUID rid,RenegotiateReceivableRequest request) {
        UUID company=tenants.requireCurrentTenant().companyId();
        Receivable r=require(company,rid);origins.lockInstallment(company,r.getBillingInstallmentId());
        r=titles.findByIdAndCompanyIdWithSettlementLock(rid,company).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));
        validate(company,r,request);
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.RECEIVABLES,ChangeOperation.UPDATE);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest cr=requests.createIdempotent(new CreateChangeRequestCommand(AuditModule.RECEIVABLES,ChangeRequestOperation.UPDATE,
                "ReceivableRenegotiation",rid,0,new ChangeRequestSnapshot(null,new AuditPayload(proposal(request))),request.justification()));
            return new ReceivableRenegotiationResponse(null,null,cr.getId(),mode);
        }
        return persist(company,r,request,"direct",null);
    }
    @Transactional
    public ReceivableRenegotiationResponse reverse(UUID rid,String reason,String justification) {
        UUID company=tenants.requireCurrentTenant().companyId();
        Receivable r=require(company,rid);origins.lockInstallment(company,r.getBillingInstallmentId());
        r=titles.findByIdAndCompanyIdWithSettlementLock(rid,company).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));
        validateReversal(company,r,reason);
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.RECEIVABLES,ChangeOperation.CANCEL);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest cr=requests.createIdempotent(new CreateChangeRequestCommand(AuditModule.RECEIVABLES,ChangeRequestOperation.CANCEL,
                "ReceivableRenegotiation",rid,0,new ChangeRequestSnapshot(null,new AuditPayload(Map.of("reason",reason))),justification));
            return new ReceivableRenegotiationResponse(null,null,cr.getId(),mode);
        }
        return persistReversal(company,r,reason,"direct",null);
    }
    @Transactional
    public void applyApproved(ChangeRequest cr) {
        Receivable r=require(cr.getCompanyId(),cr.getEntityId());origins.lockInstallment(cr.getCompanyId(),r.getBillingInstallmentId());
        r=titles.findByIdAndCompanyIdWithSettlementLock(cr.getEntityId(),cr.getCompanyId()).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));
        Map<String,Object> p=cr.getSnapshot().proposedData().values();
        if(cr.getOperation()==ChangeRequestOperation.CANCEL) {
            validateReversal(cr.getCompanyId(),r,p.get("reason").toString());
            persistReversal(cr.getCompanyId(),r,p.get("reason").toString(),"approval",cr.getId());
        } else {
            List<RenegotiateReceivableRequest.Installment> installments=new ArrayList<>();
            for(Object value:(List<?>)p.get("installments")) {
                Map<?,?> row=(Map<?,?>)value;
                installments.add(new RenegotiateReceivableRequest.Installment(LocalDate.parse(row.get("dueOn").toString()),row.get("amount").toString()));
            }
            RenegotiateReceivableRequest request=new RenegotiateReceivableRequest(installments,p.get("reason").toString(),cr.getJustification());
            validate(cr.getCompanyId(),r,request);persist(cr.getCompanyId(),r,request,"approval",cr.getId());
        }
    }
    private void validate(UUID company,Receivable r,RenegotiateReceivableRequest request) {
        reason(request.reason());
        if(r.getStatus()==ReceivableStatus.CANCELLED||r.getStatus()==ReceivableStatus.RENEGOTIATED) conflict("The title is not open for renegotiation");
        BigDecimal total=BigDecimal.ZERO;
        if(request.installments()==null||request.installments().isEmpty()||request.installments().size()>100)throw new IllegalArgumentException("Provide 1 to 100 new titles");
        for(var installment:request.installments()) {
            BigDecimal amount=new BigDecimal(installment.amount());
            if(amount.signum()<=0||amount.scale()!=2||installment.dueOn()==null||installment.dueOn().isBefore(LocalDate.now(clock)))
                throw new IllegalArgumentException("A positive canonical amount and present/future due date are required");
            total=total.add(amount);
        }
        if(total.compareTo(balances.calculate(company,r.getId()).outstandingAmount())!=0)conflict("New titles must total exactly the current unpaid balance");
    }
    private void validateReversal(UUID company,Receivable r,String reason) {
        reason(reason);
        if(r.getStatus()!=ReceivableStatus.RENEGOTIATED)conflict("The title has no active renegotiation");
        for(Receivable child:titles.findRenegotiatedChildren(r.getId(),company)) {
            if(child.getStatus()==ReceivableStatus.CANCELLED)continue;
            ReceivableBalance b=balances.calculate(company,child.getId());
            if(child.getStatus()==ReceivableStatus.RENEGOTIATED||b.confirmedSettlementAmount().signum()!=0||b.withheldAmount().signum()!=0||b.creditedAmount().signum()!=0)
                conflict("Reverse descendant renegotiations, receipts and adjustments first");
        }
    }
    private ReceivableRenegotiationResponse persist(UUID company,Receivable r,RenegotiateReceivableRequest request,String origin,UUID cr) {
        List<Receivable> children=new ArrayList<>();int number=1;
        for(var installment:request.installments()) {
            children.add(titles.saveRenegotiated(Receivable.create(company,r.getContractId(),r.getBillingInstallmentId(),r.getFinancialGroupId(),
                "Renegociação "+number++,"Reprogramação de saldo do título original",LocalDate.now(clock),installment.dueOn(),new BigDecimal(installment.amount())),r.getId()));
        }
        titles.updateStatus(r.withStatus(ReceivableStatus.RENEGOTIATED));
        record(r,children,request.reason(),origin,cr,false);
        return response(r.getId(),children);
    }
    private ReceivableRenegotiationResponse persistReversal(UUID company,Receivable r,String reason,String origin,UUID cr) {
        List<Receivable> children=new ArrayList<>();
        for(Receivable child:titles.findRenegotiatedChildren(r.getId(),company))if(child.getStatus()!=ReceivableStatus.CANCELLED)
            children.add(titles.updateStatus(child.cancel()));
        Receivable reopened=r.withStatus(ReceivableStatus.OPEN);
        titles.updateStatus(reopened.withStatus(balances.calculate(company,r.getId()).statusOn(reopened,LocalDate.now(clock))));
        record(r,children,reason,origin,cr,true);
        return response(r.getId(),children);
    }
    private void record(Receivable r,List<Receivable> children,String reason,String origin,UUID cr,boolean reversal) {
        Map<String,Object> metadata=new HashMap<>();metadata.put("origin",origin);metadata.put("reason",reason);
        if(cr!=null)metadata.put("changeRequestId",cr.toString());
        audit.recordDirectAction(AuditModule.RECEIVABLES,reversal?AuditAction.CANCEL:AuditAction.UPDATE,"ReceivableRenegotiation",r.getId(),
            Map.of("status",r.getStatus().name()),Map.of("status",reversal?"REOPENED":"RENEGOTIATED","billingInstallmentId",r.getBillingInstallmentId().toString(),
                "titles",children.stream().map(c->Map.of("id",c.getId().toString(),"amount",c.getOriginalAmount().toPlainString(),"dueOn",c.getDueOn().toString())).toList()),metadata);
    }
    private Map<String,Object> proposal(RenegotiateReceivableRequest r) {
        return Map.of("reason",r.reason(),"installments",r.installments().stream().map(i->Map.of("dueOn",i.dueOn().toString(),"amount",i.amount())).toList());
    }
    private ReceivableRenegotiationResponse response(UUID rid,List<Receivable> children) {
        return new ReceivableRenegotiationResponse(rid,children.stream().sorted(Comparator.comparing(Receivable::getDueOn).thenComparing(r->r.getId().toString()))
            .map(r->new ReceivableRenegotiationResponse.Title(r.getId(),r.getBillingInstallmentId(),r.getFinancialGroupId(),r.getDueOn(),r.getOriginalAmount().toPlainString(),r.getStatus())).toList(),null,ChangeAuthorizationMode.DIRECT);
    }
    private Receivable require(UUID company,UUID rid){return titles.findByIdAndCompanyId(rid,company).orElseThrow(()->new TenantResourceNotFoundException("Receivable"));}
    private void reason(String r){if(r==null||r.isBlank()||r.length()>1000)throw new IllegalArgumentException("A reason of 1 to 1000 characters is required");}
    private void conflict(String message){throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,message);}
}
