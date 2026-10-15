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
            assertEquals("100.00",accumulated(f,a).at("/financialSummary/acceptedAvailableToBill").asText());
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

