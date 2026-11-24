package com.renovar.canteiro.io.payables;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataAccessException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Only instruction* methods are selected in the focused run; catalog regressions run separately. */
class PayablePaymentInstructionApiIntegrationTest extends PayableCatalogApiIntegrationTest {
    @Test void instructionDirectRevealsOnlyByPermissionAndDoesNotChangeFinancialTitle()throws Exception{
        var owner=owner();var editor=editor(owner);String title=title(owner);String path=path(title);
        String original=titleSnapshot(title);
        var empty=call(editor,get(path),null,200);assertFalse(empty.path("exists").asBoolean());assertEquals(0,empty.path("version").asLong());
        var saved=call(editor,put(path),request(0,pix("pix-secret@example.com")),200);
        assertEquals(1,saved.at("/instruction/version").asInt());assertFalse(saved.toString().contains("pix-secret"));
        var reader=actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT)));
        assertEquals("PIX",call(reader,get(path),null,200).path("type").asText());
        call(reader,post(path+"/reveal"),null,403);call(reader,put(path),request(1,pix("overwrite@example.com")),403);
        var response=mvc.perform(post(path+"/reveal").with(jwt().jwt(t->t.subject(editor.user().toString())))).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("pix-secret@example.com",json.readTree(response.getContentAsString()).at("/details/pixKey").asText());
        String stored=jdbc.queryForObject("select ciphertext from payable_payment_instruction where payable_id=?",String.class,UUID.fromString(title));assertFalse(stored.contains("pix-secret"));
        assertEquals(original,titleSnapshot(title));assertSafeAudit(owner,"pix-secret","overwrite");
        assertThrows(DataAccessException.class,()->jdbc.update("delete from payable_payment_instruction where payable_id=?",UUID.fromString(title)));
        assertThrows(DataAccessException.class,()->jdbc.update("update payable_payment_instruction set version=2 where payable_id=?",UUID.fromString(title)));
    }
    @Test void instructionApprovalProtectsProposalAndRevalidatesVersionWithoutChangingOfficialData()throws Exception{
        var owner=owner();var editor=editor(owner);String title=title(owner);String path=path(title);String original=titleSnapshot(title);
        var requester=actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.VIEW_PAYMENT_DETAILS,AccessAction.REQUEST_UPDATE)));
        var approver=actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.VIEW_PAYMENT_DETAILS,AccessAction.APPROVE)));
        var pending=call(requester,put(path),request(0,pix("pending-secret@example.com")),202);String id=pending.path("changeRequestId").asText();
        assertFalse(pending.has("instruction"));assertFalse(call(editor,get(path),null,200).path("exists").asBoolean());
        String stored=jdbc.queryForObject("select proposed_data::text from change_request where id=?",String.class,UUID.fromString(id));assertFalse(stored.contains("pending-secret"));
        call(requester,post(approve(id)),Map.of(),403);
        assertEquals("pending-secret@example.com",call(approver,post(path+"/reveal").param("changeRequestId",id),null,200).at("/details/pixKey").asText());
        var outsider=editor(owner);call(outsider,post(path+"/reveal").param("changeRequestId",id),null,403);
        call(approver,post(approve(id)),Map.of(),200);assertEquals(1,call(editor,get(path),null,200).path("version").asInt());
        call(approver,post(path+"/reveal").param("changeRequestId",id),null,404);
        var stale=call(requester,put(path),request(1,pix("stale@example.com")),202);
        call(editor,put(path),request(1,pix("current@example.com")),200);
        call(approver,post(approve(stale.path("changeRequestId").asText())),Map.of(),409);
        assertEquals("current@example.com",call(editor,post(path+"/reveal"),null,200).at("/details/pixKey").asText());
        assertEquals(original,titleSnapshot(title));assertSafeAudit(owner,"pending-secret","current@example.com","stale@example.com");
    }
    @Test void instructionTenantAndAuthenticationApplyToMetadataRevealWriteAndFiles()throws Exception{
        var owner=owner();var editor=editor(owner);var foreign=editor(owner());String title=title(owner);String path=path(title);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        call(editor,put(path),request(0,Map.of("type","BOLETO")),200);
        call(foreign,get(path),null,404);call(foreign,post(path+"/reveal"),null,404);call(foreign,put(path),request(1,Map.of("type","BOLETO")),404);
        call(foreign,get(path+"/boleto"),null,404);
        mvc.perform(multipart(path+"/boleto").file(pdf()).param("expectedVersion","1").with(jwt().jwt(t->t.subject(foreign.user().toString())))).andExpect(status().isNotFound());
        assertEquals(1,jdbc.queryForObject("select count(*) from payable_payment_instruction where payable_id=?",Integer.class,UUID.fromString(title)));
        assertTrue(call(foreign,get(path),null,404).has("correlationId"));
    }
    @Test void instructionPdfOnlySupportsEncryptedPrivateDownloadAndImmutableReplacement()throws Exception{
        var owner=owner();var editor=editor(owner);String title=title(owner);String path=path(title);String original=titleSnapshot(title);
        call(editor,put(path),request(0,Map.of("type","BOLETO")),200);call(editor,get(path+"/boleto"),null,404);
        var pdf=pdf();var uploaded=mvc.perform(multipart(path+"/boleto").file(pdf).param("expectedVersion","1").with(jwt().jwt(t->t.subject(editor.user().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse();assertTrue(json.readTree(uploaded.getContentAsString()).at("/instruction/hasPdf").asBoolean());
        var download=mvc.perform(get(path+"/boleto").with(jwt().jwt(t->t.subject(editor.user().toString())))).andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf.getBytes(),download.getContentAsByteArray());assertEquals("no-store",download.getHeader("Cache-Control"));assertEquals("application/pdf",download.getContentType());
        assertEquals("attachment; filename=\"boleto.pdf\"",download.getHeader("Content-Disposition"));
        String key=jdbc.queryForObject("select pdf_storage_key from payable_payment_instruction where payable_id=? and version=2",String.class,UUID.fromString(title));
        assertFalse(new String(org.springframework.util.StreamUtils.copyToByteArray(storage().openStream(key)),java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        call(editor,put(path),request(2,pix("new@example.com")),200);assertFalse(call(editor,get(path),null,200).path("hasPdf").asBoolean());call(editor,get(path+"/boleto"),null,404);
        assertEquals(3,jdbc.queryForObject("select count(*) from payable_payment_instruction where payable_id=?",Integer.class,UUID.fromString(title)));assertEquals(original,titleSnapshot(title));
        assertSafeAudit(owner,"new@example.com",key,"private-upload-name");
    }
    @Test void instructionPendingPdfIsOnlyVisibleAsAuthorizedProposalUntilApproval()throws Exception{
        var owner=owner();var editor=editor(owner);String title=title(owner);String path=path(title);
        call(editor,put(path),request(0,Map.of("type","BOLETO")),200);
        var requester=actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.VIEW_PAYMENT_DETAILS,AccessAction.REQUEST_UPDATE)));
        var approver=actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.VIEW_PAYMENT_DETAILS,AccessAction.APPROVE)));
        var pending=mvc.perform(multipart(path+"/boleto").file(pdf()).param("expectedVersion","1").with(jwt().jwt(t->t.subject(requester.user().toString()))))
                .andExpect(status().isAccepted()).andReturn().getResponse();String id=json.readTree(pending.getContentAsString()).path("changeRequestId").asText();
        assertEquals(1,call(editor,get(path),null,200).path("version").asInt());call(editor,get(path+"/boleto"),null,404);
        mvc.perform(get(path+"/boleto").param("changeRequestId",id).with(jwt().jwt(t->t.subject(approver.user().toString())))).andExpect(status().isOk());
        call(approver,post(approve(id)),Map.of(),200);assertTrue(call(editor,get(path),null,200).path("hasPdf").asBoolean());
    }
    @Test void instructionRejectsMixedInvalidOversizedAndClosedTitleChangesWithSafeProblems()throws Exception{
        var owner=owner();var editor=editor(owner);String title=title(owner);String path=path(title);
        for(var detail:List.of(Map.of("type","PIX","pixKey","not-a-key"),Map.of("type","BOLETO","digitableLine","secret-wrong"),
                Map.of("type","BANK_TRANSFER","bankName","Banco"),Map.of("type","PIX","pixKeyType","EMAIL","pixKey","a@example.com","barcode","1"),
                Map.of("type","PIX","pixCopyPaste","x".repeat(1025)))){
            var error=call(editor,put(path),request(0,detail),400);assertTrue(error.has("correlationId"));assertFalse(error.toString().contains("secret-wrong"));
        }
        call(editor,put(path),request(-1,pix("safe@example.com")),400);
        call(editor,put(path),request(0,Map.of("type","BOLETO","barcode","1".repeat(44))),200);
        mvc.perform(multipart(path+"/boleto").file(new MockMultipartFile("file","x.pdf","application/pdf","not PDF".getBytes())).param("expectedVersion","1")
                .with(jwt().jwt(t->t.subject(editor.user().toString())))).andExpect(status().isBadRequest());
        call(editor,put(path),request(0,pix("safe@example.com")),409);
        jdbc.update("update payable set status='CANCELLED' where id=?",UUID.fromString(title));
        call(editor,put(path),request(1,pix("safe@example.com")),409);
        assertEquals(1,jdbc.queryForObject("select count(*) from payable_payment_instruction where payable_id=?",Integer.class,UUID.fromString(title)));
    }
    @Test void instructionConcurrentReplacementAllowsExactlyOneExpectedVersion()throws Exception{
        var owner=owner();var editor=editor(owner);String path=path(title(owner));var latch=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{latch.await();return code(editor,put(path),request(0,pix("one@example.com")));});
            var b=executor.submit(()->{latch.await();return code(editor,put(path),request(0,pix("two@example.com")));});latch.countDown();
            var results=List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));assertTrue(results.contains(200));assertTrue(results.contains(409));
        }
        assertEquals(1,call(editor,get(path),null,200).path("version").asInt());
    }
    @Test void instructionExecutableOpenApiExportsOnlyImplementedContracts()throws Exception{
        String text=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();var api=json.readTree(text);
        String base="/api/v1/company/payables/{payableId}/payment-instructions";
        for(String suffix:List.of("","/reveal","/boleto"))assertTrue(api.path("paths").has(base+suffix));
        for(String code:List.of("200","202","400","401","403","404","409"))assertTrue(api.path("paths").path(base).path("put").path("responses").has(code));
        assertTrue(api.at("/components/schemas/PayablePaymentInstructionUpdateRequest/required").toString().contains("expectedVersion"));
        assertTrue(api.at("/components/schemas/SupplierCreateRequest/properties").has("contactEmail"));
        assertFalse(api.at("/components/schemas/PayablePaymentInstructionResponse/properties").has("ciphertext"));
        assertTrue(api.at("/components/schemas/SupplierResponse/properties/contactEmail/type").toString().contains("null"));
        assertTrue(api.at("/components/schemas/PayablePaymentInstructionResponse/properties/type/anyOf").toString().contains("null"));
        assertTrue(api.at("/components/schemas/PayablePaymentDetails/properties/pixKeyType/anyOf").toString().contains("null"));
        assertTrue(api.path("paths").path(base+"/boleto").path("post").path("requestBody").path("content").has("multipart/form-data"));
        String export=System.getenv("PAYABLE_INSTRUCTIONS_OPENAPI_EXPORT_PATH");if(export!=null&&!export.isBlank())java.nio.file.Files.writeString(java.nio.file.Path.of(export),text);
    }
    @org.springframework.beans.factory.annotation.Autowired com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider storage;
    private com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider storage(){return storage;}
    Actor editor(Actor owner){return actor(owner.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.VIEW_PAYMENT_DETAILS,AccessAction.UPDATE_DIRECT)));}
    String title(Actor owner)throws Exception{return call(owner,post("/api/v1/company/payables"),Map.of("operationId",UUID.randomUUID(),"financialScope","COMPANY",
            "titles",List.of(Map.of("reference","Título de teste","dueOn","2026-12-10","originalAmount","100.00"))),201).at("/payables/0/id").asText();}
    String path(String id){return "/api/v1/company/payables/"+id+"/payment-instructions";}
    Map<String,Object> request(long version,Map<String,String> details){return Map.of("expectedVersion",version,"details",details);}
    Map<String,String> pix(String key){return Map.of("type","PIX","pixKeyType","EMAIL","pixKey",key);}
    String titleSnapshot(String title){return jdbc.queryForObject("select to_jsonb(p)::text from payable p where id=?",String.class,UUID.fromString(title));}
    void assertSafeAudit(Actor owner,String... excluded){String text=jdbc.queryForObject("select string_agg(coalesce(before_data::text,'')||coalesce(after_data::text,'')||coalesce(metadata::text,''),'') from audit_event where company_id=?",String.class,owner.company());
        for(String value:excluded)assertFalse(text.contains(value),"Sensitive value leaked into audit");}
    MockMultipartFile pdf()throws Exception{try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){
        doc.addPage(new PDPage());doc.save(bytes);return new MockMultipartFile("file","private-upload-name.pdf","application/pdf",bytes.toByteArray());}}
}
