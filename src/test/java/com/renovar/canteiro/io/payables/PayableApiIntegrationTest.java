package com.renovar.canteiro.io.payables;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayableApiIntegrationTest extends PayableCatalogApiIntegrationTest {
    static final String PAYABLES="/api/v1/company/payables";
    @Autowired WorkRepository works;
    @Autowired ContractRepository contracts;
    @Autowired FinalCustomerRepository customers;
    @Autowired PayableRepository payables;
    @Autowired com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository cashInstallments;
    @Autowired com.renovar.canteiro.io.receivables.domain.ReceivableRepository cashReceivables;

    @Test void createsEpiWithoutContractAndCompanyTitlesInIndependentFinancialGroupsWithReadableLabels()throws Exception{
        var f=owner();var work=work(f);String supplier=call(f,post(SUPPLIERS),Map.of("name","Fornecedor real","document","1234"),201).at("/supplier/id").asText();
        String category=category(f,"EPI","Equipamentos de proteção","WORK_COST",201).at("/category/id").asText();
        var body=body("WORK");body.put("workId",work.getId());body.put("supplierId",supplier);body.put("financialCategoryId",category);
        body.put("titles",List.of(title("EPI 1","150.50","2026-10-01"),title("EPI 2","10.00","2026-11-01")));
        var created=call(f,post(PAYABLES),body,201);assertEquals("DIRECT",created.path("mode").asText());assertEquals(2,created.path("payables").size());
        String group=created.path("financialGroupId").asText();var first=created.at("/payables/0");String id=first.path("id").asText();
        assertEquals(group,first.path("financialGroupId").asText());assertEquals(group,created.at("/payables/1/financialGroupId").asText());
        assertEquals("150.50",first.path("originalAmount").asText());assertTrue(first.path("originalAmount").isString());assertEquals("BRL",first.path("currency").asText());
        assertTrue(first.path("contractId").isNull());assertEquals("Obra identificável",first.at("/work/displayName").asText());
        assertEquals("Fornecedor real",first.at("/supplier/displayName").asText());assertEquals("EPI",first.at("/financialCategory/reference").asText());
        assertFalse(first.toString().contains("document"));assertEquals(0,first.path("version").asLong());
        assertEquals(id,call(f,get(PAYABLES+"/"+id),null,200).path("id").asText());
        var companyBody=body("COMPANY");companyBody.put("financialCategoryId",category);companyBody.put("currency","EUR");
        var company=call(f,post(PAYABLES),companyBody,201);assertNotEquals(group,company.path("financialGroupId").asText());
        assertEquals("COMPANY",company.at("/payables/0/financialScope").asText());assertTrue(company.at("/payables/0/work").isNull());
        assertEquals("EUR",company.at("/payables/0/currency").asText());
        assertEquals(2,call(f,get(PAYABLES).param("workId",work.getId().toString()),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(PAYABLES).param("financialScope","COMPANY"),null,200).path("totalItems").asInt());
        assertEquals(2,call(f,get(PAYABLES).param("supplierId",supplier),null,200).path("totalItems").asInt());
        assertEquals(3,call(f,get(PAYABLES).param("financialCategoryId",category).param("status","OPEN"),null,200).path("totalItems").asInt());
        String audit=jdbc.queryForObject("select string_agg(coalesce(after_data::text,'')||coalesce(metadata::text,''),'') from audit_event where company_id=? and entity_type='Payable'",String.class,f.company());
        assertFalse(audit.contains("150.50"));assertFalse(audit.contains("1234"));assertFalse(audit.contains("description"));
        assertEquals("Conta a pagar",jdbc.queryForObject("select entity_display_name from audit_event where entity_id=? and entity_type='Payable'",String.class,UUID.fromString(id)));
    }

    @Test void validatesScopeChainCatalogActivationAndInheritedCurrencyWithoutLeakingForeignRecords()throws Exception{
        var f=owner();var other=owner();var work=work(f);var another=work(f);var foreignWork=work(other);var contract=contract(f,work,CurrencyCode.USD);
        var c=body("WORK");call(f,post(PAYABLES),c,400);
        c=body("COMPANY");c.put("workId",work.getId());call(f,post(PAYABLES),c,400);
        c=body("COMPANY");c.put("contractId",contract.getId());call(f,post(PAYABLES),c,400);
        c=body("WORK");c.put("workId",another.getId());c.put("contractId",contract.getId());call(f,post(PAYABLES),c,400);
        c=body("WORK");c.put("workId",foreignWork.getId());call(f,post(PAYABLES),c,404);
        c=body("WORK");c.put("workId",work.getId());c.put("contractId",contract(other,foreignWork,CurrencyCode.BRL).getId());call(f,post(PAYABLES),c,404);
        c=body("WORK");c.put("workId",work.getId());c.put("contractId",contract.getId());
        var created=call(f,post(PAYABLES),c,201);assertEquals("USD",created.at("/payables/0/currency").asText());assertEquals("Contrato identificável",created.at("/payables/0/contract/displayName").asText());
        assertEquals(1,call(f,get(PAYABLES).param("contractId",contract.getId().toString()).param("currency","USD"),null,200).path("totalItems").asInt());
        c.put("operationId",UUID.randomUUID());c.put("currency","BRL");call(f,post(PAYABLES),c,409);
        String supplier=call(other,post(SUPPLIERS),Map.of("name","Outro tenant"),201).at("/supplier/id").asText();
        c=body("COMPANY");c.put("supplierId",supplier);call(f,post(PAYABLES),c,404);
        String category=category(other,"OTHER","Outra","TAX",201).at("/category/id").asText();c=body("COMPANY");c.put("financialCategoryId",category);call(f,post(PAYABLES),c,404);
        String inactive=call(f,post(SUPPLIERS),Map.of("name","Inativo"),201).at("/supplier/id").asText();call(f,delete(SUPPLIERS+"/"+inactive).param("expectedVersion","0"),null,204);
        c=body("COMPANY");c.put("supplierId",inactive);call(f,post(PAYABLES),c,409);
        c=body("COMPANY");c.remove("financialScope");call(f,post(PAYABLES),c,400);
    }

    @Test void protectsAuthenticationPermissionsTenantAndBoundedFiltersWithNoReadSideEffects()throws Exception{
        var f=owner();var other=owner();var denied=actor(f.company(),Map.of());
        mvc.perform(get(PAYABLES)).andExpect(status().isUnauthorized());mvc.perform(post(PAYABLES).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body("COMPANY")))).andExpect(status().isUnauthorized());
        call(denied,get(PAYABLES),null,403);call(denied,post(PAYABLES),body("COMPANY"),403);
        var reader=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ)));call(reader,post(PAYABLES),body("COMPANY"),403);
        var writer=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.CREATE_DIRECT)));call(writer,get(PAYABLES),null,403);
        var b=body("COMPANY");b.put("titles",List.of(title("A%_","10.00","2020-01-01"),title("Beta","20.00","2030-01-01"),title("Gama","30.00","2030-02-01")));
        var created=call(writer,post(PAYABLES),b,201);String id=created.at("/payables/0/id").asText();
        call(denied,get(PAYABLES+"/"+id),null,403);
        call(other,get(PAYABLES+"/"+id),null,404);call(reader,get(PAYABLES+"/"+UUID.randomUUID()),null,404);
        assertEquals(0,call(other,get(PAYABLES).param("financialGroupId",created.path("financialGroupId").asText()),null,200).path("totalItems").asInt());
        assertEquals(0,call(other,get(PAYABLES).param("companyId",f.company().toString()),null,200).path("totalItems").asInt());
        long events=jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company());
        var old=call(reader,get(PAYABLES+"/"+id),null,200);assertTrue(old.path("overdue").asBoolean());assertEquals("OPEN",old.path("status").asText());
        assertEquals(1,call(reader,get(PAYABLES).param("reference","%_"),null,200).path("totalItems").asInt());
        assertEquals(1,call(reader,get(PAYABLES).param("dueFrom","2030-01-01").param("dueTo","2030-01-01"),null,200).path("totalItems").asInt());
        assertEquals("Beta",call(reader,get(PAYABLES).param("page","1").param("size","1").param("sort","reference,desc"),null,200).at("/items/0/reference").asText());
        assertEquals(0,call(reader,get(PAYABLES).param("currency","USD"),null,200).path("totalItems").asInt());
        for(String[] p:new String[][]{{"page","-1"},{"size","101"},{"page","2147483647"},{"sort","supplierId,asc"},{"sort","dueOn,no"},{"financialScope","INVALID"},{"currency","GBP"},{"status","INVALID"},{"dueFrom","no-date"}})call(reader,get(PAYABLES).param(p[0],p[1]),null,400);
        call(reader,get(PAYABLES).param("dueFrom","2030-02-01").param("dueTo","2030-01-01"),null,400);
        assertEquals(events,jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company()));
        assertEquals("OPEN",jdbc.queryForObject("select status from payable where id=?",String.class,UUID.fromString(id)));
    }

    @Test void canonicalMoneyAndAtomicBatchRejectMalformedTitlesWithoutCreatingGroups()throws Exception{
        var f=owner();long groups=jdbc.queryForObject("select count(*) from payable_financial_group where company_id=?",Long.class,f.company());
        for(Object amount:List.of(25,25.5,"0.00","-1.00","1.001","01.00","1e2","1,00","100000000000000000.00")){
            var b=body("COMPANY");var t=new LinkedHashMap<>(title("Ruim","10.00","2030-01-01"));t.put("originalAmount",amount);b.put("titles",List.of(title("Boa","20.00","2030-01-01"),t));
            var error=call(f,post(PAYABLES),b,400);assertFalse(error.path("correlationId").asText().isBlank());
        }
        var b=body("COMPANY");b.put("titles",List.of());call(f,post(PAYABLES),b,400);
        b=body("COMPANY");b.put("titles",List.of(Map.of("reference","Sem vencimento","originalAmount","10.00")));call(f,post(PAYABLES),b,400);
        b=body("COMPANY");b.put("titles",List.of(title(" ","10.00","2030-01-01")));call(f,post(PAYABLES),b,400);
        assertEquals(groups,jdbc.queryForObject("select count(*) from payable_financial_group where company_id=?",Long.class,f.company()));
        assertEquals(0,jdbc.queryForObject("select count(*) from payable where company_id=?",Integer.class,f.company()));
    }

    @Test void approvalCreatesNoOfficialGroupUntilDecisionAndRevalidatesInactiveReferences()throws Exception{
        var f=owner();var requester=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.REQUEST_CREATE,AccessAction.READ)));
        var approver=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.APPROVE)));
        var b=body("COMPANY");var pending=call(requester,post(PAYABLES),b,202);assertTrue(pending.path("payables").isNull());assertTrue(pending.path("financialGroupId").isNull());
        assertEquals(0,call(f,get(PAYABLES),null,200).path("totalItems").asInt());assertEquals(0,jdbc.queryForObject("select count(*) from payable_financial_group where company_id=?",Integer.class,f.company()));
        String request=pending.path("changeRequestId").asText();assertEquals(request,call(requester,post(PAYABLES),b,202).path("changeRequestId").asText());
        call(requester,post(approve(request)),Map.of(),403);var approved=call(approver,post(approve(request)),Map.of(),200);
        assertEquals("PayableFinancialGroup",approved.at("/appliedEntity/type").asText());String group=approved.at("/appliedEntity/id").asText();
        assertEquals(1,call(f,get(PAYABLES).param("financialGroupId",group),null,200).path("totalItems").asInt());assertEquals(group,call(requester,post(PAYABLES),b,200).path("financialGroupId").asText());
        call(approver,post(approve(request)),Map.of(),409);
        String cat=category(f,"STALE","Categoria","TAX",201).at("/category/id").asText();b=body("COMPANY");b.put("financialCategoryId",cat);var stale=call(requester,post(PAYABLES),b,202);
        call(f,delete(CATEGORIES+"/"+cat).param("expectedVersion","0"),null,204);call(approver,post(approve(stale.path("changeRequestId").asText())),Map.of(),409);
        assertEquals("PENDING",jdbc.queryForObject("select status from change_request where id=?",String.class,UUID.fromString(stale.path("changeRequestId").asText())));
        assertEquals(1,call(f,get(PAYABLES),null,200).path("totalItems").asInt());
        var effective=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.REQUEST_CREATE,AccessAction.APPROVE)));
        assertEquals("DIRECT",call(effective,post(PAYABLES),body("COMPANY"),201).path("mode").asText());
    }

    @Test void idempotencySerializesConcurrentDirectAndPendingRequestsAndRejectsConflictingPayloads()throws Exception{
        var f=owner();var b=body("COMPANY");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var a=executor.submit(()->{gate.await();return code(f,post(PAYABLES),b);});var c=executor.submit(()->{gate.await();return code(f,post(PAYABLES),b);});gate.countDown();
            var codes=new ArrayList<>(List.of(a.get(60,TimeUnit.SECONDS),c.get(60,TimeUnit.SECONDS)));codes.sort(Integer::compare);assertEquals(List.of(200,201),codes);
            var requester=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.REQUEST_CREATE)));var pending=body("COMPANY");var next=new CountDownLatch(1);
            var d=executor.submit(()->{next.await();return call(requester,post(PAYABLES),pending,202);});var e=executor.submit(()->{next.await();return call(requester,post(PAYABLES),pending,202);});next.countDown();
            assertEquals(d.get(60,TimeUnit.SECONDS).path("changeRequestId").asText(),e.get(60,TimeUnit.SECONDS).path("changeRequestId").asText());
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from payable where company_id=?",Integer.class,f.company()));
        b.put("titles",List.of(title("Outro","20.00","2030-01-01")));call(f,post(PAYABLES),b,409);
        var other=owner();call(other,post(PAYABLES),b,201);
    }

    @Test void databaseProtectsHistoryTenantScopeCurrencyAndOptimisticStatusVersion()throws Exception{
        var f=owner();var work=work(f);var otherWork=work(f);var contract=contract(f,work,CurrencyCode.USD);
        var b=body("WORK");b.put("workId",work.getId());b.put("contractId",contract.getId());var p=call(f,post(PAYABLES),b,201).at("/payables/0");UUID id=UUID.fromString(p.path("id").asText());
        for(String sql:List.of("delete from payable where id=?","update payable set currency='BRL' where id=?","update payable set financial_scope='COMPANY' where id=?"))assertThrows(DataAccessException.class,()->jdbc.update(sql,id));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from payable_financial_group where id=?",UUID.fromString(p.path("financialGroupId").asText())));
        var current=payables.findByIdAndCompanyId(id,f.company()).orElseThrow();var stale=payables.findByIdAndCompanyId(id,f.company()).orElseThrow();
        var cancelled=payables.updateStatus(current.cancel());assertEquals(1,cancelled.getVersion());assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->payables.updateStatus(stale.cancel()));
        assertEquals("CANCELLED",call(f,get(PAYABLES+"/"+id),null,200).path("status").asText());
        // Database chain and currency must reject even inserts bypassing the application.
        UUID group=UUID.fromString(p.path("financialGroupId").asText());
        assertThrows(DataAccessException.class,()->jdbc.update("insert into payable(id,company_id,financial_scope,work_id,contract_id,financial_group_id,reference,due_on,original_amount,currency) values(?,?,'WORK',?,?,?,'Invalid',CURRENT_DATE,1.00,'USD')",UUID.randomUUID(),f.company(),otherWork.getId(),contract.getId(),group));
        var other=owner();String foreignSupplier=call(other,post(SUPPLIERS),Map.of("name","Outro tenant"),201).at("/supplier/id").asText();
        assertThrows(DataAccessException.class,()->jdbc.update("insert into payable(id,company_id,financial_scope,financial_group_id,supplier_id,reference,due_on,original_amount,currency) values(?,?,'COMPANY',?,?,'Invalid',CURRENT_DATE,1.00,'BRL')",UUID.randomUUID(),f.company(),group,UUID.fromString(foreignSupplier)));
    }

    @Test void executableOpenApiDeclaresRealPageRequestResponseDecimalsAndNullableApprovalExport()throws Exception{
        String text=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();var root=json.readTree(text);
        for(String s:List.of("200","201","202","400","401","403","404","409"))assertTrue(root.path("paths").path(PAYABLES).path("post").path("responses").has(s));
        String pageRef=root.path("paths").path(PAYABLES).at("/get/responses/200/content/application~1json/schema/$ref").asText();assertTrue(pageRef.endsWith("PageResponsePayableResponse"));
        var request=root.at("/components/schemas/PayableCreateRequest");assertFalse(request.path("properties").has("companyId"));assertTrue(request.path("required").toString().contains("financialScope"));
        assertEquals("string",root.at("/components/schemas/PayableTitleCreateRequest/properties/originalAmount/type").asText());assertEquals("decimal",root.at("/components/schemas/PayableTitleCreateRequest/properties/originalAmount/format").asText());
        assertEquals("string",root.at("/components/schemas/PayableResponse/properties/originalAmount/type").asText());
        assertTrue(root.at("/components/schemas/PayableCreationResponse/properties/payables/anyOf").isArray());
        String export=System.getenv("PAYABLE_OPENAPI_EXPORT_PATH");if(export!=null&&!export.isBlank()){Path target=Path.of(export);Files.createDirectories(target.toAbsolutePath().getParent());Files.writeString(target,text);}
    }

    @Test
    void filtersPayableViewsByPersistedStatusBeforePagination() throws Exception {
        var owner = owner();
        var body = body("COMPANY");
        var statuses = List.of("PAID", "CANCELLED", "RENEGOTIATED", "DRAFT", "OPEN", "OVERDUE", "PARTIALLY_PAID");
        var titles = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < statuses.size(); i++) {
            titles.add(title(statuses.get(i), "100.00", "2025-01-0" + (i + 1)));
        }
        body.put("titles", titles);
        var created = call(owner, post(PAYABLES), body, 201);
        String group = created.path("financialGroupId").asText();
        for (var item : created.path("payables")) {
            jdbc.update("update payable set status=? where id=?", item.path("reference").asText(),
                    UUID.fromString(item.path("id").asText()));
        }
        call(owner(), post(PAYABLES), body("COMPANY"), 201);

        var first = call(owner, get(PAYABLES).param("view", "OUTSTANDING").param("size", "1"), null, 200);
        assertEquals(3, first.path("totalItems").asInt());
        assertEquals("OPEN", first.at("/items/0/status").asText());
        assertTrue(first.at("/items/0/overdue").asBoolean());
        var last = call(owner, get(PAYABLES).param("view", "OUTSTANDING").param("page", "2").param("size", "1"), null, 200);
        assertEquals("PARTIALLY_PAID", last.at("/items/0/status").asText());
        assertTrue(last.at("/items/0/balance/outstandingAmount").isNull());

        var history = call(owner, get(PAYABLES).param("view", "HISTORY"), null, 200);
        assertEquals(4, history.path("totalItems").asInt());
        assertEquals(statuses.subList(0, 4), history.path("items").valueStream().map(item -> item.path("status").asText()).toList());
        assertEquals(7, call(owner, get(PAYABLES).param("financialGroupId", group), null, 200).path("totalItems").asInt());
        var combined = get(PAYABLES).param("view", "OUTSTANDING").param("status", "OVERDUE").param("financialGroupId", group);
        assertEquals(1, call(owner, combined, null, 200).path("totalItems").asInt());
        assertEquals(0, call(owner, get(PAYABLES).param("view", "HISTORY").param("status", "OVERDUE"), null, 200).path("totalItems").asInt());
        call(owner, get(PAYABLES).param("view", "INVALID"), null, 400);
    }

    @Test
    void recordsPartialAndTotalPaymentAndReopensAfterFullReversal() throws Exception {
        var owner = owner();
        var body = body("COMPANY");
        body.put("currency", "EUR");
        body.put("titles", List.of(title("Teste", "100.00", "2025-01-02")));
        String id = call(owner, post(PAYABLES), body, 201).at("/payables/0/id").asText();
        String path = PAYABLES + "/" + id;
        var first = payment("40.00");
        var partial = call(owner, post(path + "/settlements"), first, 201);
        assertEquals("PARTIALLY_PAID", partial.at("/balance/status").asText());
        assertEquals(1, call(owner, get(PAYABLES).param("view", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        assertEquals("40.00", partial.at("/balance/paidAmount").asText());
        assertEquals("60.00", partial.at("/balance/overdueAmount").asText());
        assertEquals(partial, call(owner, post(path + "/settlements"), first, 200));
        var changed = new LinkedHashMap<>(first);
        changed.put("amount", "30.00");
        assertEquals("PAYABLE_SETTLEMENT_OPERATION_CONFLICT", call(owner, post(path + "/settlements"), changed, 409).path("reason").asText());
        assertEquals("PAYABLE_SETTLEMENT_EXCEEDS_BALANCE", call(owner, post(path + "/settlements"), payment("60.01"), 409).path("reason").asText());
        var finalPayment = call(owner, post(path + "/settlements"), payment("60.00"), 201);
        assertEquals("PAID", finalPayment.at("/balance/status").asText());
        assertEquals(0, call(owner, get(PAYABLES).param("view", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        assertEquals(id, call(owner, get(PAYABLES).param("view", "HISTORY"), null, 200).at("/items/0/id").asText());
        assertEquals("EUR", finalPayment.at("/balance/currency").asText());
        String settlementId = finalPayment.at("/settlement/id").asText();
        String reverse = path + "/settlements/" + settlementId + "/reversals";
        var reversal = Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-01", "justification", "Estorno registrado");
        var reversed = call(owner, post(reverse), reversal, 201);
        assertEquals("-60.00", reversed.at("/settlement/amount").asText());
        assertEquals(settlementId, reversed.at("/settlement/reversalOfId").asText());
        assertEquals("60.00", reversed.at("/balance/outstandingAmount").asText());
        assertEquals(id, call(owner, get(PAYABLES).param("view", "OUTSTANDING"), null, 200).at("/items/0/id").asText());
        assertEquals(0, call(owner, get(PAYABLES).param("view", "HISTORY"), null, 200).path("totalItems").asInt());
        assertEquals(reversed, call(owner, post(reverse), reversal, 200));
        assertEquals("PAYABLE_SETTLEMENT_ALREADY_REVERSED", call(owner, post(reverse),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-01", "justification", "Estorno repetido"), 409).path("reason").asText());
        var history = call(owner, get(path + "/settlements").param("size", "1"), null, 200);
        assertEquals(3, history.path("totalItems").asInt());
        assertEquals("-60.00", history.at("/items/0/amount").asText());
        assertEquals("40.00", call(owner, get(path), null, 200).at("/balance/paidAmount").asText());
        assertEquals("40.00", call(owner, get(PAYABLES), null, 200).at("/items/0/balance/paidAmount").asText());
        call(owner(), get(path + "/balance"), null, 404);
        call(owner(), post(path + "/settlements"), payment("1.00"), 404);
        call(actor(owner.company(), Map.of()), get(path + "/settlements"), null, 403);
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from payable_settlement where id=?", UUID.fromString(settlementId)));
    }

    @Test
    void approvalLeavesOfficialBalanceIntactUntilDecisionAndRevalidatesIt() throws Exception {
        var owner = owner();
        var requester = actor(owner.company(), Map.of(AccessModule.PAYABLES,
                List.of(AccessAction.READ, AccessAction.REQUEST_CREATE, AccessAction.REQUEST_CANCEL)));
        var approver = actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.APPROVE)));
        var creation = body("WORK");
        creation.put("workId", work(owner).getId());
        String id = call(owner, post(PAYABLES), creation, 201).at("/payables/0/id").asText();
        String path = PAYABLES + "/" + id;
        var command = payment("10.00");
        var proposed = call(requester, post(path + "/settlements"), command, 202);
        assertFalse(proposed.has("settlement"));
        assertFalse(proposed.has("balance"));
        assertEquals(proposed, call(requester, post(path + "/settlements"), command, 202));
        assertEquals("0.00", call(owner, get(path + "/balance"), null, 200).path("paidAmount").asText());
        assertEquals(0, call(owner, get(path + "/settlements"), null, 200).path("totalItems").asInt());
        call(approver, post(approve(proposed.path("changeRequestId").asText())), Map.of(), 200);
        var applied = call(requester, post(path + "/settlements"), command, 200);
        assertEquals("10.00", applied.at("/balance/paidAmount").asText());
        var stale = call(requester, post(path + "/settlements"), payment("15.00"), 202);
        call(owner, post(path + "/settlements"), payment("1.00"), 201);
        assertEquals("PAYABLE_SETTLEMENT_EXCEEDS_BALANCE",
                call(approver, post(approve(stale.path("changeRequestId").asText())), Map.of(), 409).path("reason").asText());
        var reverse = call(requester, post(path + "/settlements/" + applied.at("/settlement/id").asText() + "/reversals"),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-01", "justification", "Estorno"), 202);
        assertEquals("11.00", call(owner, get(path + "/balance"), null, 200).path("paidAmount").asText());
        call(approver, post(approve(reverse.path("changeRequestId").asText())), Map.of(), 200);
        assertEquals("1.00", call(owner, get(path + "/balance"), null, 200).path("paidAmount").asText());
    }

    @Test
    void serializesCompetingPaymentsAndRejectsFutureDatesAndNumericMoney() throws Exception {
        var owner = owner();
        String id = call(owner, post(PAYABLES), body("COMPANY"), 201).at("/payables/0/id").asText();
        String path = PAYABLES + "/" + id + "/settlements";
        var invalid = new LinkedHashMap<>(payment("1.00"));
        invalid.put("paidOn", "2099-01-01");
        call(owner, post(path), invalid, 400);
        invalid.put("paidOn", "2025-01-01");
        invalid.put("amount", 1);
        call(owner, post(path), invalid, 400);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return code(owner, post(path), payment("25.00"));
            };
            var one = pool.submit(create);
            var two = pool.submit(create);
            assertEquals(List.of(201, 409), List.of(one.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    two.get(20, java.util.concurrent.TimeUnit.SECONDS)).stream().sorted().toList());
        }
    }

    @Test
    void aggregatesRealizedCashByEffectiveDateCurrencyScopeAndTenant() throws Exception {
        var owner = owner();
        var work = work(owner);
        var otherWork = work(owner);
        var foreign = owner();
        var foreignWork = work(foreign);
        var reader = actor(owner.company(), Map.of(AccessModule.RECEIVABLES, List.of(AccessAction.READ),
                AccessModule.PAYABLES, List.of(AccessAction.READ), AccessModule.WORKS, List.of(AccessAction.READ)));
        var contract = contract(owner, work, CurrencyCode.BRL);
        var otherContract = contract(owner, otherWork, CurrencyCode.USD);
        receipt(owner, contract, "100.00", "2025-01-01", "2025-02-01");
        receipt(owner, otherContract, "70.00", "2025-02-28", null);
        receipt(foreign, contract(foreign, foreignWork, CurrencyCode.BRL), "500.00", "2025-02-15", null);
        String workPayment = cashPayment(owner, work, "BRL", "50.00", "2025-01-31");
        cashPayment(owner, otherWork, "BRL", "8.00", "2025-02-15");
        cashPayment(owner, null, "BRL", "15.00", "2025-02-28");
        cashPayment(owner, null, "EUR", "21.00", "2025-02-15");
        cashPayment(foreign, null, "BRL", "900.00", "2025-02-15");
        var original = call(owner, get(PAYABLES + "/" + workPayment + "/settlements"), null, 200).at("/items/0/id").asText();
        call(owner, post(PAYABLES + "/" + workPayment + "/settlements/" + original + "/reversals"),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-01", "justification", "Estorno"), 201);

        var totals = cash(reader, "/realized", "2025-02-01", "2025-02-28", 200).path("totalsByCurrency");
        var brl = totals.get(0);
        assertEquals("BRL", brl.path("currency").asText());
        assertEquals("-100.00", brl.at("/receipts/amount").asText());
        assertEquals("-42.00", brl.at("/workPayments/amount").asText());
        assertEquals("15.00", brl.at("/companyPayments/amount").asText());
        assertEquals("-27.00", brl.at("/payments/amount").asText());
        assertEquals("-73.00", brl.path("netMovement").asText());
        assertEquals("70.00", totals.get(1).path("netMovement").asText());
        assertEquals("-21.00", totals.get(2).path("netMovement").asText());

        var workBrl = cash(reader, "/works/" + work.getId() + "/realized", "2025-02-01", "2025-02-28", 200)
                .at("/totalsByCurrency/0");
        assertEquals("-50.00", workBrl.at("/payments/amount").asText());
        assertEquals("-50.00", workBrl.path("netMovement").asText());
        assertTrue(workBrl.path("cashResultPercentage").isNull());
        assertFalse(workBrl.hasNonNull("companyPayments"));
        cash(reader, "/works/" + foreignWork.getId() + "/realized", "2025-02-01", "2025-02-28", 404);
    }

    @Test
    void accumulatesWorkCashFromFirstAuthorizedEffectiveMovement() throws Exception {
        var owner = owner();
        var work = work(owner);
        var otherWork = work(owner);
        var foreign = owner();
        var full = actor(owner.company(), Map.of(AccessModule.RECEIVABLES, List.of(AccessAction.READ),
                AccessModule.PAYABLES, List.of(AccessAction.READ), AccessModule.WORKS, List.of(AccessAction.READ)));
        var receiptsOnly = actor(owner.company(), Map.of(AccessModule.RECEIVABLES, List.of(AccessAction.READ),
                AccessModule.WORKS, List.of(AccessAction.READ)));
        var paymentsOnly = actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.READ),
                AccessModule.WORKS, List.of(AccessAction.READ)));
        receipt(owner, contract(owner, work, CurrencyCode.BRL), "300.00", "2025-03-05", null);
        cashPayment(owner, work, "BRL", "100.00", "2025-01-03");
        cashPayment(owner, work, "BRL", "50.00", "2025-04-02");
        cashPayment(owner, work, "USD", "20.00", "2024-12-28");
        cashPayment(owner, otherWork, "BRL", "900.00", "2024-01-01");
        cashPayment(owner, null, "BRL", "800.00", "2024-01-01");

        String base = "/api/v1/company/cash-flow/works/";
        String path = base + work.getId() + "/realized/accumulated";
        var result = call(full, get(path), null, 200);
        assertEquals("2024-12-28", result.path("from").asText());
        assertEquals(LocalDate.now(java.time.ZoneOffset.UTC).toString(), result.path("to").asText());
        var brl = result.at("/totalsByCurrency/0");
        assertEquals("300.00", brl.at("/receipts/amount").asText());
        assertEquals("150.00", brl.at("/payments/amount").asText());
        assertEquals("150.00", brl.path("netMovement").asText());
        assertEquals("50.00", brl.path("cashResultPercentage").asText());
        assertTrue(result.at("/totalsByCurrency/1/cashResultPercentage").isNull());
        var monthly = cash(full, "/works/" + work.getId() + "/realized", "2025-03-01", "2025-03-31", 200);
        assertEquals("100.00", monthly.at("/totalsByCurrency/0/cashResultPercentage").asText());
        var receipts = call(receiptsOnly, get(path), null, 200);
        assertEquals("2025-03-05", receipts.path("from").asText());
        assertTrue(receipts.at("/totalsByCurrency/0/cashResultPercentage").isNull());
        assertEquals("2024-12-28", call(paymentsOnly, get(path), null, 200).path("from").asText());
        assertTrue(call(full, get(base + work(owner).getId() + "/realized/accumulated"), null, 200).path("from").isNull());
        call(full, get(base + work(foreign).getId() + "/realized/accumulated"), null, 404);
        call(actor(owner.company(), Map.of(AccessModule.WORKS, List.of(AccessAction.READ))), get(path), null, 403);
    }

    @Test
    void exposesOnlyAuthorizedCashComponentsAndValidatesDates() throws Exception {
        var owner = owner();
        var work = work(owner);
        var reader = actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.READ)));
        var row = cash(reader, "/realized", "2025-02-01", "2025-02-28", 200).at("/totalsByCurrency/0");
        assertEquals("NO_PERMISSION", row.at("/receipts/coverage").asText());
        assertFalse(row.path("receipts").hasNonNull("amount"));
        assertFalse(row.path("receipts").hasNonNull("entryCount"));
        assertFalse(row.hasNonNull("netMovement"));
        assertEquals("RECORDED_MOVEMENTS", row.at("/payments/coverage").asText());
        assertEquals(0, row.at("/payments/entryCount").asInt());
        assertEquals("0.00", row.at("/payments/amount").asText());
        cash(reader, "/works/" + work.getId() + "/realized", "2025-02-01", "2025-02-28", 403);
        cash(actor(owner.company(), Map.of()), "/realized", "2025-02-01", "2025-02-28", 403);
        cash(reader, "/realized", "2025-02-28", "2025-02-01", 400);
        cash(reader, "/realized", "2025-02-01", "2099-02-28", 400);
        mvc.perform(get("/api/v1/company/cash-flow/realized").param("from", "2025-02-01").param("to", "2025-02-28"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void projectsCurrentResidualsWithSeparateBucketsScopesAndCoverage() throws Exception {
        var owner = owner();
        var work = work(owner);
        var foreign = owner();
        var foreignWork = work(foreign);
        var reader = actor(owner.company(), Map.of(AccessModule.RECEIVABLES, List.of(AccessAction.READ),
                AccessModule.PAYABLES, List.of(AccessAction.READ), AccessModule.WORKS, List.of(AccessAction.READ)));
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        var contract = contract(owner, work, CurrencyCode.BRL);
        receipt(owner, contract, "150.00", "100.00", "2025-01-01", null);
        var workTitle = body("WORK");
        workTitle.put("workId", work.getId());
        workTitle.put("titles", List.of(title("Obra", "70.00", today.toString()),
                title("Fora do horizonte", "900.00", today.plusDays(1).toString())));
        String workId = call(owner, post(PAYABLES), workTitle, 201).at("/payables/0/id").asText();
        call(owner, post(PAYABLES + "/" + workId + "/settlements"), payment("20.00"), 201);
        var companyTitle = body("COMPANY");
        companyTitle.put("titles", List.of(title("Vencido", "40.00", today.minusDays(1).toString()),
                title("Legado sem baixa", "60.00", today.minusDays(1).toString())));
        var created = call(owner, post(PAYABLES), companyTitle, 201);
        jdbc.update("update payable set status='PAID' where id=?",
                UUID.fromString(created.at("/payables/1/id").asText()));
        call(foreign, post(PAYABLES), companyTitle, 201);

        String base = "/api/v1/company/cash-flow";
        var report = call(reader, get(base + "/due").param("horizonDays", "1"), null, 200);
        assertEquals(report.path("asOf"), report.path("dueThrough"));
        var brl = report.at("/totalsByCurrency/0");
        assertEquals("50.00", brl.at("/receivables/overdue/amount").asText());
        assertEquals("50.00", brl.at("/payables/upcoming/amount").asText());
        assertEquals("40.00", brl.at("/payables/overdue/amount").asText());
        assertEquals("PARTIAL", brl.at("/companyPayables/overdue/coverage").asText());
        assertEquals(1, brl.at("/companyPayables/overdue/unavailableTitleCount").asInt());
        assertEquals("0.00", report.at("/totalsByCurrency/1/payables/overdue/amount").asText());

        var workOnly = call(reader, get(base + "/works/" + work.getId() + "/due").param("horizonDays", "1"), null, 200)
                .at("/totalsByCurrency/0");
        assertEquals("0.00", workOnly.at("/payables/overdue/amount").asText());
        assertFalse(workOnly.hasNonNull("companyPayables"));
        var payablesOnly = call(owner, get(base + "/due"), null, 200);
        assertEquals(30, payablesOnly.path("horizonDays").asInt());
        assertEquals("NO_PERMISSION", payablesOnly.at("/totalsByCurrency/0/receivables/overdue/coverage").asText());
        assertTrue(payablesOnly.at("/totalsByCurrency/0/receivables/overdue/amount").isNull());
        call(reader, get(base + "/works/" + foreignWork.getId() + "/due"), null, 404);
        call(reader, get(base + "/due").param("horizonDays", "366"), null, 400);
        call(actor(owner.company(), Map.of()), get(base + "/due"), null, 403);
    }

    @Test
    void comparesRecordedPaymentsUsingAdjacentPeriodsAndIndependentCoverageByScopeAndCurrency() throws Exception {
        var owner = owner();
        var work = work(owner);
        var reader = actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.READ),
                AccessModule.WORKS, List.of(AccessAction.READ)));
        var outsider = owner();
        cashPayment(outsider, null, "BRL", "900.00", "2025-02-10");
        cashPayment(owner, work, "BRL", "100.00", "2025-02-01");
        cashPayment(owner, work, "BRL", "150.00", "2025-02-11");
        String company = cashPayment(owner, null, "BRL", "50.00", "2025-02-10");
        var original = call(owner, get(PAYABLES + "/" + company + "/settlements"), null, 200).at("/items/0/id").asText();
        call(owner, post(PAYABLES + "/" + company + "/settlements/" + original + "/reversals"),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-20", "justification", "Estorno"), 201);
        cashPayment(owner, null, "BRL", "800.00", "2025-02-21");
        cashPayment(owner, null, "USD", "70.00", "2025-02-15");
        String euro = cashPayment(owner, null, "EUR", "25.00", "2025-02-02");
        String euroEntry = call(owner, get(PAYABLES + "/" + euro + "/settlements"), null, 200).at("/items/0/id").asText();
        call(owner, post(PAYABLES + "/" + euro + "/settlements/" + euroEntry + "/reversals"),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-03", "justification", "Estorno"), 201);
        cashPayment(owner, null, "EUR", "10.00", "2025-02-11");

        String path = "/payments/comparison";
        var response = cash(reader, path, "2025-02-11", "2025-02-20", 200);
        assertEquals("2025-02-01", response.at("/previousPeriod/from").asText());
        assertEquals("2025-02-10", response.at("/previousPeriod/to").asText());
        var brl = response.at("/totalsByCurrency/0");
        assertEquals("100.00", brl.at("/payments/current/amount").asText());
        assertEquals("150.00", brl.at("/payments/previous/amount").asText());
        assertEquals("-50.00", brl.at("/payments/differenceAmount").asText());
        assertEquals("-33.33", brl.at("/payments/percentageChange").asText());
        assertEquals("50.00", brl.at("/workPayments/percentageChange").asText());
        assertEquals("-100.00", brl.at("/companyPayments/differenceAmount").asText());
        assertEquals("INSUFFICIENT_RECORDS", response.at("/totalsByCurrency/1/payments/status").asText());
        assertEquals("NO_RECORDED_MOVEMENTS", response.at("/totalsByCurrency/1/payments/previous/coverage").asText());
        assertTrue(response.at("/totalsByCurrency/1/payments/differenceAmount").isNull());
        assertEquals("NON_POSITIVE_BASE", response.at("/totalsByCurrency/2/payments/status").asText());
        assertEquals("10.00", response.at("/totalsByCurrency/2/payments/differenceAmount").asText());
        assertTrue(response.at("/totalsByCurrency/2/payments/percentageChange").isNull());

        var workRow = cash(reader, "/works/" + work.getId() + path, "2025-02-11", "2025-02-20", 200)
                .at("/totalsByCurrency/0");
        assertFalse(workRow.has("companyPayments"));
        assertEquals("150.00", workRow.at("/payments/current/amount").asText());
        cash(reader, "/works/" + work(outsider).getId() + path, "2025-02-11", "2025-02-20", 404);
        cash(owner, "/works/" + work.getId() + path, "2025-02-11", "2025-02-20", 403);
        cash(actor(owner.company(), Map.of(AccessModule.RECEIVABLES, List.of(AccessAction.READ))), path,
                "2025-02-11", "2025-02-20", 403);
        cash(reader, path, "2025-02-20", "2025-02-11", 400);
        cash(reader, path, "2025-02-11", "2099-02-20", 400);
    }

    private JsonNode cash(Actor actor, String path, String from, String to, int status) throws Exception {
        return call(actor, get("/api/v1/company/cash-flow" + path).param("from", from).param("to", to), null, status);
    }

    @Test
    void exportsCashFlowPdfWithOfficialAmountsAndSourcePermissions() throws Exception {
        var owner = owner();
        var work = work(owner);
        var exporter = actor(owner.company(), Map.of(AccessModule.REPORTING, List.of(AccessAction.EXPORT),
                AccessModule.PAYABLES, List.of(AccessAction.READ, AccessAction.EXPORT),
                AccessModule.RECEIVABLES, List.of(AccessAction.READ), AccessModule.WORKS, List.of(AccessAction.READ)));
        cashPayment(owner, work, "BRL", "100.00", "2025-02-10");
        cashPayment(owner, work, "BRL", "150.00", "2025-02-11");
        cashPayment(owner, null, "BRL", "35.00", "2025-02-12");
        cashPayment(owner, null, "USD", "800.00", "2025-02-12");
        cashPayment(owner(), null, "BRL", "900.00", "2025-02-12");
        var title = body("COMPANY");
        title.put("titles", List.of(title("Vencido", "13.00", "2025-02-01"),
                title("A vencer", "21.00", LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2).toString())));
        call(owner, post(PAYABLES), title, 201);
        String base = "/api/v1/company/cash-flow";
        String query = "?from=2025-02-11&to=2025-02-20&currency=BRL&horizonDays=30";
        String company = downloadCashPdf(exporter, base + "/report/pdf" + query, "empresa");
        assertTrue(company.contains("Empresa  /  Empresa"));
        assertTrue(company.contains("185.00"));
        assertTrue(company.contains("Sem permissão de exportação"));
        assertTrue(company.contains("Exige acesso às duas fontes"));
        assertTrue(company.contains("13.00"));
        assertTrue(company.contains("21.00"));
        assertTrue(company.contains("Distribuição dos saldos apurados a pagar"));
        assertTrue(company.contains("38.2%"));
        assertTrue(company.contains("61.8%"));
        assertTrue(company.contains("100.00"));
        assertTrue(company.contains("85.00%"));
        assertFalse(company.contains("800.00"));
        assertFalse(company.contains("900.00"));
        String workPdf = downloadCashPdf(exporter, base + "/works/" + work.getId() + "/report/pdf" + query, "obra");
        assertTrue(workPdf.contains("150.00"));
        assertFalse(workPdf.contains("Pagamentos gerais"));
        call(exporter, get(base + "/works/" + work(owner()).getId() + "/report/pdf" + query), null, 404);
        call(owner, get(base + "/report/pdf" + query), null, 403);
        call(exporter, get(base + "/report/pdf" + query.replace("horizonDays=30", "horizonDays=0")), null, 400);
        assertEquals(2L, jdbc.queryForObject("""
                select count(*) from audit_event where company_id=? and action='EXPORT'
                and module='REPORTING' and metadata->>'format'='CASH_FLOW_PDF'
                """, Long.class, owner.company()));
    }

    private String downloadCashPdf(Actor actor, String path, String scope) throws Exception {
        var pending = mvc.perform(get(path).with(jwt().jwt(token -> token.subject(actor.user().toString()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        var response = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("application/pdf", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("attachment; filename=\"relatorio-financeiro-" + scope + "-BRL.pdf\"", response.getHeader("Content-Disposition"));
        byte[] pdf = response.getContentAsByteArray();
        String sample = System.getenv("CASH_FLOW_PDF_SAMPLE_PATH");
        if (sample != null && scope.equals("empresa")) Files.write(Path.of(sample), pdf);
        try (var document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages());
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void comparesPaymentsByCategoryIncludingInactiveAndUncategorizedTitles() throws Exception {
        var owner = owner();
        var work = work(owner);
        var outsider = owner();
        var reader = actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.READ),
                AccessModule.COSTS_EXPENSES, List.of(AccessAction.READ), AccessModule.WORKS, List.of(AccessAction.READ)));
        String category = call(owner, post(CATEGORIES), Map.of("code", "EPI", "name", "EPI", "type", "WORK_COST"), 201)
                .at("/category/id").asText();
        String sameName = call(owner, post(CATEGORIES), Map.of("code", "EPI_2", "name", "EPI", "type", "WORK_COST"), 201)
                .at("/category/id").asText();
        String title = cashPayment(owner, work, "BRL", "40.00", "2025-02-10", category);
        cashPayment(owner, work, "BRL", "100.00", "2025-02-11", category);
        cashPayment(owner, null, "BRL", "15.00", "2025-02-11", category);
        cashPayment(owner, work, "BRL", "10.00", "2025-02-10", sameName);
        cashPayment(owner, null, "USD", "9.00", "2025-02-11");
        cashPayment(outsider, null, "BRL", "900.00", "2025-02-11");
        call(owner, delete(CATEGORIES + "/" + category).param("expectedVersion", "0"), null, 204);
        String settlement = call(owner, get(PAYABLES + "/" + title + "/settlements"), null, 200).at("/items/0/id").asText();
        call(owner, post(PAYABLES + "/" + title + "/settlements/" + settlement + "/reversals"),
                Map.of("operationId", UUID.randomUUID(), "reversedOn", "2025-02-20", "justification", "Estorno"), 201);

        String route = "/payments/comparison/by-category";
        var rows = cash(reader, route, "2025-02-11", "2025-02-20", 200).path("categories");
        assertEquals(3, rows.size());
        assertEquals(category, rows.at("/0/categoryId").asText());
        assertFalse(rows.at("/0/categoryActive").asBoolean());
        assertEquals("75.00", rows.at("/0/payments/current/amount").asText());
        assertEquals("40.00", rows.at("/0/payments/previous/amount").asText());
        assertEquals("87.50", rows.at("/0/payments/percentageChange").asText());
        assertEquals("60.00", rows.at("/0/workPayments/current/amount").asText());
        assertEquals("15.00", rows.at("/0/companyPayments/current/amount").asText());
        assertEquals(sameName, rows.at("/1/categoryId").asText());
        assertEquals("INSUFFICIENT_RECORDS", rows.at("/1/payments/status").asText());
        assertTrue(rows.at("/2/categoryId").isNull());
        assertEquals("Sem categoria", rows.at("/2/categoryName").asText());
        assertEquals("USD", rows.at("/2/currency").asText());
        assertEquals("9.00", rows.at("/2/payments/current/amount").asText());
        var workRows = cash(reader, "/works/" + work.getId() + route, "2025-02-11", "2025-02-20", 200).path("categories");
        assertEquals(2, workRows.size());
        assertFalse(workRows.at("/0").has("companyPayments"));
        assertEquals("60.00", workRows.at("/0/payments/current/amount").asText());
        cash(reader, "/works/" + work(outsider).getId() + route, "2025-02-11", "2025-02-20", 404);
        cash(actor(owner.company(), Map.of(AccessModule.PAYABLES, List.of(AccessAction.READ))),
                route, "2025-02-11", "2025-02-20", 403);
        cash(reader, route, "2025-02-20", "2025-02-11", 400);
    }

    private String cashPayment(Actor actor, Work work, String currency, String amount, String date) throws Exception {
        return cashPayment(actor, work, currency, amount, date, null);
    }

    private String cashPayment(Actor actor, Work work, String currency, String amount, String date, String category) throws Exception {
        var command = body(work == null ? "COMPANY" : "WORK");
        if (work != null) command.put("workId", work.getId());
        if (category != null) command.put("financialCategoryId", category);
        command.put("currency", currency);
        command.put("titles", List.of(title("Despesa de teste", amount, "2025-01-01")));
        String id = call(actor, post(PAYABLES), command, 201).at("/payables/0/id").asText();
        var settlement = new LinkedHashMap<>(payment(amount));
        settlement.put("paidOn", date);
        call(actor, post(PAYABLES + "/" + id + "/settlements"), settlement, 201);
        return id;
    }

    private void receipt(Actor actor, Contract contract, String amount, String date, String reversalDate) {
        receipt(actor, contract, amount, amount, date, reversalDate);
    }

    private void receipt(Actor actor, Contract contract, String originalAmount, String amount, String date, String reversalDate) {
        var due = LocalDate.parse("2025-03-01");
        var installment = cashInstallments.save(com.renovar.canteiro.io.receivables.domain.BillingInstallment.createApproved(
                actor.company(), contract.getId(), "Cobrança de teste", null, null, due));
        var receivable = cashReceivables.save(com.renovar.canteiro.io.receivables.domain.Receivable.create(actor.company(),
                contract.getId(), installment.getId(), UUID.randomUUID(), null, null, null, due, new BigDecimal(originalAmount)));
        UUID id = UUID.randomUUID();
        String insert = """
                insert into receivable_settlement(id,company_id,receivable_id,contract_id,received_on,amount,payment_method,
                    status,entry_type,reversal_of_settlement_id) values (?,?,?,?,?,?,'PIX','CONFIRMED',?,?)
                """;
        jdbc.update(insert, id, actor.company(), receivable.getId(), contract.getId(), LocalDate.parse(date),
                new BigDecimal(amount), "SETTLEMENT", null);
        if (reversalDate != null) {
            jdbc.update(insert, UUID.randomUUID(), actor.company(), receivable.getId(), contract.getId(),
                    LocalDate.parse(reversalDate), new BigDecimal(amount).negate(), "REVERSAL", id);
        }
    }

    private Map<String, Object> payment(String amount) {
        return Map.of("operationId", UUID.randomUUID(), "amount", amount, "paidOn", "2025-01-01", "paymentMethod", "PIX");
    }

    Map<String,Object> body(String scope){var b=new LinkedHashMap<String,Object>();b.put("operationId",UUID.randomUUID());b.put("financialScope",scope);b.put("titles",List.of(title("Título","25.00","2030-01-01")));return b;}
    Map<String,Object> title(String ref,String amount,String due){return Map.of("reference",ref,"originalAmount",amount,"dueOn",due);}
    Work work(Actor f){var customer=customers.findByCompanyIdAndDocument(f.company(),"12345678000195").orElseGet(()->customers.save(FinalCustomer.create(f.company(),FinalCustomerType.LEGAL,"Cliente identificável","12345678000195")));
        return works.save(Work.create(f.company(),customer.getId(),"Obra identificável","OB-1",WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));}
    Contract contract(Actor f,Work work,CurrencyCode currency){return contracts.save(Contract.create(f.company(),work.getId(),"Contrato padrão",ContractPricingMode.FIXED,"CT-1","Contrato identificável",ContractStatus.ACTIVE,null,null,null,currency));}
}
