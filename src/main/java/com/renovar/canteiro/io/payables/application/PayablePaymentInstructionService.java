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
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.payables.domain.PayablePaymentCipher;
import com.renovar.canteiro.io.payables.domain.PayablePaymentContent;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstruction;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionRepository;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionType;
import com.renovar.canteiro.io.payables.domain.PayableCatalogValues;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.pdfbox.Loader;
import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PayablePaymentInstructionService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final PayableRepository payables;
    private final PayablePaymentInstructionRepository instructions;
    private final PayablePaymentCipher cipher;
    private final StorageProvider storage;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final AuditEventRecorder audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly=true)
    public PayablePaymentInstruction read(UUID payableId){
        authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);requirePayable(payableId);
        return latest(payableId);
    }
    @Transactional
    public Reveal reveal(UUID payableId,UUID proposalId){
        requireSensitive();requirePayable(payableId);
        PayablePaymentInstruction value=proposalId==null?latest(payableId):proposal(payableId,proposalId);
        if(value==null)throw new TenantResourceNotFoundException("Payment instructions");
        PayablePaymentContent details=decode(value);
        auditRead(value,proposalId,"reveal");return new Reveal(value,details);
    }
    @Transactional
    public Result replace(UUID payableId,long expectedVersion,PayablePaymentContent content,String justification){
        requireSensitive();var mode=authorization.requireChangeAuthorization(AccessModule.PAYABLES,ChangeOperation.UPDATE);
        instructions.lockPayable(company(),payableId);requireMutable(requirePayable(payableId));
        var before=latest(payableId);requireVersion(expectedVersion,before);
        var proposed=new PayablePaymentInstruction(UUID.randomUUID(),company(),payableId,expectedVersion+1,content.type(),
                cipher.encrypt(json.writeValueAsString(content),context(payableId)),null,null,null,tenants.requireCurrentTenant().userId(),clock.instant());
        // A full replacement never carries a PDF across beneficiaries/types implicitly.
        return applyOrRequest(before,proposed,mode,justification);
    }
    @Transactional
    public Result uploadPdf(UUID payableId,long expectedVersion,String filename,String contentType,byte[] bytes,String justification){
        requireSensitive();var mode=authorization.requireChangeAuthorization(AccessModule.PAYABLES,ChangeOperation.UPDATE);
        instructions.lockPayable(company(),payableId);requireMutable(requirePayable(payableId));
        var before=latest(payableId);requireVersion(expectedVersion,before);
        if(before==null||before.type()!=PayablePaymentInstructionType.BOLETO)throw new IllegalStateException("Save official BOLETO instructions before attaching a PDF");
        validatePdf(filename,contentType,bytes);
        String key="payables/"+company()+"/"+payableId+"/"+UUID.randomUUID()+".enc";
        storage.store(key,cipher.encryptFile(bytes,context(payableId)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.delete(key);}
        });
        var proposed=new PayablePaymentInstruction(UUID.randomUUID(),company(),payableId,before.version()+1,before.type(),
                before.ciphertext(),key,sha256(bytes),(long)bytes.length,tenants.requireCurrentTenant().userId(),clock.instant());
        return applyOrRequest(before,proposed,mode,justification);
    }
    @Transactional
    public Download download(UUID payableId,UUID proposalId){
        requireSensitive();requirePayable(payableId);
        var value=proposalId==null?latest(payableId):proposal(payableId,proposalId);
        if(value==null||value.pdfStorageKey()==null)throw new TenantResourceNotFoundException("Boleto PDF");
        InputStream stream=cipher.decryptFile(storage.openStream(value.pdfStorageKey()),context(payableId));
        auditRead(value,proposalId,"download");return new Download(stream,value.pdfSize());
    }
    @Transactional
    public void applyApproved(ChangeRequest request){
        if(!company().equals(request.getCompanyId()))throw new TenantResourceNotFoundException("Payment instruction proposal");
        requireSensitive();
        var p=fromSnapshot(request);instructions.lockPayable(company(),p.payableId());
        requireMutable(requirePayable(p.payableId()));var before=latest(p.payableId());requireVersion(request.getEntityVersion(),before);
        decode(p); // Validate protected content and target binding again at application.
        save(before,p,request.getId());
    }
    private Result applyOrRequest(PayablePaymentInstruction before,PayablePaymentInstruction proposed,ChangeAuthorizationMode mode,String justification){
        justification=PayableCatalogValues.text(justification,1000,false);
        if(mode==ChangeAuthorizationMode.DIRECT)return save(before,proposed,null);
        Map<String,Object> data=new LinkedHashMap<>();data.put("protectedInstruction",cipher.encrypt(json.writeValueAsString(proposed),context(proposed.payableId())));
        data.put("reference","Instruções para pagamento");data.put("instructionType",proposed.type().name());data.put("hasPdf",proposed.pdfStorageKey()!=null);
        var request=changes.create(new CreateChangeRequestCommand(AuditModule.PAYABLES,ChangeRequestOperation.UPDATE,
                "PayablePaymentInstruction",proposed.payableId(),before==null?0:before.version(),
                new ChangeRequestSnapshot(null,new AuditPayload(data)),justification));
        return new Result(null,request.getId(),ChangeAuthorizationMode.REQUEST_APPROVAL);
    }
    private Result save(PayablePaymentInstruction before,PayablePaymentInstruction proposed,UUID requestId){
        var saved=instructions.save(proposed);
        audit.recordDirectAction(AuditModule.PAYABLES,AuditAction.UPDATE,"PayablePaymentInstruction",saved.payableId(),
                before==null?null:safe(before),safe(saved),requestId==null?Map.of("origin","direct"):Map.of("origin","approval","changeRequestId",requestId.toString()));
        return new Result(saved,null,ChangeAuthorizationMode.DIRECT);
    }
    private void auditRead(PayablePaymentInstruction value,UUID proposalId,String operation){
        Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("origin","direct");metadata.put("operation",operation);
        metadata.put("instructionId",value.id().toString());if(proposalId!=null)metadata.put("changeRequestId",proposalId.toString());
        audit.recordDirectAction(AuditModule.PAYABLES,AuditAction.VIEW_SENSITIVE,"PayablePaymentInstruction",value.payableId(),null,safe(value),metadata);
    }
    private PayablePaymentInstruction proposal(UUID payableId,UUID id){
        var request=requests.findByIdAndCompanyId(id,company()).orElseThrow(()->new TenantResourceNotFoundException("Payment instruction proposal"));
        if(request.getModule()!=AuditModule.PAYABLES||!"PayablePaymentInstruction".equals(request.getEntityType())
                ||!payableId.equals(request.getEntityId())||request.getStatus()!=ChangeRequestStatus.PENDING)
            throw new TenantResourceNotFoundException("Payment instruction proposal");
        if(!request.getRequesterUserId().equals(tenants.requireCurrentTenant().userId()))
            authorization.requirePermission(AccessModule.PAYABLES,AccessAction.APPROVE);
        return fromSnapshot(request);
    }
    private PayablePaymentInstruction fromSnapshot(ChangeRequest request){
        var value=json.readValue(cipher.decrypt((String)request.getSnapshot().proposedData().values().get("protectedInstruction"),
                context(request.getEntityId())),PayablePaymentInstruction.class);
        if(!company().equals(value.companyId())||!request.getEntityId().equals(value.payableId())||value.version()!=request.getEntityVersion()+1)
            throw new IllegalStateException("Payment instruction proposal target mismatch");return value;
    }
    private PayablePaymentContent decode(PayablePaymentInstruction value){return json.readValue(cipher.decrypt(value.ciphertext(),context(value.payableId())),PayablePaymentContent.class);}
    private Map<String,Object> safe(PayablePaymentInstruction value){return Map.of("type",value.type().name(),"version",value.version(),"hasPdf",value.pdfStorageKey()!=null);}
    private Payable requirePayable(UUID id){return payables.findByIdAndCompanyId(id,company()).orElseThrow(()->new TenantResourceNotFoundException("Payable"));}
    private PayablePaymentInstruction latest(UUID id){return instructions.latest(company(),id).orElse(null);}
    private UUID company(){return tenants.requireCurrentTenant().companyId();}
    private String context(UUID payable){return company()+"/"+payable;}
    private void requireSensitive(){authorization.requirePermission(AccessModule.PAYABLES,AccessAction.READ);authorization.requirePermission(AccessModule.PAYABLES,AccessAction.VIEW_PAYMENT_DETAILS);}
    private void requireMutable(Payable value){if(value.getStatus()!=PayableStatus.OPEN&&value.getStatus()!=PayableStatus.OVERDUE
            &&value.getStatus()!=PayableStatus.PARTIALLY_PAID)throw new IllegalStateException("Payment instructions require an open payable");}
    private void requireVersion(long expected,PayablePaymentInstruction current){
        if(expected<0)throw new IllegalArgumentException("expectedVersion must be non-negative");
        if(expected!=(current==null?0:current.version()))throw new IllegalStateException("Payment instructions changed; reload their current version");
    }
    private void validatePdf(String name,String mime,byte[] bytes){
        if(name==null||!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")||!"application/pdf".equals(mime)
                ||bytes==null||bytes.length<5||bytes.length>10*1024*1024)throw new IllegalArgumentException("A PDF of up to 10 MB is required");
        if(bytes[0]!='%'||bytes[1]!='P'||bytes[2]!='D'||bytes[3]!='F'||bytes[4]!='-')throw new IllegalArgumentException("Invalid PDF content");
        try(var pdf=Loader.loadPDF(bytes)){if(pdf.isEncrypted()||pdf.getNumberOfPages()<1)throw new IllegalArgumentException("PDF must be readable, non-encrypted and contain pages");}
        catch(java.io.IOException e){throw new IllegalArgumentException("Invalid PDF content");}
    }
    private String sha256(byte[] value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable");}}
    public record Result(PayablePaymentInstruction instruction,UUID changeRequestId,ChangeAuthorizationMode mode) { }
    public record Reveal(PayablePaymentInstruction instruction,PayablePaymentContent details) { }
    public record Download(InputStream stream,long size) { }
}
