package com.renovar.canteiro.io.measurements;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeasurementContinuousApiIntegrationTest extends AbstractPostgresIntegrationTest {
    // Allows synthetic historical cash fixtures for continuity/attribution regressions, not a production bypass.
    // Unmocked REST containment is tested separately by BillingReceiptsAndSettlementDateApiIntegrationTest.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.renovar.canteiro.io.receivables.application.ReceivableSettlementCreationPolicy legacyFixturePolicy;
    @Autowired MockMvc mvc;
    @Autowired CompanyRepository companies;
    @Autowired FinalCustomerRepository customers;
    @Autowired WorkRepository works;
    @Autowired ContractRepository contracts;
    @Autowired ContractServiceRepository contractServices;
    @Autowired UserRepository users;
    @Autowired CompanyUserRepository memberships;
    @Autowired PasswordHasher passwords;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @Autowired UserRoleRepository userRoles;
    @Autowired MeasurementContractCompositionRepository compositions;
    @Autowired BillingAllocationRepository allocations;
    @Autowired MeasurementContinuityRepository continuity;
    @Autowired ChangeRequestRepository changeRequests;
    @Autowired Clock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider storage;
    final ObjectMapper json=new ObjectMapper();

    @Test
    void excludesRetiredArtifactsButKeepsFiveAcceptedActivitiesAndReusesTheirServicesAcrossAdvancesAndCorrections() throws Exception {
        Fixture f=fixture(false);UUID mid=measurement(f,false),v1=version(f,mid);
        UUID vine=service(f,mid,"Linha de Vinhaça",null,null),copper=service(f,mid,"Linha de cobre",null,null);
        for(int n=1;n<=5;n++)item(f,mid,v1,n<=3?vine:copper,n,"Atividade "+n,"1.0000","10.00");
        accept(f,mid,v1);
        JsonNode historical=call(f,get(base(mid)+"/versions/"+v1),null,200);
        UUID v2=revision(f,mid);
        for(int n=0;n<8;n++) {
            UUID orphan=UUID.randomUUID();
            jdbc.update("insert into measurement_continuous_service(id,company_id,measurement_id,service_title,sort_order) values (?,?,?,?,?)",
                orphan,f.company.getId(),mid,"Sobra histórica "+n,n+10);
            jdbc.update("""
                insert into measurement_continuous_service_retirement(company_id,measurement_id,continuous_service_id,source_migration,origin_created_at,reason)
                select company_id,measurement_id,id,'78',created_at,'V78_ORPHANED_COPY_GROUP' from measurement_continuous_service where id=?
                """,orphan);
            call(f,put(base(mid)+"/services/"+orphan+"/destination"),Map.of("expectedVersion",0,"destinationAction","CREATE_NEW_SERVICE"),404);
        }
        var empty=accumulated(f,mid);
        assertEquals(2,empty.path("services").size());
        assertEquals(3,empty.at("/services/0/items").size());assertEquals(2,empty.at("/services/1/items").size());
        assertEquals("30.00",empty.at("/services/0/acceptedTotals/netAmount").asText());
        assertEquals("20.00",empty.at("/services/1/acceptedTotals/netAmount").asText());
        assertEquals("50.00",empty.at("/acceptedTotals/netAmount").asText());
        assertEquals(2,call(f,get(base(mid)+"/services"),null,200).size());
        byte[] pdf=download(f,mid,"pdf"),xlsx=download(f,mid,"spreadsheet");
        try(var doc=Loader.loadPDF(pdf)) {
            String text=new PDFTextStripper().getText(doc);
            assertFalse(text.contains("Sobra histórica"));assertTrue(text.contains("Subtotal líquido aceito"));
            assertTrue(text.contains("R$ 30,00"));assertTrue(text.contains("R$ 20,00"));assertTrue(text.contains("R$ 50,00"));
        }
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            long rows=0,groups=0,subtotals=0;
            for(var row:workbook.getSheetAt(0)) {
                if(row.getCell(0)!=null) {
                    String label=row.getCell(0).toString();assertFalse(label.contains("Sobra histórica"));
                    if(label.startsWith("SERVIÇO:"))groups++;
                    if(label.equals("Subtotal líquido aceito"))subtotals++;
                }
                if(row.getCell(13)!=null&&!row.getCell(13).toString().isBlank()&&!row.getCell(13).toString().equals("Item ID"))rows++;
            }
            assertEquals(5,rows);assertEquals(2,groups);assertEquals(2,subtotals);
        }
        Files.createDirectories(Path.of("tmp","pdfs"));Files.write(Path.of("tmp","pdfs","servicos-acumulados-corrigidos.pdf"),pdf);
        Files.createDirectories(Path.of("tmp","sheets"));Files.write(Path.of("tmp","sheets","servicos-acumulados-corrigidos.xlsx"),xlsx);
        UUID addition=item(f,mid,v2,vine,1,"Novo avanço","2.0000","10.00");
        var draft=accumulated(f,mid);
        assertEquals(2,draft.path("services").size());assertEquals(4,draft.at("/services/0/items").size());
        assertEquals("50.00",draft.at("/acceptedTotals/netAmount").asText());assertEquals("20.00",draft.at("/pendingTotals/netAmount").asText());
        assertTrue(draft.at("/services/0/items/3/editable").asBoolean());assertFalse(draft.at("/services/0/items/0/editable").asBoolean());
        accept(f,mid,v2);
        var correction=call(f,post(base(mid)+"/versions/"+v2+"/items/"+addition+"/corrective-revision"),
            Map.of("action","REPLACE","itemNumber",1,"measuredOn","2026-10-11","serviceTitle","Título não é chave","activity","Novo avanço corrigido","chargeType","LINEAR_METER",
                "linearMeters",1,"unitPrice","15.00","justification","Correção conferida"),201);
        assertEquals("70.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        assertEquals("-5.00",accumulated(f,mid).at("/pendingTotals/netAmount").asText());
        accept(f,mid,UUID.fromString(correction.at("/version/id").asText()));
        var corrected=accumulated(f,mid);
        assertEquals(2,corrected.path("services").size());assertEquals("65.00",corrected.at("/acceptedTotals/netAmount").asText());
        assertEquals(6,corrected.at("/acceptedTotals/itemCount").asInt());
        JsonNode after=call(f,get(base(mid)+"/versions/"+v1),null,200);
        assertEquals(historical.path("items"),after.path("items"));assertEquals(historical.path("netAmount"),after.path("netAmount"));
        UUID intentional=service(f,mid,"Linha de Vinhaça",null,null);
        assertEquals(3,accumulated(f,mid).path("services").size());
        assertTrue(continuity.findService(f.company.getId(),intentional).isPresent());
        try(var document=Loader.loadPDF(download(f,mid,"pdf"))) {
            assertEquals(3,new PDFTextStripper().getText(document).split("Subtotal líquido aceito",-1).length-1);
        }
        assertEquals(11,jdbc.queryForObject("select count(*) from measurement_continuous_service where measurement_id=?",Integer.class,mid));
    }

    @Test
    void keepsHistoricalAttributionAnomaliesReadableWithoutPublishingInventedFinancialValues() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Histórico","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","1.00");accept(f,mid,vid);
        var c=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        var bill=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01","allocations",List.of(
            Map.of("contractServiceId",c.getContractServiceId(),"measurementContractCompositionId",c.getId(),"type","SERVICE","allocatedAmount","0.50"),
            Map.of("contractServiceId",c.getContractServiceId(),"measurementContractCompositionId",c.getId(),"type","SERVICE","allocatedAmount","0.50"))),201);
        UUID rid=UUID.fromString(bill.at("/receivable/id").asText()),settlement=UUID.randomUUID();
        jdbc.update("insert into receivable_settlement(id,company_id,receivable_id,contract_id,received_on,amount,payment_method,status,entry_type) values (?,?,?,?,?,?,'PIX','CONFIRMED','SETTLEMENT')",
            settlement,f.company.getId(),rid,f.contract.getId(),LocalDate.of(2026,10,13),new BigDecimal("0.99"));
        // Reproduces a historical immutable rounding residual concentrated in one origin.
        for(int index=0;index<2;index++) jdbc.update("insert into receivable_settlement_allocation(company_id,settlement_id,billing_allocation_id,amount) values (?,?,?,?)",
            f.company.getId(),settlement,UUID.fromString(bill.at("/billingInstallment/allocations/"+index+"/id").asText()),new BigDecimal(index==0?"0.00":"0.99"));
        var financial=accumulated(f,mid).path("financialSummary");
        assertEquals("1.00",financial.path("acceptedAmount").asText());assertTrue(financial.path("receivedAmount").isNull());
        assertEquals("LEGACY_ORIGIN_REQUIRES_RECONCILIATION",financial.path("financialAvailability").asText());
        call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-13","amount","0.01","paymentMethod","PIX"),409);
    }

    @Test
    void neverInventsZeroCashForAnIndividuallyConvertedLegacyOrigin() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Legado",null,null);
        UUID item=item(f,mid,vid,sid,1,"Serviço antigo","4.0000","25.00");accept(f,mid,vid);
        call(f,post(base(mid)+"/versions/"+vid+"/items/"+item+"/contract-service"),null,200);
        var financial=accumulated(f,mid).path("financialSummary");
        assertEquals("100.00",financial.path("acceptedAmount").asText());
        assertEquals("LEGACY_ORIGIN_REQUIRES_RECONCILIATION",financial.path("financialAvailability").asText());
        assertTrue(financial.path("receivedAmount").isNull());assertTrue(financial.path("remainingToReceive").isNull());
    }

    @Test
    void attributesTwoOriginsAndWithholdingAfterPartialReceiptWithoutLosingACent() throws Exception {
        Fixture f=fixture(true);UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"A","CREATE_NEW_SERVICE",null);
        item(f,a,av,as,1,"A","1.0000","0.01");accept(f,a,av);
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"B","CREATE_NEW_SERVICE",null);
        item(f,b,bv,bs,1,"B","1.0000","0.01");accept(f,b,bv);
        var ca=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
        var cb=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
        var billed=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01","allocations",List.of(
            Map.of("contractServiceId",ca.getContractServiceId(),"measurementContractCompositionId",ca.getId(),"type","SERVICE","allocatedAmount","0.01"),
            Map.of("contractServiceId",cb.getContractServiceId(),"measurementContractCompositionId",cb.getId(),"type","SERVICE","allocatedAmount","0.01"))),201);
        String rid=billed.at("/receivable/id").asText();
        var received=call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-13","amount","0.01","paymentMethod","PIX"),201);
        BigDecimal beforeA=new BigDecimal(accumulated(f,a).at("/financialSummary/receivedAmount").asText());
        BigDecimal beforeB=new BigDecimal(accumulated(f,b).at("/financialSummary/receivedAmount").asText());
        assertEquals(new BigDecimal("0.01"),beforeA.add(beforeB));
        List<Map<String,Object>> invoiceItems=new ArrayList<>();
        for(var line:billed.at("/billingInstallment/allocations"))invoiceItems.add(Map.of("billingAllocationId",line.path("id").asText(),"allocatedAmount","0.01"));
        var invoice=call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number",UUID.randomUUID().toString(),"issuedOn","2026-10-13","items",invoiceItems),201);
        call(f,post("/api/v1/company/invoices/"+invoice.at("/invoice/id").asText()+"/tax-obligations"),
            Map.of("receivableId",rid,"name","Retenção","amount","0.01","treatment","WITHHELD_BY_CUSTOMER"),201);
        assertEquals(beforeA,new BigDecimal(accumulated(f,a).at("/financialSummary/receivedAmount").asText()));
        assertEquals(beforeB,new BigDecimal(accumulated(f,b).at("/financialSummary/receivedAmount").asText()));
        assertEquals("0.00",accumulated(f,a).at("/financialSummary/remainingToReceive").asText());
        assertEquals("0.00",accumulated(f,b).at("/financialSummary/remainingToReceive").asText());
        call(f,post("/api/v1/company/receivables/"+rid+"/settlements/"+received.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Estorno de centavo"),201);
        BigDecimal remaining=new BigDecimal(accumulated(f,a).at("/financialSummary/remainingToReceive").asText())
            .add(new BigDecimal(accumulated(f,b).at("/financialSummary/remainingToReceive").asText()));
        assertEquals(new BigDecimal("0.01"),remaining);
    }

    @Test
    void serializesConcurrentCompositionAndOriginBillingWithoutDuplicatingValue() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Concorrência","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Origem","4.0000","25.00");
        Fixture recorder=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT)));
        accept(recorder,mid,vid);
        assertEquals(0,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate=new CountDownLatch(1);
            var one=executor.submit(()->{gate.await();return call(f,post(base(mid)+"/contract-compositions/apply"),Map.of(),200);});
            var two=executor.submit(()->{gate.await();return call(f,post(base(mid)+"/contract-compositions/apply"),Map.of(),200);});
            gate.countDown();one.get(60,TimeUnit.SECONDS);two.get(60,TimeUnit.SECONDS);
            assertEquals(1,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
            var c=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
            Map<String,Object> payload=Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01","allocations",List.of(
                Map.of("contractServiceId",c.getContractServiceId(),"measurementContractCompositionId",c.getId(),"type","SERVICE","allocatedAmount","60.00")));
            CountDownLatch billGate=new CountDownLatch(1);
            var b1=executor.submit(()->{billGate.await();return rawStatus(f,post("/api/v1/company/billing-installments"),payload);});
            var b2=executor.submit(()->{billGate.await();return rawStatus(f,post("/api/v1/company/billing-installments"),payload);});
            billGate.countDown();List<Integer> statuses=new ArrayList<>(List.of(b1.get(60,TimeUnit.SECONDS),b2.get(60,TimeUnit.SECONDS)));statuses.sort(Integer::compare);
            assertEquals(List.of(201,409),statuses);
            assertEquals("60.00",accumulated(f,mid).at("/financialSummary/billedAmount").asText());
            assertEquals("40.00",accumulated(f,mid).at("/financialSummary/acceptedAvailableToBill").asText());
        }
    }

    @Test
    void preventsAcceptedReductionUntilTheOriginalBillingIsCancelled() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Redução faturada","CREATE_NEW_SERVICE",null);
        UUID original=item(f,mid,vid,sid,1,"Original","4.0000","25.00");accept(f,mid,vid);
        var bill=bill(f,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst(),"70.00");
        var correction=call(f,post(base(mid)+"/versions/"+vid+"/items/"+original+"/corrective-revision"),
            Map.of("action","VOID","justification","Anulação conferida"),201);
        UUID cv=UUID.fromString(correction.at("/version/id").asText());
        workflow(f,mid,cv,"SEND",null);workflow(f,mid,cv,"AWAIT_EXTERNAL_ACCEPTANCE",null);
        call(f,post(base(mid)+"/versions/"+cv+"/workflow"),Map.of("action","RECORD_EXTERNAL_ACCEPTANCE","externallyAccepted",true,"externalAcceptanceOn","2026-10-11"),409);
        assertEquals("100.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        call(f,post("/api/v1/company/billing-installments/"+bill.at("/billingInstallment/id").asText()+"/cancellation"),Map.of("reason","Estorno antes da correção"),200);
        workflow(f,mid,cv,"RECORD_EXTERNAL_ACCEPTANCE",true);
        assertEquals("0.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
    }

    @Test
    void separatesAcceptanceFromServiceApprovalAndReusesThePendingOriginRequest() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Composição sujeita a alçada","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Origem","4.0000","25.00");
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT),
            AccessModule.SERVICES,List.of(AccessAction.REQUEST_CREATE)));
        accept(requester,mid,vid);
        assertEquals(0,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        JsonNode states=call(f,get(base(mid)+"/contract-compositions"),null,200);
        String request=states.get(0).path("changeRequestId").asText();
        assertEquals("AWAITING_APPROVAL",states.get(0).path("state").asText());
        workflow(requester,mid,vid,"RECORD_EXTERNAL_ACCEPTANCE",true);
        assertEquals(request,call(f,get(base(mid)+"/contract-compositions"),null,200).get(0).path("changeRequestId").asText());
        assertEquals(0,contractServices.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());
        assertEquals("0.00",officialBilling(f).path("contractNetAmount").asText());
        Fixture approver=actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.APPROVE)));
        call(approver,post("/api/v1/company/change-requests/"+request+"/approve"),Map.of(),200);
        assertEquals(1,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        assertEquals(new BigDecimal("100.00"),contractServices.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).getFirst().getEffectiveNetAmount());
        UUID appliedTarget=continuity.findService(f.company.getId(),sid).orElseThrow().contractServiceId();
        assertOfficialService(f,appliedTarget,"0.00","100.00","100.00");
        assertEquals("100.00",officialBilling(f).path("contractNetAmount").asText());
        assertEquals("APPLIED",call(f,get(base(mid)+"/contract-compositions"),null,200).get(0).path("state").asText());
    }

    @Test
    void workflowApprovalDoesNotChangeOfficialItemsTotalsOrOrderAndDeduplicatesRetries() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Alçada","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Item oficial","2.0000","50.00");
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.REQUEST_UPDATE)));
        JsonNode proposal=call(requester,post(base(mid)+"/versions/"+vid+"/workflow"),Map.of("action","SEND"),202);
        String cr=proposal.path("changeRequestId").asText();
        assertEquals(cr,call(requester,post(base(mid)+"/versions/"+vid+"/workflow"),Map.of("action","SEND"),202).path("changeRequestId").asText());
        assertEquals("DRAFT",accumulated(f,mid).at("/currentVersion/status").asText());
        assertEquals("0.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        call(requester,post("/api/v1/company/change-requests/"+cr+"/approve"),Map.of(),403);
        Fixture approver=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.APPROVE)));
        call(approver,post("/api/v1/company/change-requests/"+cr+"/approve"),Map.of(),200);
        assertEquals("SENT",accumulated(f,mid).at("/currentVersion/status").asText());
    }

    @Test
    void reconcilesCorrectiveCopiesAndVoidWithoutRecountingTheFrozenOriginal() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Correção","CREATE_NEW_SERVICE",null);
        UUID original=item(f,mid,vid,sid,1,"Original","4.0000","25.00");accept(f,mid,vid);
        UUID target=continuity.findService(f.company.getId(),sid).orElseThrow().contractServiceId();
        var correction=call(f,post(base(mid)+"/versions/"+vid+"/items/"+original+"/corrective-revision"),
            Map.of("action","REPLACE","itemNumber",1,"serviceTitle","Correção","activity","Corrigida","measuredOn","2026-10-11",
                "chargeType","LINEAR_METER","linearMeters",2,"unitPrice","25.00","justification","Metragem conferida"),201);
        UUID cv=UUID.fromString(correction.at("/version/id").asText());
        assertEquals("100.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        assertEquals("-50.00",accumulated(f,mid).at("/pendingTotals/netAmount").asText());
        accept(f,mid,cv);
        assertEquals("50.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        assertEquals(1,accumulated(f,mid).at("/services/0/items").size());
        assertEquals(new BigDecimal("50.00"),contractServices.findByIdAndCompanyId(target,f.company.getId()).orElseThrow().getEffectiveNetAmount());
        assertTrue(continuity.isReversed(f.company.getId(),compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst().getId()));
        UUID corrected=UUID.fromString(correction.at("/item/id").asText());
        var voided=call(f,post(base(mid)+"/versions/"+cv+"/items/"+corrected+"/corrective-revision"),Map.of("action","VOID","justification","Anulação conferida"),201);
        accept(f,mid,UUID.fromString(voided.at("/version/id").asText()));
        assertEquals("0.00",accumulated(f,mid).at("/acceptedTotals/netAmount").asText());
        assertEquals(new BigDecimal("0.00"),contractServices.findByIdAndCompanyId(target,f.company.getId()).orElseThrow().getEffectiveNetAmount());
        assertEquals("100.00",call(f,get(base(mid)+"/versions/"+vid),null,200).path("netAmount").asText());
    }

    @Test
    void preservesFinancialOriginAcrossCreditRenegotiationPartialReceiptAndReversals() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Financeiro","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","10.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        var bill=bill(f,origin,"700.00");String rid=bill.at("/receivable/id").asText();
        var receipt=call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-11","amount","400.00","paymentMethod","PIX"),201);
        var credit=call(f,post("/api/v1/company/receivables/"+rid+"/credits"),Map.of("amount","50.00","reason","Crédito negociado"),201);
        assertEquals("550.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        var reneg=call(f,post("/api/v1/company/receivables/"+rid+"/renegotiations"),Map.of("reason","Novo prazo",
            "installments",List.of(Map.of("dueOn","2027-01-01","amount","100.00"),Map.of("dueOn","2027-02-01","amount","150.00"))),200);
        assertEquals("700.00",accumulated(f,mid).at("/financialSummary/billedAmount").asText());
        String child=reneg.at("/titles/0/id").asText();
        var partial=call(f,post("/api/v1/company/receivables/"+child+"/settlements"),Map.of("receivedOn",LocalDate.now(clock).toString(),"amount","40.00","paymentMethod","PIX"),201);
        assertEquals("440.00",accumulated(f,mid).at("/financialSummary/receivedAmount").asText());
        assertEquals("510.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        call(f,post("/api/v1/company/receivables/"+rid+"/renegotiations/reversal"),Map.of("reason","Reabrir título"),409);
        call(f,post("/api/v1/company/receivables/"+child+"/settlements/"+partial.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Estorno"),201);
        call(f,post("/api/v1/company/receivables/"+rid+"/renegotiations/reversal"),Map.of("reason","Reabrir título"),200);
        call(f,post("/api/v1/company/receivables/"+rid+"/credits/"+credit.path("id").asText()+"/reversal"),Map.of("reason","Reverter crédito"),201);
        assertEquals("600.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        assertEquals("400.00",accumulated(f,mid).at("/financialSummary/receivedAmount").asText());
    }

    @Test
    void totalsCurrentHeaderDiscountByStableServiceAndDeniesUnauthorizedReadAndExport() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,false),vid=version(f,mid),a=service(f,mid,"A",null,null),b=service(f,mid,"B",null,null);
        item(f,mid,vid,a,1,"A","1.0000","100.00");item(f,mid,vid,b,2,"B","1.0000","100.00");
        call(f,post(base(mid)+"/versions/"+vid+"/discount"),Map.of("discountType","FIXED","discountValue","50.00"),201);
        var accumulated=accumulated(f,mid);
        assertEquals("150.00",accumulated.path("pendingTotals").path("netAmount").asText());
        assertEquals("75.00",accumulated.at("/services/0/pendingTotals/netAmount").asText());
        Fixture noAccess=actor(f,Map.of());
        call(noAccess,get(base(mid)+"/accumulated"),null,403);
        call(noAccess,get(base(mid)+"/accumulated/pdf"),null,403);
    }

    JsonNode bill(Fixture f,MeasurementContractComposition c,String amount)throws Exception {
        return call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
            "allocations",List.of(Map.of("contractServiceId",c.getContractServiceId(),"measurementContractCompositionId",c.getId(),"type","SERVICE","allocatedAmount",amount))),201);
    }
    Fixture actor(Fixture f,Map<AccessModule,List<AccessAction>> grants) {
        User u=User.create(UUID.randomUUID()+"@example.com",UserType.COMPANY);u.activate(passwords.hash("Canteiro#2026Seguro"),Instant.now());u=users.save(u);
        memberships.save(CompanyUser.create(u.getId(),f.company.getId()));Role role=roles.save(Role.create(f.company.getId(),"Alçada "+UUID.randomUUID(),null));
        grants.forEach((module,actions)->actions.forEach(action->rolePermissions.save(RolePermission.create(role.getId(),permissions.findByModuleAndAction(module,action).orElseThrow().getId()))));
        userRoles.save(UserRole.create(u.getId(),role.getId(),f.company.getId()));return new Fixture(f.company,u,f.work,f.contract);
    }

    @Test
    void keepsAcceptedItemsInTheMainBodyAcrossEmptyAndPopulatedAdvancesAndComposesOnlyNewValue() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),v1=version(f,mid);
        UUID service=service(f,mid,"Linha de vinhaça","CREATE_NEW_SERVICE",null);
        item(f,mid,v1,service,1,"Trecho 1","10.0000","100.00");
        accept(f,mid,v1);
        JsonNode accepted=accumulated(f,mid);
        assertEquals("1000.00",accepted.at("/acceptedTotals/netAmount").asText());
        UUID target=UUID.fromString(accepted.at("/services/0/service/contractServiceId").asText());
        assertEquals(new BigDecimal("1000.00"),contractServices.findByIdAndCompanyId(target,f.company.getId()).orElseThrow().getEffectiveNetAmount());
        // Repeating acceptance does not create a service or financial increment.
        workflow(f,mid,v1,"RECORD_EXTERNAL_ACCEPTANCE",true);
        assertEquals(1,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        UUID v2=revision(f,mid);
        JsonNode empty=accumulated(f,mid);
        assertEquals(1,empty.at("/services/0/items").size());
        assertFalse(empty.at("/services/0/items/0/editable").asBoolean());
        assertEquals("1000.00",empty.at("/acceptedTotals/netAmount").asText());
        item(f,mid,v2,service,1,"Trecho 2","2.0000","150.00");
        JsonNode draft=accumulated(f,mid);
        assertEquals(2,draft.at("/services/0/items").size());
        assertTrue(draft.at("/services/0/items/1/editable").asBoolean());
        assertEquals("1000.00",draft.at("/acceptedTotals/netAmount").asText());
        assertEquals("300.00",draft.at("/pendingTotals/netAmount").asText());
        assertEquals(new BigDecimal("1000.00"),contractServices.findByIdAndCompanyId(target,f.company.getId()).orElseThrow().getEffectiveNetAmount());
        accept(f,mid,v2);
        assertEquals(new BigDecimal("1300.00"),contractServices.findByIdAndCompanyId(target,f.company.getId()).orElseThrow().getEffectiveNetAmount());
        assertEquals(1,contractServices.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());
        assertEquals("1300.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        assertEquals(2,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
    }

    @Test
    void linksAnAcceptedStandaloneMeasurementAndPreservesItsDestination() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,false),vid=version(f,mid);
        UUID sid=service(f,mid,"Linha de cobre","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução avulsa","4.0000","25.00");accept(f,mid,vid);
        JsonNode loose=accumulated(f,mid);
        assertEquals("NO_CONTRACT",loose.at("/financialSummary/financialAvailability").asText());
        assertTrue(loose.at("/financialSummary/receivedAmount").isNull());
        assertEquals(0,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        call(f,put(base(mid)+"/link"),Map.of("workId",f.work.getId(),"contractId",f.contract.getId()),200);
        assertEquals(1,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        call(f,post(base(mid)+"/contract-compositions/apply"),Map.of(),200);
        assertEquals(1,compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).size());
        assertNotNull(continuity.findService(f.company.getId(),sid).orElseThrow().contractServiceId());
    }

    @Test
    void attributesPartialBillingReceiptsAndReversalsToOnlyTheOriginatingMeasurement() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Tubulação",null,
            ContractServiceStatus.ACTIVE,BigDecimal.ONE,BigDecimal.ZERO));
        UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"Serviço A","ADD_TO_EXISTING_SERVICE",target.getId());
        item(f,a,av,as,1,"A","10.0000","100.00");accept(f,a,av);
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Serviço B","ADD_TO_EXISTING_SERVICE",target.getId());
        item(f,b,bv,bs,1,"B","10.0000","100.00");accept(f,b,bv);
        UUID origin=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst().getId();
        JsonNode billed=call(f,post("/api/v1/company/billing-installments"),
            Map.of("contractId",f.contract.getId(),"issuedOn","2026-10-01","dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",target.getId(),"measurementContractCompositionId",origin,"type","SERVICE","allocatedAmount","700.00"))),201);
        UUID receivable=UUID.fromString(billed.at("/receivable/id").asText());
        JsonNode payment=call(f,post("/api/v1/company/receivables/"+receivable+"/settlements"),
            Map.of("receivedOn","2026-10-11","amount","400.00","paymentMethod","PIX"),201);
        JsonNode fa=accumulated(f,a).path("financialSummary"),fb=accumulated(f,b).path("financialSummary");
        assertEquals("700.00",fa.path("billedAmount").asText());
        assertEquals("400.00",fa.path("receivedAmount").asText());
        assertEquals("600.00",fa.path("remainingToReceive").asText());
        assertEquals("300.00",fa.path("billedOutstandingAmount").asText());
        assertEquals("0.00",fb.path("receivedAmount").asText());
        // A traced allocation may not consume this accepted origin again or omit its origin above the zero base.
        call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
            "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","1.00"))),400);
        String settlement=payment.at("/settlement/id").asText();
        call(f,post("/api/v1/company/receivables/"+receivable+"/settlements/"+settlement+"/reversal"),Map.of("reason","Correção de lançamento"),201);
        assertEquals("0.00",accumulated(f,a).at("/financialSummary/receivedAmount").asText());
        assertEquals("1000.00",accumulated(f,a).at("/financialSummary/remainingToReceive").asText());
    }

    @Test
    void rendersCompleteAccumulatedPdfSpreadsheetAndEquivalentPreviewWithUnavailableFinance() throws Exception {
        Fixture f=fixture(false);UUID mid=measurement(f,false),v1=version(f,mid),s=service(f,mid,"Linha de álcool",null,null);
        item(f,mid,v1,s,1,"Atividade já aceita","3.0000","25.00");accept(f,mid,v1);
        UUID v2=revision(f,mid);item(f,mid,v2,s,1,"Nova atividade","2.0000","30.00");
        byte[] pdf=download(f,mid,"pdf"),preview=download(f,mid,"spreadsheet/preview"),xlsx=download(f,mid,"spreadsheet");
        try(var document=Loader.loadPDF(pdf);var previewDoc=Loader.loadPDF(preview)) {
            String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("Linha de álcool"));assertTrue(text.contains("Atividade já aceita"));assertTrue(text.contains("Nova atividade"));
            assertTrue(text.contains("Aceito"));assertTrue(text.contains("Em revisão"));assertTrue(text.contains("Falta receber"));assertTrue(text.contains("Indisponível"));
            assertEquals(text.replaceAll("Posição UTC: \\S+","Posição UTC: snapshot"),
                    new PDFTextStripper().getText(previewDoc).replaceAll("Posição UTC: \\S+","Posição UTC: snapshot"));
        }
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            String text=workbook.getSheetAt(0).toString();
            assertNotNull(workbook.getSheetAt(0).getRow(0));
            long itemRows=0;for(var row:workbook.getSheetAt(0))if(row.getCell(13)!=null&&!row.getCell(13).toString().isBlank()&&!row.getCell(13).toString().equals("Item ID"))itemRows++;
            assertEquals(2,itemRows);
        }
        Path output=Path.of("output","pdf");Files.createDirectories(output);Files.write(output.resolve("medicao-acumulada-validacao.pdf"),pdf);
        Files.createDirectories(Path.of("tmp","sheets"));Files.write(Path.of("tmp","sheets","medicao-acumulada-validacao.xlsx"),xlsx);
    }

    @Test
    void isolatesTenantAndFinancialPermissionAndRequiresAnOptimisticDestinationVersion() throws Exception {
        Fixture f=fixture(false),other=fixture(true);UUID mid=measurement(f,true);
        UUID sid=service(f,mid,"Identidade persistida",null,null);
        call(other,get(base(mid)+"/accumulated"),null,404);
        call(other,get(base(mid)+"/services"),null,404);
        assertEquals("NOT_AUTHORIZED",accumulated(f,mid).at("/financialSummary/financialAvailability").asText());
        call(f,put(base(mid)+"/services/"+sid+"/destination"),Map.of("expectedVersion",99,"destinationAction","CREATE_NEW_SERVICE"),409);
        call(f,put(base(mid)+"/services/"+sid+"/destination"),Map.of("expectedVersion",0,"destinationAction","CREATE_NEW_SERVICE"),200);
        assertEquals("CREATE_NEW_SERVICE",call(f,get(base(mid)+"/services"),null,200).get(0).path("destinationAction").asText());
    }

    @Test
    void revalidatesServiceReductionOnApprovalWithoutAnticipatingAnyOfficialEffect() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Preço oficial",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Outro serviço",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("200.00")));
        Fixture requester=actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.REQUEST_UPDATE)));
        long billingVersion=jdbc.queryForObject("select billing_version from contract where id=? and company_id=?",Long.class,f.contract.getId(),f.company.getId());
        JsonNode proposal=call(requester,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+target.getId()),
                Map.of("name","Preço oficial","status","ACTIVE","quantity","1.0000","unitPrice","50.00"),202);
        assertTrue(proposal.path("service").isNull());
        assertEquals(billingVersion,jdbc.queryForObject("select billing_version from contract where id=? and company_id=?",Long.class,f.contract.getId(),f.company.getId()));
        assertOfficialService(f,target.getId(),"100.00","0.00","100.00");
        assertEquals("300.00",officialBilling(f).path("contractNetAmount").asText());
        call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","100.00"))),201);
        Fixture approver=actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.APPROVE)));
        call(approver,post("/api/v1/company/change-requests/"+proposal.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertOfficialService(f,target.getId(),"100.00","0.00","100.00");
        assertEquals("300.00",officialBilling(f).path("contractNetAmount").asText());
    }

    @Test
    void keepsAnExistingDestinationCommerciallyUnchangedUntilItsOwnServiceApproval() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Serviço existente",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Avanço",null,null);
        item(f,mid,vid,sid,1,"Acréscimo","4.0000","25.00");accept(f,mid,vid);
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT),
                AccessModule.SERVICES,List.of(AccessAction.READ,AccessAction.REQUEST_UPDATE)));
        call(requester,put(base(mid)+"/services/"+sid+"/destination"),Map.of("expectedVersion",0,
                "destinationAction","ADD_TO_EXISTING_SERVICE","contractServiceId",target.getId()),200);
        JsonNode states=call(f,get(base(mid)+"/contract-compositions"),null,200);
        String pending=states.get(0).path("changeRequestId").asText();
        assertEquals("AWAITING_APPROVAL",states.get(0).path("state").asText());
        assertOfficialService(f,target.getId(),"100.00","0.00","100.00");
        assertEquals("100.00",officialBilling(f).path("contractNetAmount").asText());
        call(requester,post(base(mid)+"/contract-compositions/apply"),Map.of(),200);
        assertEquals(pending,call(f,get(base(mid)+"/contract-compositions"),null,200).get(0).path("changeRequestId").asText());
        Fixture approver=actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.APPROVE)));
        call(approver,post("/api/v1/company/change-requests/"+pending+"/approve"),Map.of(),200);
        assertOfficialService(f,target.getId(),"100.00","100.00","200.00");
        assertEquals("200.00",officialBilling(f).path("contractNetAmount").asText());
        assertEquals("APPLIED",call(f,get(base(mid)+"/contract-compositions"),null,200).get(0).path("state").asText());
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void serializesServicePriceReductionAndBillingAgainstTheSameContractCapacity() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Preço concorrente",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Capacidade de outro serviço",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("200.00")));
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var price=pool.submit(()-> {start.await();return rawStatus(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+target.getId()),
                    Map.of("name","Preço concorrente","status","ACTIVE","quantity","1.0000","unitPrice","50.00"));});
            var bill=pool.submit(()-> {start.await();return rawStatus(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                    "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","100.00"))));});
            start.countDown();int p=price.get(30,TimeUnit.SECONDS),b=bill.get(30,TimeUnit.SECONDS);
            assertTrue((p==200&&b==409)||(p==409&&b==201),"price="+p+", billing="+b);
        }
        JsonNode control=officialBilling(f);JsonNode row=null;
        for(var service:control.path("services"))if(service.path("contractServiceId").asText().equals(target.getId().toString()))row=service;
        assertNotNull(row);
        assertTrue(new BigDecimal(row.path("activeAllocatedAmount").asText()).compareTo(new BigDecimal(row.path("serviceNetAmount").asText()))<=0);
    }

    @Test
    void cannotUseAcceptedCompositionToHideAUntracedBillingCommitmentAboveTheNegotiatedBase() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base e medição",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Acréscimo","ADD_TO_EXISTING_SERVICE",target.getId());
        item(f,mid,vid,sid,1,"Incremento aceito","4.0000","25.00");accept(f,mid,vid);
        call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","100.00"))),201);
        // Effective amount would still be 150.00: the total guard alone cannot protect the untraced base.
        call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+target.getId()),
                Map.of("name","Base e medição","status","ACTIVE","quantity","1.0000","unitPrice","50.00"),409);
        assertOfficialService(f,target.getId(),"100.00","100.00","200.00");
        assertEquals("100.00",officialBilling(f).path("activeAllocatedAmount").asText());
    }

    @Test
    void cannotReduceABilledServiceBelowItsOwnCommitmentEvenWhenTheContractHasSpareCapacity() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base faturada",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Outro serviço",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("200.00")));
        call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","100.00"))),201);
        call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+target.getId()),
                Map.of("name","Base faturada","status","ACTIVE","quantity","1.0000","unitPrice","50.00"),409);
        assertOfficialService(f,target.getId(),"100.00","0.00","100.00");
        call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+target.getId()+"/discount"),
                Map.of("discountType","FIXED","discountValue","50.00","justification","Revisão de preço"),409);
        assertOfficialService(f,target.getId(),"100.00","0.00","100.00");
    }

    @Test
    void cannotRemoveContractDiscountBackingWhileItsNegativeBillingAllocationIsActive() throws Exception {
        Fixture f=fixture(true);
        ContractService target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,
                ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        String contract="/api/v1/company/contracts/"+f.contract.getId();
        call(f,put(contract+"/discount"),Map.of("discountType","FIXED","discountValue","20.00","justification","Negociação"),200);
        JsonNode billing=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",target.getId(),"type","SERVICE","allocatedAmount","100.00"),
                        Map.of("type","CONTRACT_DISCOUNT","allocatedAmount","-20.00"))),201);
        call(f,post(contract+"/discount/reversal"),Map.of("justification","Desfazer negociação"),409);
        call(f,put(contract+"/discount"),Map.of("discountType","FIXED","discountValue","10.00","justification","Reduzir desconto"),409);
        assertEquals("20.00",officialBilling(f).path("contractDiscountAmount").asText());
        call(f,post("/api/v1/company/billing-installments/"+billing.at("/billingInstallment/id").asText()+"/cancellation"),
                Map.of("reason","Estorno antes de renegociar"),200);
        call(f,post(contract+"/discount/reversal"),Map.of("justification","Desconto desfeito após estorno"),200);
        assertEquals("0.00",officialBilling(f).path("contractDiscountAmount").asText());
    }

    @Test
    void persistsBothCommercialDestinationsAndTheCompleteDiscountInvoiceReceiptReversalChain() throws Exception {
        for(ContractPricingMode mode:ContractPricingMode.values()) {
            Fixture f=fixture(true,mode);
            ContractService existing=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base prevista",null,
                    ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
            UUID a=measurement(f,false),av=version(f,a),as=service(f,a,"Novo serviço","CREATE_NEW_SERVICE",null);
            item(f,a,av,as,1,"Avulso aceito","4.0000","25.00");accept(f,a,av);
            assertEquals("100.00",officialBilling(f).path("contractNetAmount").asText());
            call(f,put(base(a)+"/link"),Map.of("workId",f.work.getId(),"contractId",f.contract.getId()),200);
            UUID newTarget=continuity.findService(f.company.getId(),as).orElseThrow().contractServiceId();
            assertOfficialService(f,newTarget,"0.00","100.00","100.00");
            UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Complemento previsto","ADD_TO_EXISTING_SERVICE",existing.getId());
            item(f,b,bv,bs,1,"Complemento aceito","4.0000","25.00");accept(f,b,bv);
            assertOfficialService(f,existing.getId(),"100.00","100.00","200.00");
            call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/services/"+existing.getId()),
                    Map.of("name","Base prevista","status","CANCELLED","quantity","1.0000","unitPrice","100.00"),409);
            call(f,post(base(a)+"/contract-compositions/apply"),Map.of(),200);
            call(f,post(base(b)+"/contract-compositions/apply"),Map.of(),200);
            assertEquals("300.00",officialBilling(f).path("serviceSubtotal").asText());
            assertEquals(2,contractServices.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());
            var ca=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
            var cb=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
            assertEquals(mode==ContractPricingMode.FIXED?"CONTRACT_ADDITION":"MEASUREMENT_PRICING",ca.getOriginType().name());
            call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/discount"),
                    Map.of("discountType","FIXED","discountValue","60.00","justification","Desconto negociado"),200);
            JsonNode billing=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),
                    "issuedOn","2026-10-01","dueOn","2026-11-01","allocations",List.of(
                            Map.of("contractServiceId",existing.getId(),"type","SERVICE","allocatedAmount","100.00"),
                            Map.of("contractServiceId",existing.getId(),"measurementContractCompositionId",cb.getId(),"type","SERVICE","allocatedAmount","100.00"),
                            Map.of("contractServiceId",newTarget,"measurementContractCompositionId",ca.getId(),"type","SERVICE","allocatedAmount","100.00"),
                            Map.of("type","CONTRACT_DISCOUNT","allocatedAmount","-60.00"))),201);
            assertTrue(billing.at("/receivable/originalAmount").isString());
            assertEquals("240.00",billing.at("/receivable/originalAmount").asText());
            String allocation=null;
            for(JsonNode line:billing.at("/billingInstallment/allocations")) {
                assertTrue(line.path("allocatedAmount").isString());
                if(line.path("measurementContractCompositionId").asText().equals(ca.getId().toString()))allocation=line.path("id").asText();
            }
            assertNotNull(allocation);
            List<String> invoices=new ArrayList<>();
            for(String amount:List.of("40.00","60.00")) {
                JsonNode invoice=call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number",UUID.randomUUID().toString(),
                        "issuedOn","2026-10-13","items",List.of(Map.of("billingAllocationId",allocation,"allocatedAmount",amount))),201);
                assertTrue(invoice.at("/invoice/totalAmount").isString());
                assertEquals(allocation,invoice.at("/invoice/items/0/billingAllocationId").asText());
                invoices.add(invoice.at("/invoice/id").asText());
            }
            call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number",UUID.randomUUID().toString(),
                    "issuedOn","2026-10-13","items",List.of(Map.of("billingAllocationId",allocation,"allocatedAmount","0.01"))),409);
            assertEquals("240.00",officialBilling(f).path("activeAllocatedAmount").asText());
            assertEquals("0.00",officialBilling(f).path("availableAmount").asText());
            String receivable=billing.at("/receivable/id").asText();
            JsonNode payment=call(f,post("/api/v1/company/receivables/"+receivable+"/settlements"),
                    Map.of("receivedOn","2026-10-13","amount","120.00","paymentMethod","PIX"),201);
            assertTrue(payment.at("/settlement/amount").isString());
            assertEquals("120.00",payment.at("/balance/outstandingAmount").asText());
            for(UUID mid:List.of(a,b)) {
                var summary=accumulated(f,mid).path("financialSummary");
                assertEquals("100.00",summary.path("acceptedAmount").asText());
                assertEquals("40.00",summary.path("receivedAmount").asText());
                assertEquals("40.00",summary.path("remainingToReceive").asText());
            }
            String installment=billing.at("/billingInstallment/id").asText();
            call(f,post("/api/v1/company/billing-installments/"+installment+"/cancellation"),Map.of("reason","Não estornado"),409);
            call(f,post("/api/v1/company/receivables/"+receivable+"/settlements/"+payment.at("/settlement/id").asText()+"/reversal"),
                    Map.of("reason","Correção de recebimento"),201);
            for(UUID mid:List.of(a,b))assertEquals("0.00",accumulated(f,mid).at("/financialSummary/receivedAmount").asText());
            for(String invoice:invoices)call(f,post("/api/v1/company/invoices/"+invoice+"/cancellation"),Map.of("reason","Correção da nota"),200);
            call(f,post("/api/v1/company/billing-installments/"+installment+"/cancellation"),Map.of("reason","Correção da parcela"),200);
            assertEquals("240.00",officialBilling(f).path("availableAmount").asText());
            assertOfficialService(f,existing.getId(),"100.00","100.00","200.00");
            // F05-21: cancellation restores documentary gross capacity, but its uncommitted
            // contract discount remains in the official NET availability (100 - 20).
            assertEquals("100.00",netPosition(netCapacity(f),ca.getId()).path("grossAvailableAmount").asText());
            assertEquals("80.00",accumulated(f,a).at("/financialSummary/acceptedAvailableToBill").asText());
        }
    }

    @Test
    void preservesAllThreeCurrenciesFromCreationToCompositionBillingInvoiceTaxReceiptAndReversal()throws Exception {
        for(var currency:com.renovar.canteiro.io.shared.domain.CurrencyCode.values()) {
            Fixture original=fixture(true);
            var created=call(original,post("/api/v1/company/contracts"),Map.of("workId",original.work.getId(),"name","Contrato "+currency,
                    "status","ACTIVE","pricingMode","MEASUREMENT_BASED","currency",currency.name()),201);
            UUID cid=UUID.fromString(created.at("/contract/id").asText());assertEquals(currency.name(),created.at("/contract/currency").asText());
            Fixture f=new Fixture(original.company,original.user,original.work,contracts.findByIdAndCompanyId(cid,original.company.getId()).orElseThrow());
            var made=call(f,post("/api/v1/company/measurements"),Map.of("title","Medição "+currency,"workId",f.work.getId(),"contractId",cid),201);
            UUID mid=UUID.fromString(made.at("/measurement/id").asText()),vid=UUID.fromString(made.at("/version/id").asText());
            assertEquals(currency.name(),made.at("/measurement/currency").asText());
            String incompatible=currency.name().equals("USD")?"EUR":"USD";
            call(f,post("/api/v1/company/measurements"),Map.of("title","Incompatível","workId",f.work.getId(),"contractId",cid,"currency",incompatible),409);
            call(f,post("/api/v1/company/measurements"),Map.of("title","Inválida","currency","GBP"),400);
            UUID sid=service(f,mid,"Serviço contínuo","CREATE_NEW_SERVICE",null);
            UUID iid=item(f,mid,vid,sid,1,"Execução","4.0000","25.00");accept(f,mid,vid);
            assertEquals(currency.name(),call(f,get(base(mid)+"/versions"),null,200).get(0).path("currency").asText());
            call(f,put(base(mid)+"/versions/"+vid+"/items/"+iid),Map.of("itemNumber",1,"serviceTitle","Serviço contínuo","activity","Alterar aceito",
                    "chargeType","LINEAR_METER","linearMeters",4,"unitPrice","10.00","measuredOn","2026-10-13"),409);
            var composition=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
            var billing=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",cid,"dueOn","2026-11-01","allocations",List.of(
                    Map.of("contractServiceId",composition.getContractServiceId(),"measurementContractCompositionId",composition.getId(),"type","SERVICE","allocatedAmount","100.00"))),201);
            assertEquals(currency.name(),billing.at("/billingInstallment/currency").asText());assertEquals(currency.name(),billing.at("/receivable/currency").asText());
            String allocation=billing.at("/billingInstallment/allocations/0/id").asText(),rid=billing.at("/receivable/id").asText();
            var invoice=call(f,post("/api/v1/company/invoices"),Map.of("contractId",cid,"number",UUID.randomUUID().toString(),"issuedOn","2026-10-13",
                    "items",List.of(Map.of("billingAllocationId",allocation,"allocatedAmount","100.00"))),201);
            assertEquals(currency.name(),invoice.at("/invoice/currency").asText());
            var tax=call(f,post("/api/v1/company/invoices/"+invoice.at("/invoice/id").asText()+"/tax-obligations"),
                    Map.of("name","Imposto informativo","amount","0.01","treatment","INFORMATIONAL"),201);
            assertEquals(currency.name(),tax.at("/taxObligation/currency").asText());
            var receipt=call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-13","amount","25.00","paymentMethod","OTHER"),201);
            assertEquals(currency.name(),receipt.at("/settlement/currency").asText());assertEquals(currency.name(),receipt.at("/balance/currency").asText());
            var financial=accumulated(f,mid).path("financialSummary");assertEquals(currency.name(),financial.path("currency").asText());
            assertEquals("100.00",financial.path("acceptedAmount").asText());assertEquals("25.00",financial.path("receivedAmount").asText());assertEquals("75.00",financial.path("remainingToReceive").asText());
            call(f,post("/api/v1/company/receivables/"+rid+"/settlements/"+receipt.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Correção"),201);
            assertEquals("100.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
            call(f,put("/api/v1/company/contracts/"+cid),Map.of("name","Contrato alterado","status","ACTIVE","currency",incompatible),409);
            assertEquals(currency.name(),call(f,put("/api/v1/company/contracts/"+cid),
                    Map.of("name","Contrato atualizado","status","ACTIVE","currency",currency.name()),200).at("/contract/currency").asText());
            assertEquals(currency,contracts.findByIdAndCompanyId(cid,f.company.getId()).orElseThrow().getCurrency());
            var foreign=fixture(true);call(foreign,get(base(mid)),null,404);call(foreign,get("/api/v1/company/billing-control/contracts/"+cid),null,404);
            var denied=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ)));
            call(denied,post("/api/v1/company/measurements"),Map.of("title","Não autorizado","currency",currency.name()),403);
        }
    }

    @Test
    void validatesLaterLinkAndApprovalWithoutChangingOfficialCurrencyOrMixingContractListAmounts()throws Exception {
        Fixture f=fixture(true);
        var supported=call(f,get("/api/v1/company/currencies"),null,200);assertEquals(3,supported.size());assertEquals("BRL",supported.get(0).path("code").asText());
        var euro=contracts.save(Contract.create(f.company.getId(),f.work.getId(),null,ContractPricingMode.MEASUREMENT_BASED,null,"Contrato EUR",
                ContractStatus.ACTIVE,null,null,null,com.renovar.canteiro.io.shared.domain.CurrencyCode.EUR));
        UUID standalone=UUID.fromString(call(f,post("/api/v1/company/measurements"),Map.of("title","Avulsa EUR","currency","EUR"),201).at("/measurement/id").asText());
        call(f,put(base(standalone)+"/link"),Map.of("workId",f.work.getId(),"contractId",f.contract.getId()),409);
        call(f,put(base(standalone)+"/link"),Map.of("workId",f.work.getId(),"contractId",euro.getId()),200);
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.REQUEST_CREATE)));
        Integer before=jdbc.queryForObject("select count(*) from measurement where company_id=?",Integer.class,f.company.getId());
        var pending=call(requester,post("/api/v1/company/measurements"),Map.of("title","Aprovar EUR","workId",f.work.getId(),"contractId",euro.getId()),202);
        assertTrue(pending.path("measurement").isNull());assertEquals(before,jdbc.queryForObject("select count(*) from measurement where company_id=?",Integer.class,f.company.getId()));
        var request=changeRequests.findByIdAndCompanyId(UUID.fromString(pending.path("changeRequestId").asText()),f.company.getId()).orElseThrow();
        assertEquals("EUR",request.getSnapshot().proposedData().values().get("currency"));
        Fixture approver=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.APPROVE)));
        call(approver,post("/api/v1/company/change-requests/"+request.getId()+"/approve"),Map.of(),200);
        assertEquals("EUR",jdbc.queryForObject("select currency from measurement where company_id=? and title='Aprovar EUR'",String.class,f.company.getId()));
        Fixture contractRequester=actor(f,Map.of(AccessModule.CONTRACTS,List.of(AccessAction.REQUEST_CREATE)));
        var proposedContract=call(contractRequester,post("/api/v1/company/contracts"),Map.of("workId",f.work.getId(),"name","Aprovar USD","status","ACTIVE","currency","USD"),202);
        assertTrue(proposedContract.path("contract").isNull());
        assertEquals(0,jdbc.queryForObject("select count(*) from contract where company_id=? and name='Aprovar USD'",Integer.class,f.company.getId()));
        Fixture contractApprover=actor(f,Map.of(AccessModule.CONTRACTS,List.of(AccessAction.APPROVE)));
        call(contractApprover,post("/api/v1/company/change-requests/"+proposedContract.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals("USD",jdbc.queryForObject("select currency from contract where company_id=? and name='Aprovar USD'",String.class,f.company.getId()));
        var list=call(f,get("/api/v1/company/billing-control/contracts"),null,200).path("items");
        assertEquals(3,list.size());var codes=new java.util.HashSet<String>();for(var row:list)codes.add(row.path("currency").asText());
        assertEquals(java.util.Set.of("BRL","USD","EUR"),codes);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->
                jdbc.update("update contract set currency='USD' where id=?",euro.getId()));
        assertEquals("EUR",jdbc.queryForObject("select currency from contract where id=?",String.class,euro.getId()));
    }

    @Test
    void accumulatedDownloadsLoadOnlyThePrivateEmitterLogoAndFallbackWhenItIsInvalid()throws Exception {
        Fixture f=fixture(false),other=fixture(false);UUID mid=measurement(f,false);
        f.company.update(null,null,"22333444000155",null,null,null,null);companies.save(f.company);
        other.company.update(null,null,"33444555000166",null,null,null,null);companies.save(other.company);
        for(Fixture tenant:List.of(f,other)) {
            var image=new java.awt.image.BufferedImage(160,40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphic=image.createGraphics();graphic.setColor(tenant==f?java.awt.Color.GREEN:java.awt.Color.RED);graphic.fillRect(0,0,160,40);graphic.dispose();
            var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
            String key="companies/"+tenant.company.getId()+"/logo-test.png";storage.store(key,bytes.toByteArray());tenant.company.updateLogo(key);companies.save(tenant.company);
        }
        try(var pdf=Loader.loadPDF(download(f,mid,"pdf"))) {
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains(f.company.getCorporateName()));assertTrue(text.contains("CNPJ: 22.333.444/0001-55"));
            assertFalse(text.contains(other.company.getCorporateName()));assertFalse(text.contains("33.444.555/0001-66"));
            boolean found=false;for(var name:pdf.getPage(0).getResources().getXObjectNames()) {
                var object=pdf.getPage(0).getResources().getXObject(name);
                if(object instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject logo){found=true;assertEquals(java.awt.Color.GREEN.getRGB(),logo.getImage().getRGB(20,20));assertEquals(4,logo.getWidth()/logo.getHeight());}
            }assertTrue(found);
        }
        try(var xlsx=WorkbookFactory.create(new ByteArrayInputStream(download(f,mid,"spreadsheet")))) {
            assertEquals(1,xlsx.getAllPictures().size());var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(xlsx.getAllPictures().getFirst().getData()));
            assertEquals(java.awt.Color.GREEN.getRGB(),image.getRGB(20,20));assertEquals(4,image.getWidth()/image.getHeight());
        }
        storage.store("companies/"+f.company.getId()+"/logo-test.png",new byte[]{1,2,3});
        try(var pdf=Loader.loadPDF(download(f,mid,"pdf"))){assertFalse(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());assertTrue(new PDFTextStripper().getText(pdf).contains("Emitente"));}
        call(other,get(base(mid)+"/accumulated"),null,404);
    }

    @Test
    void billableOriginsSeparateAdvancesOnTheSameContractServiceAndKeepReadOnlyCanonicalCapacity() throws Exception {
        Fixture f=fixture(true);UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"Mesmo título","CREATE_NEW_SERVICE",null);
        item(f,a,av,as,1,"Origem A","4.0000","25.00");accept(f,a,av);
        var first=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Mesmo título","ADD_TO_EXISTING_SERVICE",first.getContractServiceId());
        item(f,b,bv,bs,1,"Origem B","2.0000","25.00");accept(f,b,bv);
        UUID av2=revision(f,a);item(f,a,av2,as,1,"Avanço A","1.0000","25.00");accept(f,a,av2);
        JsonNode bill=bill(f,first,"40.00");
        String path=originsPath(f);
        long auditBefore=jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId());
        JsonNode before=officialBilling(f),list=call(f,get(path),null,200);
        assertEquals(3,list.path("totalItems").asInt());
        JsonNode origin=null;
        for(JsonNode row:list.path("items")) {
            assertEquals(first.getContractServiceId().toString(),row.path("contractServiceId").asText());
            assertEquals("AVAILABLE",row.path("status").asText());assertEquals("BRL",row.path("currency").asText());
            assertTrue(row.path("availableToBillAmount").isString());assertTrue(row.path("acceptedAmount").isString());
            if(first.getId().toString().equals(row.path("measurementContractCompositionId").asText()))origin=row;
        }
        assertNotNull(origin);assertEquals("100.00",origin.path("acceptedAmount").asText());
        assertEquals("40.00",origin.path("activeAllocatedAmount").asText());assertEquals("60.00",origin.path("availableToBillAmount").asText());
        assertEquals(av.toString(),origin.path("measurementVersionId").asText());assertEquals(as.toString(),origin.path("continuousServiceId").asText());
        assertEquals(before,officialBilling(f));
        assertEquals(auditBefore,jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId()));
        assertEquals(2,call(f,get(path).param("measurementId",a.toString()),null,200).path("totalItems").asInt());
        Fixture requester=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.READ,AccessAction.REQUEST_CREATE),
                AccessModule.MEASUREMENTS,List.of(AccessAction.READ)));
        JsonNode proposal=call(requester,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",first.getContractServiceId(),"measurementContractCompositionId",first.getId(),"type","SERVICE","allocatedAmount","10.00"))),202);
        assertFalse(proposal.path("changeRequestId").asText().isBlank());
        assertEquals(list,call(f,get(path),null,200));
        bill(f,first,"60.00");assertEquals(2,call(f,get(path),null,200).path("totalItems").asInt());
        call(f,post("/api/v1/company/billing-installments/"+bill.at("/billingInstallment/id").asText()+"/cancellation"),Map.of("reason","Liberar somente origem cancelada"),200);
        JsonNode restored=call(f,get(path).param("measurementId",a.toString()),null,200);
        for(JsonNode row:restored.path("items"))if(first.getId().toString().equals(row.path("measurementContractCompositionId").asText())) {
            assertEquals("60.00",row.path("activeAllocatedAmount").asText());assertEquals("40.00",row.path("availableToBillAmount").asText());
        }
    }

    @Test
    void billableOriginsRequireBothPermissionsAndNeverExposeForeignTenantsOrFilters() throws Exception {
        Fixture f=fixture(true),other=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Privado","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Origem","1.0000","25.00");accept(f,mid,vid);
        String path=originsPath(f);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(var grants:List.of(Map.of(AccessModule.BILLING,List.of(AccessAction.READ)),Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ)))) {
            JsonNode denied=call(actor(f,grants),get(path),null,403);
            assertFalse(denied.path("correlationId").asText().isBlank());
        }
        call(other,get(path),null,404);call(f,get(originsPath(other)),null,404);
        call(f,get("/api/v1/company/billing-control/contracts/"+UUID.randomUUID()+"/measurement-origins"),null,404);
        assertEquals(0,call(f,get(path).param("measurementId",UUID.randomUUID().toString()),null,200).path("totalItems").asInt());
        assertEquals(0,call(f,get(path).param("contractServiceId",UUID.randomUUID().toString()),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(path).param("companyId",other.company.getId().toString()),null,200).path("totalItems").asInt());
        assertEquals(0,call(other,get(originsPath(other)).param("measurementId",mid.toString()),null,200).path("totalItems").asInt());
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        assertEquals(1,call(f,get(path).param("contractServiceId",origin.getContractServiceId().toString()),null,200).path("totalItems").asInt());
    }

    @Test
    void billableOriginsPageAndSortDeterministicallyAndRejectInvalidParameters() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid);
        for(int index=1;index<=3;index++) {
            UUID sid=service(f,mid,"Mesmo título","CREATE_NEW_SERVICE",null);
            item(f,mid,vid,sid,index,"Origem "+index,"1.0000","25.00");
        }
        accept(f,mid,vid);
        String path=originsPath(f);
        JsonNode all=call(f,get(path),null,200);
        assertEquals(3,all.path("totalItems").asInt());assertEquals(20,all.path("size").asInt());
        for(int page=0;page<3;page++) {
            JsonNode result=call(f,get(path).param("size","1").param("page",Integer.toString(page)),null,200);
            assertEquals(3,result.path("totalPages").asInt());assertEquals(all.path("items").get(page),result.at("/items/0"));
        }
        assertEquals(0,call(f,get(path).param("size","1").param("page","3"),null,200).path("items").size());
        JsonNode asc=call(f,get(path).param("sort","createdAt,asc"),null,200);
        for(int index=0;index<3;index++)assertEquals(all.path("items").get(index),asc.path("items").get(2-index));
        for(String[] invalid:new String[][]{{"page","-1"},{"size","0"},{"size","101"},{"page","2147483647"},
                {"sort","amount,asc"},{"sort","createdAt,invalid"},{"sort","createdAt"},{"measurementId","invalid"}}) {
            JsonNode error=call(f,get(path).param(invalid[0],invalid[1]),null,400);
            assertFalse(error.path("correlationId").asText().isBlank());
        }
    }

    @Test
    void billableOriginsExcludeDraftSentPendingRejectedAndCompositionApprovalUntilOfficial() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Pendente","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Origem","4.0000","25.00");
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        workflow(f,mid,vid,"SEND",null);
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        workflow(f,mid,vid,"AWAIT_EXTERNAL_ACCEPTANCE",null);
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        call(f,post(base(mid)+"/versions/"+vid+"/workflow"),Map.of("action","RECORD_EXTERNAL_ACCEPTANCE","externallyAccepted",false,
                "externalAcceptanceOn","2026-10-11","externalAcceptanceNotes","Conferir execução"),200);
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        UUID revision=revision(f,mid);
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT),
                AccessModule.SERVICES,List.of(AccessAction.REQUEST_CREATE)));
        accept(requester,mid,revision);
        String pending=call(f,get(base(mid)+"/contract-compositions"),null,200).get(0).path("changeRequestId").asText();
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        call(actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.APPROVE))),post("/api/v1/company/change-requests/"+pending+"/approve"),Map.of(),200);
        assertEquals(1,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
    }

    @Test
    void billableOriginsExcludeUnreconciledReductionsAndReplacedOriginsButKeepTheNewAcceptedSource() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Correção","CREATE_NEW_SERVICE",null);
        UUID item=item(f,mid,vid,sid,1,"Origem","4.0000","25.00");accept(f,mid,vid);
        var original=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        JsonNode correction=call(f,post(base(mid)+"/versions/"+vid+"/items/"+item+"/corrective-revision"),
                Map.of("action","REPLACE","itemNumber",1,"serviceTitle","Correção","activity","Corrigida","measuredOn","2026-10-11",
                        "chargeType","LINEAR_METER","linearMeters",2,"unitPrice","25.00","justification","Metragem conferida"),201);
        UUID cv=UUID.fromString(correction.at("/version/id").asText());
        assertEquals(1,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        Fixture requester=actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT),
                AccessModule.SERVICES,List.of(AccessAction.REQUEST_UPDATE)));
        accept(requester,mid,cv);
        assertTrue(continuity.hasUnreconciledReduction(f.company.getId(),original.getId()));
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        String pending=null;for(JsonNode row:call(f,get(base(mid)+"/contract-compositions"),null,200))
            if(row.path("state").asText().equals("AWAITING_APPROVAL"))pending=row.path("changeRequestId").asText();
        assertNotNull(pending);
        call(actor(f,Map.of(AccessModule.SERVICES,List.of(AccessAction.APPROVE))),post("/api/v1/company/change-requests/"+pending+"/approve"),Map.of(),200);
        JsonNode selected=call(f,get(originsPath(f)),null,200);
        assertEquals(1,selected.path("totalItems").asInt());assertEquals("50.00",selected.at("/items/0/availableToBillAmount").asText());
        assertFalse(original.getId().toString().equals(selected.at("/items/0/measurementContractCompositionId").asText()));
        assertEquals(cv.toString(),selected.at("/items/0/measurementVersionId").asText());
        assertTrue(continuity.isReversed(f.company.getId(),original.getId()));
    }

    @Test
    void billableOriginsExportTheExecutableContractWithoutEnablingThePaymentGateway() throws Exception {
        String specification=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();
        JsonNode document=json.readTree(specification);
        assertEquals("3.1.0",document.path("openapi").asText());
        assertTrue(document.path("paths").has("/api/v1/company/billing-control/contracts/{contractId}/measurement-origins"));
        assertTrue(document.path("paths").has("/api/v1/company/billing-control/contracts/{contractId}/net-capacity"));
        assertTrue(document.path("paths").has("/api/v1/company/billing-installments/net"));
        assertFalse(document.path("paths").has("/api/v1/webhooks/payments"));
        JsonNode schema=document.at("/components/schemas/BillableMeasurementOriginResponse");
        assertTrue(schema.path("required").toString().contains("measurementContractCompositionId"));
        String export=System.getenv("BILLABLE_ORIGINS_OPENAPI_EXPORT_PATH");
        if(export!=null&&!export.isBlank()) {
            Path target=Path.of(export).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());Files.writeString(target,specification);
        }
    }

    @Test
    void billingInstallmentReadsPreserveDiscountsAndDistinctOriginsOnTheSameServiceWithoutChangingOfficialData() throws Exception {
        Fixture f=fixture(true);UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"Execução","CREATE_NEW_SERVICE",null);
        item(f,a,av,as,1,"Origem A","4.0000","25.00");accept(f,a,av);
        var ca=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Execução","ADD_TO_EXISTING_SERVICE",ca.getContractServiceId());
        item(f,b,bv,bs,1,"Origem B","2.0000","25.00");accept(f,b,bv);
        var cb=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
        call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/discount"),Map.of("discountType","FIXED","discountValue","10.00","justification","Desconto negociado"),200);
        JsonNode created=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"reference","PARC-01",
                "description","Primeira parcela oficial","issuedOn","2026-10-11","dueOn","2026-11-01","allocations",List.of(
                Map.of("contractServiceId",ca.getContractServiceId(),"measurementContractCompositionId",ca.getId(),"type","SERVICE","allocatedAmount","100.00"),
                Map.of("contractServiceId",cb.getContractServiceId(),"measurementContractCompositionId",cb.getId(),"type","SERVICE","allocatedAmount","50.00"),
                Map.of("type","CONTRACT_DISCOUNT","allocatedAmount","-10.00"))),201);
        String id=created.at("/billingInstallment/id").asText();
        long events=jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId());
        JsonNode billing=officialBilling(f),list=call(f,get("/api/v1/company/billing-installments"),null,200),detail=call(f,get("/api/v1/company/billing-installments/"+id),null,200);
        assertEquals(1,list.path("totalItems").asInt());assertEquals(id,list.at("/items/0/id").asText());
        assertEquals("140.00",list.at("/items/0/netAmount").asText());assertEquals("140.00",detail.path("netAmount").asText());
        assertTrue(detail.path("netAmount").isString());assertEquals("BRL",detail.path("currency").asText());
        assertEquals("PARC-01",detail.path("reference").asText());assertEquals("Primeira parcela oficial",detail.path("description").asText());
        assertEquals("2026-10-11",detail.path("issuedOn").asText());assertEquals("2026-11-01",detail.path("dueOn").asText());
        assertEquals("OPEN",detail.path("status").asText());assertEquals(3,detail.path("allocations").size());
        java.util.Set<String> origins=new java.util.HashSet<>();
        for(JsonNode line:detail.path("allocations")) {
            assertFalse(line.path("allocationId").asText().isBlank());assertTrue(line.path("allocatedAmount").isString());
            if(line.path("type").asText().equals("SERVICE")) {
                assertEquals(ca.getContractServiceId().toString(),line.path("contractServiceId").asText());
                assertFalse(line.path("contractServiceName").asText().isBlank());origins.add(line.path("measurementContractCompositionId").asText());
            } else {
                assertEquals("-10.00",line.path("allocatedAmount").asText());assertTrue(line.path("contractServiceId").isNull());
                assertTrue(line.path("measurementContractCompositionId").isNull());
            }
        }
        assertEquals(java.util.Set.of(ca.getId().toString(),cb.getId().toString()),origins);
        assertTrue(detail.path("receivableReferencesAvailable").asBoolean());
        assertEquals(created.at("/receivable/id").asText(),detail.at("/receivables/0/id").asText());
        assertEquals(java.util.Set.of("id","reference"),fieldNames(detail.at("/receivables/0")));
        assertEquals(events,jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId()));
        assertEquals(billing,officialBilling(f));
    }

    @Test
    void billingInstallmentReadsRequireBillingAndGateReceivableReferencesWithoutMeasurementPermission() throws Exception {
        Fixture f=fixture(true),other=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Origem","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        JsonNode created=bill(f,origin,"50.00");String base="/api/v1/company/billing-installments",detail=base+"/"+created.at("/billingInstallment/id").asText();
        Fixture billingOnly=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.READ)));
        JsonNode allowed=call(billingOnly,get(detail),null,200);call(billingOnly,get(base),null,200);
        assertFalse(allowed.path("receivableReferencesAvailable").asBoolean());assertEquals(0,allowed.path("receivables").size());
        assertEquals(origin.getId().toString(),allowed.at("/allocations/0/measurementContractCompositionId").asText());
        assertFalse(allowed.toString().contains(created.at("/receivable/id").asText()));
        Fixture denied=actor(f,Map.of(AccessModule.RECEIVABLES,List.of(AccessAction.READ)));
        for(String route:List.of(base,detail)) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            JsonNode error=call(denied,get(route),null,403);assertFalse(error.path("correlationId").asText().isBlank());
        }
        call(other,get(detail),null,404);call(f,get(base+"/"+UUID.randomUUID()),null,404);
        assertEquals(0,call(other,get(base).param("contractId",f.contract.getId().toString()),null,200).path("totalItems").asInt());
        JsonNode own=call(f,get(base).param("companyId",other.company.getId().toString()),null,200);
        assertEquals(1,own.path("totalItems").asInt());assertEquals(f.contract.getId().toString(),own.at("/items/0/contractId").asText());
    }

    @Test
    void billingInstallmentReadsKeepCancelledHistoryAndDoNotPublishApprovalProposals() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Origem","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        String base="/api/v1/company/billing-installments";
        Fixture requester=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.REQUEST_CREATE,AccessAction.READ)));
        JsonNode proposal=call(requester,post(base),Map.of("contractId",f.contract.getId(),"reference","Ainda pendente","dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",origin.getContractServiceId(),"measurementContractCompositionId",origin.getId(),"type","SERVICE","allocatedAmount","20.00"))),202);
        assertEquals(0,call(requester,get(base),null,200).path("totalItems").asInt());
        call(requester,get(base+"/"+proposal.path("changeRequestId").asText()),null,404);
        JsonNode created=bill(f,origin,"40.00");String id=created.at("/billingInstallment/id").asText();
        call(f,post(base+"/"+id+"/cancellation"),Map.of("reason","Cancelamento preservado"),200);
        JsonNode detail=call(f,get(base+"/"+id),null,200),list=call(f,get(base).param("status","CANCELLED"),null,200);
        assertEquals("CANCELLED",detail.path("status").asText());assertEquals("40.00",detail.path("netAmount").asText());
        assertEquals("CANCELLED",detail.at("/allocations/0/status").asText());assertEquals("40.00",detail.at("/allocations/0/allocatedAmount").asText());
        assertEquals(1,list.path("totalItems").asInt());assertEquals("40.00",list.at("/items/0/netAmount").asText());
        assertEquals(0,call(f,get(base).param("status","OPEN"),null,200).path("totalItems").asInt());
    }

    @Test
    void billingInstallmentReadsFilterLiteralReferencesDatesContractsAndPageWithControlledSort() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Origem","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();String base="/api/v1/company/billing-installments";
        for(int n=1;n<=3;n++)call(f,post(base),Map.of("contractId",f.contract.getId(),"reference",n==1?"AbC%_1":"PARC-"+n,
                "issuedOn","2026-10-11","dueOn","2026-11-0"+n,"allocations",List.of(Map.of("contractServiceId",origin.getContractServiceId(),
                "measurementContractCompositionId",origin.getId(),"type","SERVICE","allocatedAmount","10.00"))),201);
        JsonNode all=call(f,get(base),null,200);assertEquals(3,all.path("totalItems").asInt());
        for(int page=0;page<3;page++) {
            JsonNode result=call(f,get(base).param("page",Integer.toString(page)).param("size","1"),null,200);
            assertEquals(3,result.path("totalPages").asInt());assertEquals(all.path("items").get(page),result.at("/items/0"));
        }
        assertEquals(0,call(f,get(base).param("page","3").param("size","1"),null,200).path("items").size());
        assertEquals("2026-11-03",call(f,get(base).param("sort","dueOn,desc"),null,200).at("/items/0/dueOn").asText());
        assertEquals(1,call(f,get(base).param("reference","abc%_"),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(base).param("reference","%"),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(base).param("dueFrom","2026-11-02").param("dueTo","2026-11-02"),null,200).path("totalItems").asInt());
        assertEquals(3,call(f,get(base).param("contractId",f.contract.getId().toString()),null,200).path("totalItems").asInt());
        assertEquals(0,call(f,get(base).param("contractId",UUID.randomUUID().toString()),null,200).path("totalItems").asInt());
        for(String[] invalid:new String[][]{{"page","-1"},{"size","0"},{"size","101"},{"page","2147483647"},{"sort","netAmount,asc"},
                {"sort","dueOn,invalid"},{"sort","dueOn"},{"contractId","invalid"},{"dueFrom","2026-99-01"},{"status","INVALID"},{"reference","x".repeat(101)}}) {
            JsonNode error=call(f,get(base).param(invalid[0],invalid[1]),null,400);assertFalse(error.path("correlationId").asText().isBlank());
        }
        call(f,get(base).param("dueFrom","2026-11-03").param("dueTo","2026-11-01"),null,400);
    }

    @Test
    void billingInstallmentReadsKeepTheContractCurrencyWithoutConversion() throws Exception {
        Fixture f=fixture(true);
        Contract usd=contracts.save(Contract.create(f.company.getId(),f.work.getId(),null,ContractPricingMode.FIXED,null,"Contrato USD",
                ContractStatus.ACTIVE,null,null,null,com.renovar.canteiro.io.shared.domain.CurrencyCode.USD));
        var service=contractServices.save(ContractService.create(f.company.getId(),usd.getId(),"Base USD",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        JsonNode created=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",usd.getId(),"dueOn","2026-11-01",
                "allocations",List.of(Map.of("contractServiceId",service.getId(),"type","SERVICE","allocatedAmount","99.90"))),201);
        String id=created.at("/billingInstallment/id").asText();
        assertEquals("USD",call(f,get("/api/v1/company/billing-installments/"+id),null,200).path("currency").asText());
        JsonNode list=call(f,get("/api/v1/company/billing-installments"),null,200);
        assertEquals("USD",list.at("/items/0/currency").asText());assertEquals("99.90",list.at("/items/0/netAmount").asText());
    }

    @Test
    void netBillingDistributesDiscountAndPreservesOriginThroughInvoiceReceiptAndReversal() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Origem líquida","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        setNetDiscount(f,"FIXED","10.00");
        var before=accumulated(f,mid).path("financialSummary");
        assertEquals("100.00",before.path("acceptedAmount").asText());assertEquals("90.00",before.path("acceptedAvailableToBill").asText());
        assertEquals("90.00",before.path("remainingToReceive").asText());assertEquals("10.00",before.path("financialAdjustmentAmount").asText());
        JsonNode capacity=netCapacity(f);long auditCount=jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId());
        assertEquals(capacity,netCapacity(f));assertEquals(auditCount,jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId()));
        Map<String,Object> payload=netPayload(f,capacity,origin.getContractServiceId(),origin.getId(),"90.00");
        JsonNode created=call(f,post("/api/v1/company/billing-installments/net"),payload,201);
        String allocation=created.at("/billingInstallment/allocations/0/id").asText(),rid=created.at("/receivable/id").asText();
        assertEquals(1,created.at("/billingInstallment/allocations").size());assertEquals("SERVICE",created.at("/billingInstallment/allocations/0/type").asText());
        assertEquals("90.00",created.at("/receivable/originalAmount").asText());
        assertEquals("100.00",created.at("/billingInstallment/allocations/0/netBillingGrossAmount").asText());
        assertEquals("10.00",created.at("/billingInstallment/allocations/0/netBillingDiscountAmount").asText());
        var replay=call(f,post("/api/v1/company/billing-installments/net"),payload,200);
        assertEquals(created,replay);assertEquals("0.00",netCapacity(f).path("remainingNetAmount").asText());
        assertEquals("0.00",officialBilling(f).at("/services/0/availableAmount").asText());
        assertEquals("0.00",officialBilling(f).at("/services/0/netAvailableToBillAmount").asText());
        assertEquals(0,call(f,get(originsPath(f)),null,200).path("totalItems").asInt());
        var detail=call(f,get("/api/v1/company/billing-installments/"+created.at("/billingInstallment/id").asText()),null,200);
        assertEquals("100.00",detail.at("/allocations/0/netBillingGrossAmount").asText());
        var invoice=call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number",UUID.randomUUID().toString(),"issuedOn","2026-10-13",
                "items",List.of(Map.of("billingAllocationId",allocation,"allocatedAmount","90.00"))),201);
        call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number",UUID.randomUUID().toString(),"issuedOn","2026-10-13",
                "items",List.of(Map.of("billingAllocationId",allocation,"allocatedAmount","0.01"))),409);
        var receipt=call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-13","amount","45.00","paymentMethod","PIX"),201);
        assertEquals("45.00",accumulated(f,mid).at("/financialSummary/receivedAmount").asText());
        assertEquals("45.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-13","amount","45.00","paymentMethod","PIX"),201);
        assertEquals("0.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        call(f,post("/api/v1/company/receivables/"+rid+"/settlements/"+receipt.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Estorno parcial"),201);
        assertEquals("45.00",accumulated(f,mid).at("/financialSummary/remainingToReceive").asText());
        assertEquals("0.00",netCapacity(f).path("remainingNetAmount").asText());
        assertOfficialService(f,origin.getContractServiceId(),"0.00","100.00","100.00");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update billing_allocation set net_billing_gross_amount=101 where id=?",UUID.fromString(allocation)));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from billing_allocation where id=?",UUID.fromString(allocation)));
        assertFalse(invoice.at("/invoice/id").asText().isBlank());
    }

    @Test
    void netBillingKeepsTwoOriginsOnOneServiceSeparateAndRedistributesOnlyNewUncommittedCapacity() throws Exception {
        Fixture f=fixture(true);UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"Mesmo título","CREATE_NEW_SERVICE",null);
        item(f,a,av,as,1,"A","1.0000","100.00");accept(f,a,av);var ca=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Mesmo título","ADD_TO_EXISTING_SERVICE",ca.getContractServiceId());
        item(f,b,bv,bs,1,"B","1.0000","100.00");accept(f,b,bv);var cb=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
        setNetDiscount(f,"FIXED","20.00");
        JsonNode quote=netCapacity(f);
        assertEquals("90.00",netPosition(quote,ca.getId()).path("netAvailableAmount").asText());
        assertEquals("90.00",netPosition(quote,cb.getId()).path("netAvailableAmount").asText());
        var first=call(f,post("/api/v1/company/billing-installments/net"),netPayload(f,quote,ca.getContractServiceId(),ca.getId(),"90.00"),201);
        call(f,post("/api/v1/company/receivables/"+first.at("/receivable/id").asText()+"/settlements"),Map.of("receivedOn","2026-10-13","amount","90.00","paymentMethod","PIX"),201);
        assertEquals("90.00",accumulated(f,a).at("/financialSummary/receivedAmount").asText());
        assertEquals("0.00",accumulated(f,b).at("/financialSummary/receivedAmount").asText());
        UUID next=revision(f,b);item(f,b,next,bs,1,"Novo avanço","1.0000","100.00");accept(f,b,next);
        JsonNode after=netCapacity(f);assertEquals("190.00",after.path("remainingNetAmount").asText());
        assertEquals("10.00",after.path("remainingDiscountAmount").asText());
        assertEquals("95.00",netPosition(after,cb.getId()).path("netAvailableAmount").asText());
        var saved=allocations.findByIdAndCompanyId(UUID.fromString(first.at("/billingInstallment/allocations/0/id").asText()),f.company.getId()).orElseThrow();
        assertEquals(new BigDecimal("10.00"),saved.getNetBillingDiscountAmount());assertEquals(new BigDecimal("100.00"),saved.getCapacityAmount());
        assertEquals("0.00",accumulated(f,a).at("/financialSummary/remainingToReceive").asText());
        assertEquals("190.00",accumulated(f,b).at("/financialSummary/remainingToReceive").asText());
    }

    @Test
    void netBillingPreservesLegacyNegativeRowsAndClosesPartialBasisCentsAndCancellation() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);
        var one=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Um",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        var two=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Dois",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        setNetDiscount(f,"PERCENTAGE","10.00");
        var legacy=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01","allocations",List.of(
                Map.of("contractServiceId",one.getId(),"type","SERVICE","allocatedAmount","100.00"),Map.of("type","CONTRACT_DISCOUNT","allocatedAmount","-10.00"))),201);
        var first=call(f,post("/api/v1/company/billing-installments/net"),netPayload(f,netCapacity(f),two.getId(),null,"44.99"),201);
        assertEquals("49.98",first.at("/billingInstallment/allocations/0/netBillingGrossAmount").asText());
        var last=call(f,post("/api/v1/company/billing-installments/net"),netPayload(f,netCapacity(f),two.getId(),null,"45.01"),201);
        assertEquals("50.02",last.at("/billingInstallment/allocations/0/netBillingGrossAmount").asText());
        assertEquals("0.00",netCapacity(f).path("remainingNetAmount").asText());
        String contract="/api/v1/company/contracts/"+f.contract.getId();
        call(f,put(contract+"/discount"),Map.of("discountType","FIXED","discountValue","10.00","justification","Não pode retirar lastro"),409);
        call(f,put(contract+"/services/"+two.getId()),Map.of("name","Dois","status","ACTIVE","quantity","1.0000","unitPrice","95.00"),409);
        var historical=call(f,get("/api/v1/company/billing-installments/"+legacy.at("/billingInstallment/id").asText()),null,200);
        assertEquals("90.00",historical.path("netAmount").asText());
        for(var row:historical.path("allocations"))assertTrue(row.path("netBillingGrossAmount").isNull());
        call(f,post("/api/v1/company/billing-installments/"+last.at("/billingInstallment/id").asText()+"/cancellation"),Map.of("reason","Cancelar preservando base"),200);
        assertEquals("45.01",netCapacity(f).path("remainingNetAmount").asText());
        assertEquals("5.01",netCapacity(f).path("remainingDiscountAmount").asText());
    }

    @Test
    void netBillingApprovalReservesNothingIsIdempotentAndRejectsStaleCapacity() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);
        var service=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        setNetDiscount(f,"FIXED","10.00");
        Fixture requester=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.REQUEST_CREATE)));
        Fixture approver=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.APPROVE)));
        var capacity=netCapacity(f);var payload=netPayload(f,capacity,service.getId(),null,"45.00");
        var pending=call(requester,post("/api/v1/company/billing-installments/net"),payload,202);
        assertEquals(pending,call(requester,post("/api/v1/company/billing-installments/net"),payload,202));
        assertTrue(pending.path("billingInstallment").isNull());assertTrue(pending.path("receivable").isNull());
        assertEquals(capacity,netCapacity(f));assertEquals(0,allocations.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals("45.00",netCapacity(f).path("remainingNetAmount").asText());
        call(requester,post("/api/v1/company/billing-installments/net"),payload,200);
        var stale=netPayload(f,capacity,service.getId(),null,"1.00");call(f,post("/api/v1/company/billing-installments/net"),stale,409);
        var second=call(requester,post("/api/v1/company/billing-installments/net"),netPayload(f,netCapacity(f),service.getId(),null,"10.00"),202);
        call(f,post("/api/v1/company/billing-installments/net"),netPayload(f,netCapacity(f),service.getId(),null,"5.00"),201);
        call(approver,post("/api/v1/company/change-requests/"+second.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertEquals("PENDING",changeRequests.findByIdAndCompanyId(UUID.fromString(second.path("changeRequestId").asText()),f.company.getId()).orElseThrow().getStatus().name());
        assertEquals(2,allocations.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());
    }

    @Test
    void netBillingEnforcesAuthorizationTenantExactOriginsAndStrictPositiveDecimalRequests() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED),other=fixture(true);
        var service=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        Fixture denied=actor(f,Map.of());var quote=netCapacity(f);var payload=netPayload(f,quote,service.getId(),null,"10.00");
        String route="/api/v1/company/billing-installments/net",capacity=netPath(f);
        mvc.perform(get(capacity)).andExpect(status().isUnauthorized());
        call(denied,get(capacity),null,403);call(denied,post(route),payload,403);call(other,get(capacity),null,404);call(other,post(route),payload,404);
        call(actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.READ))),get(capacity),null,403);
        call(actor(f,Map.of(AccessModule.MEASUREMENTS,List.of(AccessAction.READ))),get(capacity),null,403);
        call(f,get(capacity).param("size","101"),null,400);call(f,get(capacity).param("page","-1"),null,400);
        assertEquals(0,call(f,get(capacity).param("contractServiceId",UUID.randomUUID().toString()),null,200).at("/positions/totalItems").asInt());
        call(f,post(route),netPayload(f,quote,service.getId(),UUID.randomUUID(),"10.00"),404);
        for(Object amount:List.of(10,new BigDecimal("10.00"),"-1.00","0.00","10","1.001","1e1")) {
            Map<String,Object> invalid=new HashMap<>(payload);invalid.put("operationId",UUID.randomUUID());invalid.put("allocations",List.of(Map.of("contractServiceId",service.getId(),"netAmount",amount)));
            var error=call(f,post(route),invalid,400);assertFalse(error.path("correlationId").asText().isBlank());
        }
        Map<String,Object> duplicate=new HashMap<>(payload);duplicate.put("allocations",List.of(Map.of("contractServiceId",service.getId(),"netAmount","1.00"),Map.of("contractServiceId",service.getId(),"netAmount","1.00")));
        call(f,post(route),duplicate,400);
        Map<String,Object> tooLong=new HashMap<>(payload);tooLong.put("description","x".repeat(1001));call(f,post(route),tooLong,400);
        call(f,post(route),payload,201);Map<String,Object> changed=new HashMap<>(payload);changed.put("reference","Outra proposta");call(f,post(route),changed,409);
        Map<String,Object> escaped=new HashMap<>(netPayload(f,netCapacity(f),service.getId(),null,"1.00"));
        escaped.put("reference","a, description=b");escaped.put("description","c");call(f,post(route),escaped,201);
        Map<String,Object> ambiguous=new HashMap<>(escaped);ambiguous.put("reference","a");ambiguous.put("description","b, description=c");
        call(f,post(route),ambiguous,409);
    }

    @Test
    void netBillingSerializesConcurrentOriginsAndReplaysTheSameOperationOnlyOnce() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);
        var service=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        setNetDiscount(f,"FIXED","10.00");var quote=netCapacity(f);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate=new CountDownLatch(1);
            var p1=netPayload(f,quote,service.getId(),null,"60.00");var p2=netPayload(f,quote,service.getId(),null,"60.00");
            var a=executor.submit(()->{gate.await();return rawStatus(f,post("/api/v1/company/billing-installments/net"),p1);});
            var b=executor.submit(()->{gate.await();return rawStatus(f,post("/api/v1/company/billing-installments/net"),p2);});
            gate.countDown();List<Integer> codes=new ArrayList<>(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS)));codes.sort(Integer::compare);assertEquals(List.of(201,409),codes);
            var current=netCapacity(f);var same=netPayload(f,current,service.getId(),null,"30.00");CountDownLatch again=new CountDownLatch(1);
            var c=executor.submit(()->{again.await();return rawStatus(f,post("/api/v1/company/billing-installments/net"),same);});
            var d=executor.submit(()->{again.await();return rawStatus(f,post("/api/v1/company/billing-installments/net"),same);});
            again.countDown();codes=new ArrayList<>(List.of(c.get(60,TimeUnit.SECONDS),d.get(60,TimeUnit.SECONDS)));codes.sort(Integer::compare);assertEquals(List.of(200,201),codes);
        }
        assertEquals(2,allocations.findByContractIdAndCompanyId(f.contract.getId(),f.company.getId()).size());assertEquals("0.00",netCapacity(f).path("remainingNetAmount").asText());
    }

    @Test
    void netBillingHandlesFullyDiscountedCapacityRejectedRequestsAndReportsOfficialNetAmounts() throws Exception {
        Fixture f=fixture(true);UUID mid=measurement(f,true),vid=version(f,mid),sid=service(f,mid,"Relatório líquido","CREATE_NEW_SERVICE",null);
        item(f,mid,vid,sid,1,"Execução","1.0000","100.00");accept(f,mid,vid);
        var origin=compositions.findByMeasurementIdAndCompanyId(mid,f.company.getId()).getFirst();
        setNetDiscount(f,"PERCENTAGE","100.00");var zero=netCapacity(f);
        assertEquals("0.00",netPosition(zero,origin.getId()).path("netAvailableAmount").asText());
        assertFalse(netPosition(zero,origin.getId()).path("eligible").asBoolean());
        call(f,post("/api/v1/company/billing-installments/net"),netPayload(f,zero,origin.getContractServiceId(),origin.getId(),"0.01"),409);
        setNetDiscount(f,"FIXED","10.00");
        Fixture requester=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.REQUEST_CREATE)));
        Fixture approver=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.APPROVE,AccessAction.REJECT)));
        Map<String,Object> payload=new HashMap<>(netPayload(f,netCapacity(f),origin.getContractServiceId(),origin.getId(),"90.00"));payload.put("justification","Revisão da cobrança");
        var proposal=call(requester,post("/api/v1/company/billing-installments/net"),payload,202);
        call(approver,post("/api/v1/company/change-requests/"+proposal.path("changeRequestId").asText()+"/reject"),Map.of("decisionReason","Aguardando conferência"),200);
        call(requester,post("/api/v1/company/billing-installments/net"),payload,409);
        payload.put("operationId",UUID.randomUUID());
        var pending=call(requester,post("/api/v1/company/billing-installments/net"),payload,202);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        var replay=call(requester,post("/api/v1/company/billing-installments/net"),payload,200);
        call(f,post("/api/v1/company/receivables/"+replay.at("/receivable/id").asText()+"/settlements"),Map.of("receivedOn","2026-10-13","amount","90.00","paymentMethod","PIX"),201);
        try(var pdf=Loader.loadPDF(download(f,mid,"pdf"))) {
            String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("Falta receber"));assertTrue(text.contains("R$ 0,00"));assertTrue(text.contains("R$ 90,00"));
        }
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(download(f,mid,"spreadsheet")))) {
            boolean remaining=false;
            for(var row:workbook.getSheetAt(0))if(row.getCell(0)!=null&&row.getCell(0).toString().equals("Falta receber")) {
                remaining=true;StringBuilder values=new StringBuilder();row.forEach(cell->values.append(cell.toString()));assertTrue(values.toString().contains("R$ 0,00"));
            }
            assertTrue(remaining);
        }
    }

    @Test
    void serviceNetBillingUsesBaseThenOfficialAcceptanceDateAndReplaysWithoutReResolving() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);
        var target=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Serviço contínuo",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("600.00")));
        UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"A","ADD_TO_EXISTING_SERVICE",target.getId());
        item(f,a,av,as,1,"A","1.0000","300.00");workflow(f,a,av,"SEND",null);workflow(f,a,av,"AWAIT_EXTERNAL_ACCEPTANCE",null);
        call(f,post(base(a)+"/versions/"+av+"/workflow"),Map.of("action","RECORD_EXTERNAL_ACCEPTANCE","externallyAccepted",true,"externalAcceptanceOn","2026-10-15"),200);
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"B","ADD_TO_EXISTING_SERVICE",target.getId());
        item(f,b,bv,bs,1,"B","1.0000","100.00");workflow(f,b,bv,"SEND",null);workflow(f,b,bv,"AWAIT_EXTERNAL_ACCEPTANCE",null);
        call(f,post(base(b)+"/versions/"+bv+"/workflow"),Map.of("action","RECORD_EXTERNAL_ACCEPTANCE","externallyAccepted",true,"externalAcceptanceOn","2026-10-14"),200);
        var earlier=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
        var capacity=call(f,get(netPath(f)+"/services").param("size","1"),null,200);
        assertTrue(capacity.path("coverageComplete").asBoolean());assertEquals(1,capacity.path("totalServices").asInt());
        assertEquals("1000.00",capacity.at("/services/items/0/availableNetAmount").asText());assertEquals(3,capacity.at("/services/items/0/originCount").asInt());
        var payload=serviceNetPayload(f,capacity,target.getId(),"PARTIAL","650.00");String route="/api/v1/company/billing-installments/net/by-service";
        var created=call(f,post(route),payload,201);var lines=created.at("/billingInstallment/allocations");assertEquals(2,lines.size());
        for(var line:lines) if(line.path("measurementContractCompositionId").isNull())assertEquals("600.00",line.path("allocatedAmount").asText());
        else {assertEquals(earlier.getId().toString(),line.path("measurementContractCompositionId").asText());assertEquals("50.00",line.path("allocatedAmount").asText());}
        assertEquals(created.at("/billingInstallment/id"),call(f,post(route),payload,200).at("/billingInstallment/id"));
        List<Map<String,Object>> invoiceLines=new ArrayList<>();for(var line:lines)invoiceLines.add(Map.of("billingAllocationId",line.path("id").asText(),"allocatedAmount",line.path("allocatedAmount").asText()));
        var invoice=call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number","NF-POR-SERVICO","issuedOn","2026-10-26","items",invoiceLines),201);
        assertEquals("650.00",invoice.at("/invoice/totalAmount").asText());
        String rid=created.at("/receivable/id").asText();var cash=call(f,post("/api/v1/company/receivables/"+rid+"/settlements"),Map.of("receivedOn","2026-10-26","amount","325.00","paymentMethod","PIX"),201);
        UUID cashId=UUID.fromString(cash.at("/settlement/id").asText());
        assertEquals(new BigDecimal("25.00"),jdbc.queryForObject("select sum(s.amount) from receivable_settlement_allocation s join billing_allocation a on a.id=s.billing_allocation_id and a.company_id=s.company_id where s.company_id=? and s.settlement_id=? and a.measurement_contract_composition_id=?",BigDecimal.class,f.company.getId(),cashId,earlier.getId()));
        var reverse=call(f,post("/api/v1/company/receivables/"+rid+"/settlements/"+cashId+"/reversal"),Map.of("reason","Conferência de origem"),201);
        assertEquals(new BigDecimal("-25.00"),jdbc.queryForObject("select sum(s.amount) from receivable_settlement_allocation s join billing_allocation a on a.id=s.billing_allocation_id and a.company_id=s.company_id where s.company_id=? and s.settlement_id=? and a.measurement_contract_composition_id=?",BigDecimal.class,f.company.getId(),UUID.fromString(reverse.path("reversalId").asText()),earlier.getId()));
        var current=call(f,get(netPath(f)+"/services"),null,200);assertEquals("350.00",current.at("/services/items/0/availableNetAmount").asText());
        var all=call(f,post(route),serviceNetPayload(f,current,target.getId(),"ALL_AVAILABLE",null),201);assertEquals("350.00",all.at("/receivable/originalAmount").asText());
        var changed=new HashMap<String,Object>(payload);changed.put("reference","Outra intenção");call(f,post(route),changed,409);
    }

    @Test
    void serviceNetBillingApprovalReservesNothingAndRejectsChangedCapacity() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);var service=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        Fixture requester=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.REQUEST_CREATE)));
        Fixture approver=actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.APPROVE)));
        String route="/api/v1/company/billing-installments/net/by-service";var quote=call(f,get(netPath(f)+"/services"),null,200);
        var body=serviceNetPayload(f,quote,service.getId(),"ALL_AVAILABLE",null);
        var pending=call(requester,post(route),body,202);assertTrue(pending.path("billingInstallment").isNull());
        assertEquals(pending,call(requester,post(route),body,202));assertEquals(quote.path("capacityVersion"),netCapacity(f).path("capacityVersion"));
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals("100.00",call(requester,post(route),body,200).at("/receivable/originalAmount").asText());
        Fixture another=fixture(true,ContractPricingMode.FIXED);var s=contractServices.save(ContractService.create(another.company.getId(),another.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        var req=actor(another,Map.of(AccessModule.BILLING,List.of(AccessAction.REQUEST_CREATE)));var apr=actor(another,Map.of(AccessModule.BILLING,List.of(AccessAction.APPROVE)));
        var p=call(req,post(route),serviceNetPayload(another,netCapacity(another),s.getId(),"ALL_AVAILABLE",null),202);
        call(another,post(route),serviceNetPayload(another,netCapacity(another),s.getId(),"PARTIAL","10.00"),201);
        call(apr,post("/api/v1/company/change-requests/"+p.path("changeRequestId").asText()+"/approve"),Map.of(),409);
    }

    @Test
    void serviceNetBillingPreservesDiscountBasisAndEnforcesTenantPermissionsAndModes() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);var service=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        setNetDiscount(f,"FIXED","10.00");var quote=call(f,get(netPath(f)+"/services"),null,200);
        String route="/api/v1/company/billing-installments/net/by-service";var body=serviceNetPayload(f,quote,service.getId(),"PARTIAL","45.00");
        var other=fixture(true);call(other,get(netPath(f)+"/services"),null,404);call(other,post(route),body,404);
        call(actor(f,Map.of(AccessModule.BILLING,List.of(AccessAction.READ))),get(netPath(f)+"/services"),null,403);
        call(f,get(netPath(f)+"/services").param("size","101"),null,400);
        call(f,post(route),serviceNetPayload(f,quote,service.getId(),"ALL_AVAILABLE","1.00"),400);
        call(f,post(route),serviceNetPayload(f,quote,service.getId(),"PARTIAL",null),400);
        var result=call(f,post(route),body,201);assertEquals("50.00",result.at("/billingInstallment/allocations/0/netBillingGrossAmount").asText());
        assertEquals("5.00",result.at("/billingInstallment/allocations/0/netBillingDiscountAmount").asText());
    }

    @Test
    void serviceNetBillingSerializesConcurrentPartialsAndWholeAvailableReplays() throws Exception {
        Fixture f=fixture(true,ContractPricingMode.FIXED);var s=contractServices.save(ContractService.create(f.company.getId(),f.contract.getId(),"Base",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        var quote=netCapacity(f);String route="/api/v1/company/billing-installments/net/by-service";
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new CountDownLatch(1);var p1=serviceNetPayload(f,quote,s.getId(),"PARTIAL","60.00");var p2=serviceNetPayload(f,quote,s.getId(),"PARTIAL","60.00");
            var a=pool.submit(()->{gate.await();return rawStatus(f,post(route),p1);});var b=pool.submit(()->{gate.await();return rawStatus(f,post(route),p2);});gate.countDown();
            var codes=new ArrayList<Integer>(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS)));codes.sort(Integer::compareTo);assertEquals(List.of(201,409),codes);
            var whole=serviceNetPayload(f,netCapacity(f),s.getId(),"ALL_AVAILABLE",null);var again=new CountDownLatch(1);
            var c=pool.submit(()->{again.await();return rawStatus(f,post(route),whole);});var d=pool.submit(()->{again.await();return rawStatus(f,post(route),whole);});again.countDown();
            codes=new ArrayList<>(List.of(c.get(60,TimeUnit.SECONDS),d.get(60,TimeUnit.SECONDS)));codes.sort(Integer::compareTo);assertEquals(List.of(200,201),codes);
        }
        assertEquals("0.00",call(f,get(netPath(f)+"/services"),null,200).at("/services/items/0/availableNetAmount").asText());
    }

    Map<String,Object> serviceNetPayload(Fixture f,JsonNode quote,UUID service,String mode,String amount) {
        Map<String,Object> line=new HashMap<>();line.put("contractServiceId",service);line.put("mode",mode);if(amount!=null)line.put("netAmount",amount);
        return Map.of("operationId",UUID.randomUUID(),"contractId",f.contract.getId(),"capacityVersion",quote.path("capacityVersion").asText(),"dueOn","2026-11-01","services",List.of(line));
    }

    String netPath(Fixture f) {return "/api/v1/company/billing-control/contracts/"+f.contract.getId()+"/net-capacity";}
    JsonNode netCapacity(Fixture f)throws Exception{return call(f,get(netPath(f)).param("size","100"),null,200);}
    JsonNode netPosition(JsonNode capacity,UUID origin){for(var p:capacity.at("/positions/items"))if(p.path("measurementContractCompositionId").asText().equals(origin.toString()))return p;throw new AssertionError("Origin not found");}
    void setNetDiscount(Fixture f,String type,String amount)throws Exception{call(f,put("/api/v1/company/contracts/"+f.contract.getId()+"/discount"),Map.of("discountType",type,"discountValue",amount,"justification","Ajuste contratual"),200);}
    Map<String,Object> netPayload(Fixture f,JsonNode capacity,UUID service,UUID origin,String amount) {
        Map<String,Object> line=new HashMap<>();line.put("contractServiceId",service);line.put("measurementContractCompositionId",origin);line.put("netAmount",amount);
        return Map.of("operationId",UUID.randomUUID(),"contractId",f.contract.getId(),"capacityVersion",capacity.path("capacityVersion").asText(),"dueOn","2026-11-01","allocations",List.of(line));
    }

    java.util.Set<String> fieldNames(JsonNode node) {
        java.util.Set<String> names=new java.util.HashSet<>();node.properties().forEach(entry->names.add(entry.getKey()));return names;
    }

    String originsPath(Fixture f) { return "/api/v1/company/billing-control/contracts/"+f.contract.getId()+"/measurement-origins"; }

    JsonNode officialBilling(Fixture f)throws Exception {
        return call(f,get("/api/v1/company/billing-control/contracts/"+f.contract.getId()),null,200);
    }

    void assertOfficialService(Fixture f,UUID id,String baseAmount,String composed,String effective)throws Exception {
        JsonNode list=call(f,get("/api/v1/company/contracts/"+f.contract.getId()+"/services"),null,200);
        JsonNode found=null;for(var row:list)if(row.path("id").asText().equals(id.toString()))found=row;
        assertNotNull(found);
        assertEquals(baseAmount,found.path("netAmount").asText());
        assertEquals(composed,found.path("measurementCompositionAmount").asText());
        assertEquals(effective,found.path("effectiveNetAmount").asText());
    }

    @Test
    void invoiceReadPreservesTwoExactMeasurementOriginsWithoutRequiringMeasurementContentPermission() throws Exception {
        Fixture f=fixture(true);UUID a=measurement(f,true),av=version(f,a),as=service(f,a,"Origem A","CREATE_NEW_SERVICE",null);
        item(f,a,av,as,1,"A","1.0000","100.00");accept(f,a,av);
        var ca=compositions.findByMeasurementIdAndCompanyId(a,f.company.getId()).getFirst();
        UUID b=measurement(f,true),bv=version(f,b),bs=service(f,b,"Origem B","ADD_TO_EXISTING_SERVICE",ca.getContractServiceId());
        item(f,b,bv,bs,1,"B","1.0000","100.00");accept(f,b,bv);
        var cb=compositions.findByMeasurementIdAndCompanyId(b,f.company.getId()).getFirst();
        var billing=call(f,post("/api/v1/company/billing-installments"),Map.of("contractId",f.contract.getId(),"dueOn","2026-11-01",
                "reference","Parcela de origens", "allocations",List.of(
                Map.of("contractServiceId",ca.getContractServiceId(),"measurementContractCompositionId",ca.getId(),"type","SERVICE","allocatedAmount","30.00"),
                Map.of("contractServiceId",cb.getContractServiceId(),"measurementContractCompositionId",cb.getId(),"type","SERVICE","allocatedAmount","40.00"))),201);
        var lines=billing.at("/billingInstallment/allocations");
        var created=call(f,post("/api/v1/company/invoices"),Map.of("contractId",f.contract.getId(),"number","NF-ORIGENS","issuedOn","2026-10-25",
                "items",List.of(Map.of("billingAllocationId",lines.get(0).path("id").asText(),"allocatedAmount",lines.get(0).path("allocatedAmount").asText()),
                Map.of("billingAllocationId",lines.get(1).path("id").asText(),"allocatedAmount",lines.get(1).path("allocatedAmount").asText()))),201);
        String invoice=created.at("/invoice/id").asText();
        jdbc.update("delete from role_permission where role_id in (select role_id from user_role where user_id=?) and permission_id not in (select id from permission where module='INVOICES' and action='READ')",f.user.getId());
        long auditCount=jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId());
        var detail=call(f,get("/api/v1/company/invoices/"+invoice),null,200);
        assertEquals("70.00",detail.at("/invoice/totalAmount").asText());
        assertEquals(2,detail.path("items").size());
        var origins=new java.util.HashSet<String>();
        for(var line:detail.path("items")) {
            origins.add(line.path("measurementContractCompositionId").asText());
            assertEquals(ca.getContractServiceId().toString(),line.path("contractServiceId").asText());
            var expected=allocations.findByIdAndCompanyId(UUID.fromString(line.path("billingAllocationId").asText()),f.company.getId()).orElseThrow();
            assertEquals(expected.getMeasurementContractCompositionId().toString(),line.path("measurementContractCompositionId").asText());
        }
        assertEquals(java.util.Set.of(ca.getId().toString(),cb.getId().toString()),origins);
        assertEquals(1,call(f,get("/api/v1/company/invoices").param("installmentReference","de origens"),null,200).path("totalItems").asInt());
        assertEquals(auditCount,jdbc.queryForObject("select count(*) from audit_event where company_id=?",Long.class,f.company.getId()));
        call(f,get(base(a)),null,403);
    }

    Fixture fixture(boolean finance) {
        return fixture(finance, ContractPricingMode.MEASUREMENT_BASED);
    }

    Fixture fixture(boolean finance, ContractPricingMode pricingMode) {
        String suffix=UUID.randomUUID().toString();Company company=companies.save(Company.create("Emitente "+suffix,null,
            Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),suffix+"@example.com",null,null,null));
        User user=User.create(suffix+"@example.com",UserType.COMPANY);user.activate(passwords.hash("Canteiro#2026Seguro"),Instant.now());user=users.save(user);
        memberships.save(CompanyUser.create(user.getId(),company.getId()));
        Role role=roles.save(Role.create(company.getId(),"Operador "+suffix,null));
        for(AccessModule module:List.of(AccessModule.CONTRACTS,AccessModule.MEASUREMENTS,AccessModule.SERVICES,AccessModule.DISCOUNTS,AccessModule.BILLING,AccessModule.RECEIVABLES,AccessModule.INVOICES)){
            if(!finance&&(module==AccessModule.BILLING||module==AccessModule.RECEIVABLES))continue;
            for(AccessAction action:List.of(AccessAction.READ,AccessAction.CREATE_DIRECT,AccessAction.UPDATE_DIRECT,AccessAction.CANCEL_DIRECT,AccessAction.EXPORT)){
                var permission=permissions.findByModuleAndAction(module,action);if(permission.isPresent())rolePermissions.save(RolePermission.create(role.getId(),permission.get().getId()));
            }
        }userRoles.save(UserRole.create(user.getId(),role.getId(),company.getId()));
        FinalCustomer customer=customers.save(FinalCustomer.create(company.getId(),FinalCustomerType.LEGAL,"Cliente "+suffix,
            String.format("%014d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100_000_000_000_000L))));
        Work work=works.save(Work.create(company.getId(),customer.getId(),"Obra "+suffix,null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        Contract contract=contracts.save(Contract.create(company.getId(),work.getId(),null,pricingMode,
                null,"Contrato por medição",ContractStatus.ACTIVE,null,null,null));
        return new Fixture(company,user,work,contract);
    }
    UUID measurement(Fixture f,boolean linked)throws Exception{
        Map<String,Object> body=new HashMap<>();body.put("title","Medição contínua");body.put("measuredOn","2026-10-11");
        if(linked){body.put("workId",f.work.getId());body.put("contractId",f.contract.getId());}
        return UUID.fromString(call(f,post("/api/v1/company/measurements"),body,201).at("/measurement/id").asText());
    }
    UUID version(Fixture f,UUID mid)throws Exception{return UUID.fromString(call(f,get(base(mid)),null,200).at("/latestVersion/id").asText());}
    UUID service(Fixture f,UUID mid,String title,String action,UUID target)throws Exception{
        Map<String,Object>b=new HashMap<>();b.put("serviceTitle",title);if(action!=null)b.put("destinationAction",action);if(target!=null)b.put("contractServiceId",target);
        return UUID.fromString(call(f,post(base(mid)+"/services"),b,201).path("id").asText());
    }
    UUID item(Fixture f,UUID mid,UUID vid,UUID sid,int number,String activity,String meters,String price)throws Exception{
        return UUID.fromString(call(f,post(base(mid)+"/versions/"+vid+"/items"),Map.of("continuousServiceId",sid,"itemNumber",number,"serviceTitle","Título de apresentação",
            "activity",activity,"measuredOn","2026-10-11","chargeType","LINEAR_METER","linearMeters",new BigDecimal(meters),"unitPrice",price),201).at("/item/id").asText());
    }
    void accept(Fixture f,UUID mid,UUID vid)throws Exception{workflow(f,mid,vid,"SEND",null);workflow(f,mid,vid,"AWAIT_EXTERNAL_ACCEPTANCE",null);workflow(f,mid,vid,"RECORD_EXTERNAL_ACCEPTANCE",true);}
    void workflow(Fixture f,UUID mid,UUID vid,String action,Boolean accepted)throws Exception{
        Map<String,Object>b=new HashMap<>();b.put("action",action);if(accepted!=null){b.put("externallyAccepted",accepted);b.put("externalAcceptanceOn","2026-10-11");}
        call(f,post(base(mid)+"/versions/"+vid+"/workflow"),b,200);
    }
    UUID revision(Fixture f,UUID mid)throws Exception{return UUID.fromString(call(f,post(base(mid)+"/revisions"),Map.of(),201).at("/version/id").asText());}
    JsonNode accumulated(Fixture f,UUID mid)throws Exception{return call(f,get(base(mid)+"/accumulated"),null,200);}
    JsonNode call(Fixture f,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,Object body,int expected)throws Exception{
        request.with(jwt().jwt(token->token.subject(f.user.getId().toString())));
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String output=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return output.isBlank()?json.readTree("{}"):json.readTree(output);
    }
    int rawStatus(Fixture f,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,Object body)throws Exception {
        return mvc.perform(request.with(jwt().jwt(token->token.subject(f.user.getId().toString())))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }
    byte[] download(Fixture f,UUID mid,String path)throws Exception{
        MvcResult pending=mvc.perform(get(base(mid)+"/accumulated/"+path).with(jwt().jwt(token->token.subject(f.user.getId().toString()))))
            .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
    String base(UUID mid){return "/api/v1/company/measurements/"+mid;}
    record Fixture(Company company,User user,Work work,Contract contract){}
}

