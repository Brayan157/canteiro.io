package com.renovar.canteiro.io.receivables;

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
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ManualInvoiceSettlementApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CompanyRepository companies;
    @Autowired FinalCustomerRepository customers;
    @Autowired WorkRepository works;
    @Autowired ContractRepository contracts;
    @Autowired ContractServiceRepository services;
    @Autowired BillingInstallmentRepository installments;
    @Autowired BillingAllocationRepository allocations;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceItemRepository invoiceItems;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReceivableRepository receivables;
    @Autowired ReceivableSettlementRepository settlements;
    @Autowired TaxObligationRepository taxes;
    @Autowired UserRepository users;
    @Autowired CompanyUserRepository memberships;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @Autowired UserRoleRepository userRoles;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired com.renovar.canteiro.io.receivables.application.NetBillingCapacityService reportCapacity;
    @Autowired com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase reportAmounts;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.renovar.canteiro.io.receivables.domain.InvoiceFinancialSnapshotRepository snapshotStorage;
    @Autowired com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository reportDiscounts;
    @Autowired com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider reportStorage;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository reportSource;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.renovar.canteiro.io.receivables.application.ContractBillingPdfGenerator reportGenerator;
    @Autowired com.renovar.canteiro.io.measurements.domain.MeasurementRepository measurements;
    @Autowired com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository measurementVersions;
    @Autowired com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository measurementItems;
    @Autowired com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository measurementServices;
    @Autowired com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository compositions;
    private final ObjectMapper json = new ObjectMapper();


    @Test void lifecycleRequiresExactServiceSettlementRatherThanInvoiceOrOtherServiceCash() throws Exception {
        var f=fixture(false); lifecycleGrant(f);
        assertEquals("SERVICE_NOT_FULLY_SETTLED",call(f,put(serviceStatus(f,0)),Map.of("status","COMPLETED"),409).path("reason").asText());
        call(f,post(base(f)+"/invoice-settlements"),body(f,"60.00"),201);
        call(f,put(serviceStatus(f,0)),Map.of("status","COMPLETED"),200);
        assertEquals("SERVICE_NOT_FULLY_SETTLED",call(f,put(serviceStatus(f,1)),Map.of("status","COMPLETED"),409).path("reason").asText());
        assertEquals("CONTRACT_HAS_NON_TERMINAL_SERVICES",call(f,put(contractPath(f)+"/status"),Map.of("status","COMPLETED"),409).path("reason").asText());
        var last=body(f,"40.00");last.put("items",List.of(line(f,1,"40.00")));
        call(f,post(base(f)+"/invoice-settlements"),last,201);
        call(f,put(serviceStatus(f,1)),Map.of("status","COMPLETED"),200);
        call(f,put(contractPath(f)+"/status"),Map.of("status","COMPLETED"),200);
        assertEquals(2,count("receivable_settlement",f));
    }

    @Test void lifecycleSettlesWithProvenTaxAndPreservesHeaderAndServiceDiscounts() throws Exception {
        for(int style=0;style<4;style++) {
            var f=fixture(false,false,false,style==1 || style==3,style==2 || style==3);lifecycleGrant(f);
            if(style==0)retain(f,"10.00");
            call(f,post(base(f)+"/invoice-settlements"),body(f,style==3 ? "48.60" : "54.00",style==3 ? "32.40" : "36.00"),201);
            var result=call(f,put(serviceStatus(f,0)),Map.of("status","COMPLETED"),200);
            assertEquals(style==2 || style==3 ? "54.00" : "60.00",result.at("/service/netAmount").asText());
            assertEquals("60.00",result.at("/service/unitPrice").asText());
            if(style==2 || style==3)assertEquals("PERCENTAGE",result.at("/service/discountType").asText());
            call(f,put(serviceStatus(f,1)),Map.of("status","COMPLETED"),200);
            call(f,put(contractPath(f)+"/status"),Map.of("status","FINALIZED"),200);
            var control=call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,200);
            assertEquals(style==0 ? "100.00" : style==3 ? "81.00" : "90.00",control.path("contractNetAmount").asText());
            assertEquals(style==1 ? "10.00" : style==3 ? "9.00" : "0.00",control.path("contractDiscountAmount").asText());
            assertEquals(style==3 ? "81.00" : "90.00",cash(f,0).path("receivedAmount").asText());
        }
    }

    @Test void lifecycleBlocksCreditAndUnknownLegacyEvenWhenTitleLooksSettled() throws Exception {
        var legacy=fixture(false);lifecycleGrant(legacy);
        settlements.save(ReceivableSettlement.confirm(legacy.companyId(),legacy.receivableId(),legacy.contractId(),today(),new BigDecimal("100.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertEquals("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED",call(legacy,put(serviceStatus(legacy,0)),Map.of("status","COMPLETED"),409).path("reason").asText());
        var credit=fixture(false);lifecycleGrant(credit);
        jdbc.update("insert into receivable_credit(id,company_id,receivable_id,contract_id,amount,reason,actor_user_id) values(?,?,?,?,?,?,?)",UUID.randomUUID(),credit.companyId(),credit.receivableId(),credit.contractId(),new BigDecimal("100.00"),"Crédito sintético",credit.userId());
        assertEquals("SERVICE_CREDIT_REQUIRES_RECONCILIATION",call(credit,put(serviceStatus(credit,0)),Map.of("status","COMPLETED"),409).path("reason").asText());
    }

    @Test void lifecycleCompletedServiceKeepsFinancialBlockingReasonAndNeverDisclosesItWithoutReads() throws Exception {
        var legacy=fixture(false,false,false,true);lifecycleGrant(legacy);
        settlements.save(ReceivableSettlement.confirm(legacy.companyId(),legacy.receivableId(),legacy.contractId(),today(),new BigDecimal("90.00"),ReceivableSettlementPaymentMethod.PIX,null));
        // A historical status is not proof of receipt ownership; this fixture predates exact invoice-item attribution.
        jdbc.update("update contract_service set status='COMPLETED' where company_id=? and contract_id=?",legacy.companyId(),legacy.contractId());
        String route=contractPath(legacy)+"/closure-eligibility",service=legacy.items().getFirst().getContractServiceId().toString();
        var before=snapshot(legacy,false);
        var data=call(legacy,get(route).param("serviceId",service).param("size","1"),null,200);
        assertEquals("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED",data.at("/completion/reason").asText());
        assertEquals("COMPLETED",data.at("/services/items/0/status").asText());
        assertEquals("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED",data.at("/services/items/0/completion/reason").asText());
        assertFalse(data.at("/services/items/0/completion/eligible").asBoolean());
        assertEquals(before,snapshot(legacy,false));
        assertEquals("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED",call(legacy,put(contractPath(legacy)+"/status"),Map.of("status","COMPLETED"),409).path("reason").asText());
        assertEquals(before,snapshot(legacy,false));
        revoke(legacy,AccessModule.RECEIVABLES,AccessAction.READ);
        var hidden=call(legacy,get(route).param("serviceId",service),null,200);
        assertEquals("CLOSURE_FINANCIAL_READ_REQUIRED",hidden.at("/completion/reason").asText());
        assertTrue(hidden.at("/completion/eligible").isNull());
        assertEquals("SERVICE_ALREADY_COMPLETED",hidden.at("/services/items/0/completion/reason").asText());
        assertFalse(hidden.toString().contains("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED"));
        var paid=fixture(false);lifecycleGrant(paid);
        call(paid,post(base(paid)+"/invoice-settlements"),body(paid,"60.00","40.00"),201);
        call(paid,put(serviceStatus(paid,0)),Map.of("status","COMPLETED"),200);
        var available=call(paid,get(contractPath(paid)+"/closure-eligibility").param("serviceId",paid.items().getFirst().getContractServiceId().toString()),null,200);
        assertEquals("SERVICE_ALREADY_COMPLETED",available.at("/services/items/0/completion/reason").asText());
        assertFalse(available.at("/services/items/0/completion/eligible").asBoolean());
    }

    @Test void lifecycleAllowsZeroWithoutCommitmentsAndCancellationDoesNotSettleDebt() throws Exception {
        var f=fixture(false);lifecycleGrant(f);
        var zero=services.save(ContractService.create(f.companyId(),f.contractId(),"Sem valor",null,ContractServiceStatus.DRAFT,BigDecimal.ONE,BigDecimal.ZERO));
        call(f,put(contractPath(f)+"/services/"+zero.getId()+"/status"),Map.of("status","COMPLETED"),200);
        assertEquals("SERVICE_HAS_FINANCIAL_COMMITMENTS",call(f,put(serviceStatus(f,0)),Map.of("status","CANCELLED"),409).path("reason").asText());
        var unbilled=services.save(ContractService.create(f.companyId(),f.contractId(),"Sem faturamento",null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("20.00")));
        call(f,put(contractPath(f)+"/services/"+unbilled.getId()+"/status"),Map.of("status","CANCELLED"),200);
        assertEquals("100.00",call(f,get(base(f)+"/balance"),null,200).path("outstandingAmount").asText());
        assertEquals("CONTRACT_SERVICES_NOT_CANCELLED",call(f,put(contractPath(f)+"/status"),Map.of("status","CANCELLED"),409).path("reason").asText());
        assertEquals(0,count("receivable_settlement",f));
    }

    @Test void lifecycleEmptyContractCanCloseButMustReopenBeforeNewOrReactivatedServices() throws Exception {
        var f=fixture(false);lifecycleGrant(f);var empty=emptyContract(f);
        String path="/api/v1/company/contracts/"+empty.getId();
        call(f,put(path+"/status"),Map.of("status","COMPLETED"),200);
        var draft=Map.of("name","Novo","status","DRAFT","quantity","1.0000","unitPrice","1.00");
        assertEquals("CONTRACT_REOPEN_REQUIRED",call(f,post(path+"/services"),draft,409).path("reason").asText());
        call(f,put(path+"/status"),Map.of("status","CANCELLED"),200);
        call(f,put(path+"/status"),Map.of("status","ACTIVE"),200);
        call(f,post(path+"/services"),draft,201);
        assertEquals("CONTRACT_HAS_NON_TERMINAL_SERVICES",call(f,put(path+"/status"),Map.of("status","FINALIZED"),409).path("reason").asText());
    }

    @Test void lifecycleEligibilitySummaryUsesWholeContractAndExactFilterAndHidesFinancialDecisionsWithoutRead() throws Exception {
        var f=fixture(false);lifecycleGrant(f);
        var before=snapshot(f,false);
        var result=call(f,get(contractPath(f)+"/closure-eligibility").param("size","1").param("serviceId",f.items().getFirst().getContractServiceId().toString()),null,200);
        assertEquals(2,result.path("serviceCount").asInt());assertEquals(2,result.path("nonTerminalServiceCount").asInt());assertEquals(1,result.at("/services/totalItems").asInt());
        assertEquals("CONTRACT_HAS_NON_TERMINAL_SERVICES",result.at("/completion/reason").asText());
        assertEquals("DIRECT",result.at("/services/items/0/cancellation/authorizationMode").asText());
        assertEquals(before,snapshot(f,false));
        var reader=actor(f);grant(reader,AccessModule.CONTRACTS,AccessAction.READ);grant(reader,AccessModule.SERVICES,AccessAction.READ);grant(reader,AccessModule.SERVICES,AccessAction.UPDATE_DIRECT);
        var hidden=call(reader,get(contractPath(f)+"/closure-eligibility"),null,200);
        assertTrue(hidden.at("/services/items/0/completion/eligible").isNull());
        assertEquals("CLOSURE_FINANCIAL_READ_REQUIRED",hidden.at("/services/items/0/completion/reason").asText());
        assertEquals("CLOSURE_NOT_AUTHORIZED",hidden.at("/services/items/0/cancellation/reason").asText());
        for(String field:List.of("amount","receivedAmount","invoiceId","receivableId","beforeData"))assertFalse(hidden.toString().contains("\""+field+"\""));
        var foreign=fixture(false);lifecycleGrant(foreign);
        call(foreign,get(contractPath(f)+"/closure-eligibility"),null,404);
        call(f,get(contractPath(f)+"/closure-eligibility").param("serviceId",foreign.items().getFirst().getContractServiceId().toString()),null,404);
        for(String[] invalid:new String[][]{{"page","-1"},{"size","0"},{"size","101"}})call(f,get(contractPath(f)+"/closure-eligibility").param(invalid[0],invalid[1]),null,400);
    }

    @Test void lifecycleStatusProposalHasNoFinancialSnapshotAndRechecksOnApprovalAfterCashReversal() throws Exception {
        var f=fixture(false);lifecycleGrant(f);grant(f,AccessModule.RECEIVABLES,AccessAction.CANCEL_DIRECT);
        var received=call(f,post(base(f)+"/invoice-settlements"),body(f,"60.00","40.00"),201);
        var requester=actor(f);grant(requester,AccessModule.SERVICES,AccessAction.REQUEST_UPDATE);
        var approver=actor(f);grant(approver,AccessModule.SERVICES,AccessAction.APPROVE);
        var before=snapshot(f,true);
        var pending=call(requester,put(serviceStatus(f,0)),Map.of("status","COMPLETED"),202);
        assertEquals(before,snapshot(f,true));
        String proposal=jdbc.queryForObject("select proposed_data::text from change_request where company_id=? and id=?",String.class,f.companyId(),UUID.fromString(pending.path("changeRequestId").asText()));
        assertFalse(proposal.contains("unitPrice"));assertFalse(proposal.contains("discountValue"));
        call(f,post(base(f)+"/settlements/"+received.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Estorno sintético"),201);
        before=snapshot(f,false);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertEquals(before,snapshot(f,false));
    }

    @Test void lifecycleStatusOnlyApprovalPreservesProfileChangedAfterRequest() throws Exception {
        var f=fixture(false);lifecycleGrant(f);var requester=actor(f);grant(requester,AccessModule.SERVICES,AccessAction.REQUEST_UPDATE);
        var approver=actor(f);grant(approver,AccessModule.SERVICES,AccessAction.APPROVE);
        var pending=call(requester,put(serviceStatus(f,0)),Map.of("status","DRAFT"),202);
        call(f,put(serviceStatus(f,0).replace("/status","")),Map.of("name","Nome posterior","status","ACTIVE","quantity","2.0000","unitPrice","40.00"),200);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        var saved=services.findByIdAndCompanyId(f.items().getFirst().getContractServiceId(),f.companyId()).orElseThrow();
        assertEquals("Nome posterior",saved.getName());assertEquals(new BigDecimal("40.00"),saved.getUnitPrice());assertEquals(new BigDecimal("2.0000"),saved.getQuantity());assertEquals(ContractServiceStatus.DRAFT,saved.getStatus());
    }

    @Test void lifecycleCancellationRequiresCancelAuthorizationAndApprovalHandler() throws Exception {
        var f=fixture(false);lifecycleGrant(f);var empty=emptyContract(f);
        var child=services.save(ContractService.create(f.companyId(),empty.getId(),"Livre",null,ContractServiceStatus.DRAFT,BigDecimal.ONE,BigDecimal.ONE));
        var onlyUpdate=actor(f);grant(onlyUpdate,AccessModule.SERVICES,AccessAction.UPDATE_DIRECT);
        String path="/api/v1/company/contracts/"+empty.getId();String childPath=path+"/services/"+child.getId()+"/status";
        call(onlyUpdate,put(childPath),Map.of("status","CANCELLED"),403);
        var requester=actor(f);grant(requester,AccessModule.SERVICES,AccessAction.REQUEST_CANCEL);grant(requester,AccessModule.CONTRACTS,AccessAction.REQUEST_CANCEL);
        var approver=actor(f);grant(approver,AccessModule.SERVICES,AccessAction.APPROVE);grant(approver,AccessModule.CONTRACTS,AccessAction.APPROVE);
        var pending=call(requester,put(childPath),Map.of("status","CANCELLED"),202);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        pending=call(requester,put(path+"/status"),Map.of("status","CANCELLED"),202);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals(ContractStatus.CANCELLED,contracts.findByIdAndCompanyId(empty.getId(),f.companyId()).orElseThrow().getStatus());
    }

    @Test void lifecycleContractApprovalRechecksChildrenAndStatusDatePreservesCurrentWhenOmitted() throws Exception {
        var f=fixture(false);lifecycleGrant(f);var empty=emptyContract(f);String path="/api/v1/company/contracts/"+empty.getId();
        var requester=actor(f);grant(requester,AccessModule.CONTRACTS,AccessAction.REQUEST_UPDATE);
        var approver=actor(f);grant(approver,AccessModule.CONTRACTS,AccessAction.APPROVE);
        var pending=call(requester,put(path+"/status"),Map.of("status","COMPLETED"),202);
        call(f,post(path+"/services"),Map.of("name","Após proposta","status","DRAFT","quantity","1.0000","unitPrice","0.00"),201);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        var second=emptyContract(f);String secondPath="/api/v1/company/contracts/"+second.getId();
        pending=call(requester,put(secondPath+"/status"),Map.of("status","ACTIVE"),202);
        call(f,put(secondPath),Map.of("name","Nome mais recente","status","ACTIVE","completedOn","2026-01-15"),200);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        var saved=contracts.findByIdAndCompanyId(second.getId(),f.companyId()).orElseThrow();assertEquals("Nome mais recente",saved.getName());assertEquals(LocalDate.parse("2026-01-15"),saved.getCompletedOn());
    }

    @Test void lifecycleDiscountBlocksOutstandingChargeWithoutInvoiceInDirectAndProposalAndServicePaths() throws Exception {
        var f=fixture(false);lifecycleGrant(f);grant(f,AccessModule.DISCOUNTS,AccessAction.UPDATE_DIRECT);
        jdbc.update("update invoice set status='CANCELLED' where company_id=? and contract_id=?",f.companyId(),f.contractId());
        var discount=Map.of("discountType","PERCENTAGE","discountValue","1.0000","justification","Teste sintético");var before=snapshot(f,false);
        assertEquals("CONTRACT_HAS_OPEN_CHARGES",call(f,put(contractPath(f)+"/discount"),discount,409).path("reason").asText());
        assertEquals("CONTRACT_HAS_OPEN_CHARGES",call(f,put(serviceStatus(f,0).replace("/status","/discount")),discount,409).path("reason").asText());
        assertEquals("CONTRACT_HAS_OPEN_CHARGES",call(f,put(serviceStatus(f,0).replace("/status","")),Map.of("name","Redução","status","ACTIVE","quantity","1.0000","unitPrice","59.00"),409).path("reason").asText());
        var requester=actor(f);grant(requester,AccessModule.DISCOUNTS,AccessAction.REQUEST_UPDATE);
        call(requester,put(contractPath(f)+"/discount"),discount,409);
        call(f,post(contractPath(f)+"/services"),Map.of("name","Com desconto","status","ACTIVE","quantity","1.0000","unitPrice","10.00","discountType","FIXED","discountValue","1.00"),409);
        assertEquals(before,snapshot(f,false));
    }

    @Test void lifecycleDiscountUsesOfficialCashFloorAndDoesNotTreatPaidOpenInstallmentAsUnpaid() throws Exception {
        var f=fixture(false);lifecycleGrant(f);grant(f,AccessModule.DISCOUNTS,AccessAction.UPDATE_DIRECT);
        call(f,post(base(f)+"/invoice-settlements"),body(f,"60.00","40.00"),201);
        assertEquals("CONTRACT_NET_BELOW_RECEIVED_AMOUNT",call(f,put(contractPath(f)+"/discount"),Map.of("discountType","FIXED","discountValue","1.00","justification","Teste sintético"),409).path("reason").asText());
        call(f,put(serviceStatus(f,0).replace("/status","")),Map.of("name","Base ampliada","status","ACTIVE","quantity","1.0000","unitPrice","110.00"),200);
        call(f,put(contractPath(f)+"/discount"),Map.of("discountType","FIXED","discountValue","20.00","justification","Teste sintético"),200);
        assertEquals("100.00",cash(f,0).path("receivedAmount").asText());
    }

    @Test void lifecycleDiscountApprovalRechecksNewOpenChargeAndCancelledTitleIsNotOpen() throws Exception {
        var f=fixture(false);lifecycleGrant(f);grant(f,AccessModule.DISCOUNTS,AccessAction.UPDATE_DIRECT);
        jdbc.update("update receivable set status='CANCELLED' where company_id=? and id=?",f.companyId(),f.receivableId());
        call(f,put(serviceStatus(f,0).replace("/status","")),Map.of("name","Base ampliada","status","ACTIVE","quantity","1.0000","unitPrice","110.00"),200);
        var requester=actor(f);grant(requester,AccessModule.DISCOUNTS,AccessAction.REQUEST_UPDATE);var approver=actor(f);grant(approver,AccessModule.DISCOUNTS,AccessAction.APPROVE);
        var pending=call(requester,put(contractPath(f)+"/discount"),Map.of("discountType","FIXED","discountValue","10.00","justification","Teste sintético"),202);
        jdbc.update("update receivable set status='OPEN' where company_id=? and id=?",f.companyId(),f.receivableId());var before=snapshot(f,false);
        assertEquals("CONTRACT_HAS_OPEN_CHARGES",call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),409).path("reason").asText());
        assertEquals(before,snapshot(f,false));
    }

    @Test void lifecycleGenericUpdatesAndCreationCannotBypassStatusRules() throws Exception {
        var f=fixture(false);lifecycleGrant(f);
        assertEquals("SERVICE_NOT_FULLY_SETTLED",call(f,put(serviceStatus(f,0).replace("/status","")),Map.of("name","Serviço 0","status","COMPLETED","quantity","1.0000","unitPrice","60.00"),409).path("reason").asText());
        assertEquals("CONTRACT_HAS_NON_TERMINAL_SERVICES",call(f,put(contractPath(f)),Map.of("name","Contrato","status","COMPLETED"),409).path("reason").asText());
        var before=snapshot(f,false);
        call(f,post(contractPath(f)+"/services"),Map.of("name","Não recebido","status","COMPLETED","quantity","1.0000","unitPrice","1.00"),409);
        assertEquals(before,snapshot(f,false));
        call(f,post(contractPath(f)+"/services"),Map.of("name","Sem valor","status","COMPLETED","quantity","1.0000","unitPrice","0.00"),201);
    }

    @Test void lifecycleConcurrentContractCompletionAndChildCreationRemainConsistent() throws Exception {
        var f=fixture(false);lifecycleGrant(f);var empty=emptyContract(f);String path="/api/v1/company/contracts/"+empty.getId();
        var go=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var closing=pool.submit(()->{ready.countDown();go.await();return mvc.perform(put(path+"/status").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}")).andReturn().getResponse().getStatus();});
            var creating=pool.submit(()->{ready.countDown();go.await();return mvc.perform(post(path+"/services").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Concorrente\",\"status\":\"DRAFT\",\"quantity\":\"1.0000\",\"unitPrice\":\"0.00\"}")).andReturn().getResponse().getStatus();});
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            int closeStatus=closing.get(30,TimeUnit.SECONDS),createStatus=creating.get(30,TimeUnit.SECONDS);
            assertTrue(closeStatus==200 && createStatus==409 || closeStatus==409 && createStatus==201);
            var current=contracts.findByIdAndCompanyId(empty.getId(),f.companyId()).orElseThrow();
            assertEquals(current.getStatus()==ContractStatus.COMPLETED ? 0 : 1,services.findByContractIdAndCompanyId(empty.getId(),f.companyId()).size());
        }
    }

    @Test void lifecycleDiscountCashFloorSurvivesHistoricalCancelledAllocations() throws Exception {
        var f=fixture(false);lifecycleGrant(f);grant(f,AccessModule.DISCOUNTS,AccessAction.UPDATE_DIRECT);
        settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("100.00"),ReceivableSettlementPaymentMethod.PIX,null));
        jdbc.update("update billing_allocation set status='CANCELLED' where company_id=? and contract_id=?",f.companyId(),f.contractId());
        var before=snapshot(f,false);
        assertEquals("CONTRACT_NET_BELOW_RECEIVED_AMOUNT",call(f,put(contractPath(f)+"/discount"),Map.of("discountType","FIXED","discountValue","1.00","justification","Histórico sintético"),409).path("reason").asText());
        assertEquals(before,snapshot(f,false));
    }

    private void lifecycleGrant(F f) {
        for(var a:List.of(AccessAction.READ,AccessAction.CREATE_DIRECT,AccessAction.UPDATE_DIRECT,AccessAction.CANCEL_DIRECT))grant(f,AccessModule.SERVICES,a);
        for(var a:List.of(AccessAction.READ,AccessAction.UPDATE_DIRECT,AccessAction.CANCEL_DIRECT))grant(f,AccessModule.CONTRACTS,a);
        grant(f,AccessModule.BILLING,AccessAction.READ);
    }
    private String contractPath(F f) {return "/api/v1/company/contracts/"+f.contractId();}
    private String serviceStatus(F f,int index) {return contractPath(f)+"/services/"+f.items().get(index).getContractServiceId()+"/status";}
    private Contract emptyContract(F f) {return contracts.save(Contract.create(f.companyId(),contracts.findByIdAndCompanyId(f.contractId(),f.companyId()).orElseThrow().getWorkId(),"VAZIO","Sem serviços",ContractStatus.ACTIVE,null,null,null));}

    @Test void installmentFinancialReadBatchesOnlyTheCurrentPageAndKeepsTenantAndOfficialData() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        for(int i=0;i<4;i++) {
            var installment=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"BATCH-"+i,null,today(),today().plusDays(30)));
            var title=receivables.save(Receivable.create(f.companyId(),f.contractId(),installment.getId(),UUID.randomUUID(),"BATCH-"+i,null,today(),today().plusDays(30),new BigDecimal("10.00")));
            if(i==0)settlements.save(ReceivableSettlement.confirm(f.companyId(),title.getId(),f.contractId(),today(),new BigDecimal("10.00"),ReceivableSettlementPaymentMethod.PIX,null));
        }
        var before=snapshot(f,false);
        for(int page=0;page<2;page++) {
            org.mockito.Mockito.clearInvocations(receivables);
            var data=call(f,get("/api/v1/company/billing-installments").param("contractId",f.contractId().toString()).param("page",Integer.toString(page)).param("size","2").param("sort","reference,asc"),null,200);
            assertEquals(5,data.path("totalItems").asInt());assertEquals(3,data.path("totalPages").asInt());assertEquals(2,data.path("items").size());
            var ids=data.path("items").valueStream().map(row->UUID.fromString(row.path("id").asText())).toList();
            org.mockito.Mockito.verify(receivables,org.mockito.Mockito.times(1)).findOriginalPositionsByBillingInstallmentIdsAndCompanyId(ids,f.companyId());
            org.mockito.Mockito.verify(receivables,org.mockito.Mockito.never()).findReadByIdAndCompanyId(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            for(var row:data.path("items"))assertInstallmentPosition(row,row.path("reference").asText().equals("BATCH-0")?"RECEIVED":"OPEN",false);
        }
        assertEquals(before,snapshot(f,false));
        var foreign=fixture(false);grant(foreign,AccessModule.BILLING,AccessAction.READ);
        UUID foreignId=receivables.findByIdAndCompanyId(foreign.receivableId(),foreign.companyId()).orElseThrow().getBillingInstallmentId();
        assertTrue(receivables.findOriginalPositionsByBillingInstallmentIdsAndCompanyId(List.of(foreignId),f.companyId()).isEmpty());
        call(f,get("/api/v1/company/billing-installments/"+foreignId),null,404);
        org.mockito.Mockito.clearInvocations(receivables);
        assertEquals(0,call(f,get("/api/v1/company/billing-installments").param("contractId",foreign.contractId().toString()),null,200).path("totalItems").asInt());
        org.mockito.Mockito.verify(receivables,org.mockito.Mockito.never()).findOriginalPositionsByBillingInstallmentIdsAndCompanyId(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any());
        revoke(f,AccessModule.BILLING,AccessAction.READ);
        call(f,get("/api/v1/company/billing-installments"),null,403);
        call(f,get("/api/v1/company/billing-installments/"+foreignId),null,403);
    }

    @Test void billingFinancialStatusReflectsCashAndReversalsWhilePersistedInstallmentStaysOpen() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        assertFinancial(f,"OPEN");
        var first=settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("30.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(f,"PARTIALLY_RECEIVED");
        var rest=settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("70.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(f,"RECEIVED");
        settlements.save(ReceivableSettlement.reverse(rest,today(),"Estorno sintético"));assertFinancial(f,"PARTIALLY_RECEIVED");
        settlements.save(ReceivableSettlement.reverse(first,today(),"Estorno sintético"));assertFinancial(f,"OPEN");
    }

    @Test void billingFinancialStatusTreatsWithholdingAndCreditsAsAdjustmentsRatherThanReceivedCash() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),f.receivableId(),"Retenção integral",null,null,new BigDecimal("100.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        assertFinancial(f,"SETTLED_WITH_ADJUSTMENTS");
        var credited=fixture(false);grant(credited,AccessModule.BILLING,AccessAction.READ);
        jdbc.update("insert into receivable_credit(id,company_id,receivable_id,contract_id,amount,reason,actor_user_id) values (?,?,?,?,?,?,?)",UUID.randomUUID(),credited.companyId(),credited.receivableId(),credited.contractId(),new BigDecimal("100.00"),"Crédito sintético",credited.userId());
        assertFinancial(credited,"SETTLED_WITH_ADJUSTMENTS");
        assertEquals(0,count("receivable_settlement",credited));
    }

    @Test void billingFinancialStatusPreservesCancelledAndRenegotiatedOriginalDespitePaidChild() throws Exception {
        var cancelled=fixture(false);grant(cancelled,AccessModule.BILLING,AccessAction.READ);
        jdbc.update("update receivable set status='CANCELLED' where id=?",cancelled.receivableId());assertFinancial(cancelled,"CANCELLED");
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        jdbc.update("update receivable set status='RENEGOTIATED' where id=?",f.receivableId());
        var original=receivables.findByIdAndCompanyId(f.receivableId(),f.companyId()).orElseThrow();
        var child=receivables.saveRenegotiated(Receivable.create(f.companyId(),f.contractId(),original.getBillingInstallmentId(),UUID.randomUUID(),"SUCESSOR",null,today(),today().plusDays(30),new BigDecimal("100.00")),original.getId());
        settlements.save(ReceivableSettlement.confirm(f.companyId(),child.getId(),f.contractId(),today(),new BigDecimal("100.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(f,"RENEGOTIATED");
    }

    @Test void billingFinancialStatusIsUnavailableWithoutPermissionOrOriginalAndIgnoresPendingReceipt() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        var requester=actor(f,AccessAction.REQUEST_CREATE);
        call(requester,post(base(f)+"/invoice-settlements"),body(f,"30.00","20.00"),202);
        assertFinancial(f,"OPEN");
        revoke(f,AccessModule.RECEIVABLES,AccessAction.READ);
        org.mockito.Mockito.clearInvocations(receivables);
        assertInstallmentReads(f,null,null);
        org.mockito.Mockito.verify(receivables,org.mockito.Mockito.never()).findOriginalPositionsByBillingInstallmentIdsAndCompanyId(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any());
        var hidden=call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,200);
        for(var row:hidden.path("allocations")){assertFalse(row.path("receivableFinancialStatusAvailable").asBoolean());assertTrue(row.has("receivableFinancialStatus"));assertTrue(row.path("receivableFinancialStatus").isNull());assertTrue(row.has("receivableReceivedWithWithholding"));assertTrue(row.path("receivableReceivedWithWithholding").isNull());}
        jdbc.update("update role_permission set active=true where role_id=? and permission_id=?",f.roleId(),permissions.findByModuleAndAction(AccessModule.RECEIVABLES,AccessAction.READ).orElseThrow().getId());
        var empty=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"SEM-TITULO",null,today(),today().plusDays(30)));
        jdbc.update("update billing_allocation set billing_installment_id=? where id=?",empty.getId(),f.items().getFirst().getBillingAllocationId());
        assertInstallmentPosition(call(f,get("/api/v1/company/billing-installments/"+empty.getId()),null,200),null,null);
        assertInstallmentPosition(call(f,get("/api/v1/company/billing-installments").param("reference","SEM-TITULO"),null,200).at("/items/0"),null,null);
        var missing=call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,200);
        var unavailable=missing.path("allocations").valueStream().filter(r->r.path("billingInstallmentId").asText().equals(empty.getId().toString())).findFirst().orElseThrow();
        assertFalse(unavailable.path("receivableFinancialStatusAvailable").asBoolean());assertTrue(unavailable.path("receivableFinancialStatus").isNull());assertTrue(unavailable.path("receivableReceivedWithWithholding").isNull());
        var foreign=fixture(false);grant(foreign,AccessModule.BILLING,AccessAction.READ);
        call(foreign,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,404);
        revoke(f,AccessModule.BILLING,AccessAction.READ);call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,403);
    }

    @Test void billingFinancialStatusLoadsOfficialPositionBeyondTheFirstHundredTitles() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        for(int index=0;index<101;index++) {
            var installment=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"OUTRA-"+index,null,today(),today().plusDays(1)));
            receivables.save(Receivable.create(f.companyId(),f.contractId(),installment.getId(),UUID.randomUUID(),"TITULO-"+index,null,today(),today().plusDays(1),BigDecimal.ONE));
        }
        settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("30.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(f,"PARTIALLY_RECEIVED");
    }

    @Test void billingFinancialStatusReceivedWithWithholdingHintDoesNotReclassifyCashOrCredit() throws Exception {
        var f=fixture(false);grant(f,AccessModule.BILLING,AccessAction.READ);
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),f.receivableId(),"Imposto retido",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        var cash=settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("90.00"),ReceivableSettlementPaymentMethod.PIX,null));
        var before=snapshot(f,true);var response=call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,200);
        assertEquals("90.00",response.path("receivedAmount").asText());assertEquals(before,snapshot(f,true));
        for(var row:response.path("allocations")){assertEquals("SETTLED_WITH_ADJUSTMENTS",row.path("receivableFinancialStatus").asText());assertTrue(row.path("receivableReceivedWithWithholding").asBoolean());}
        assertInstallmentReads(f,"SETTLED_WITH_ADJUSTMENTS",true);
        settlements.save(ReceivableSettlement.reverse(cash,today(),"Estorno sintético"));assertFinancial(f,"OPEN");
        var mixed=fixture(false);grant(mixed,AccessModule.BILLING,AccessAction.READ);
        taxes.save(TaxObligation.register(mixed.companyId(),mixed.items().getFirst().getInvoiceId(),mixed.contractId(),mixed.receivableId(),"Retido",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        jdbc.update("insert into receivable_credit(id,company_id,receivable_id,contract_id,amount,reason,actor_user_id) values (?,?,?,?,?,?,?)",UUID.randomUUID(),mixed.companyId(),mixed.receivableId(),mixed.contractId(),new BigDecimal("20.00"),"Crédito sintético",mixed.userId());
        settlements.save(ReceivableSettlement.confirm(mixed.companyId(),mixed.receivableId(),mixed.contractId(),today(),new BigDecimal("70.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(mixed,"SETTLED_WITH_ADJUSTMENTS");
        var partial=fixture(false);grant(partial,AccessModule.BILLING,AccessAction.READ);
        taxes.save(TaxObligation.register(partial.companyId(),partial.items().getFirst().getInvoiceId(),partial.contractId(),partial.receivableId(),"Retido",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        settlements.save(ReceivableSettlement.confirm(partial.companyId(),partial.receivableId(),partial.contractId(),today(),new BigDecimal("40.00"),ReceivableSettlementPaymentMethod.PIX,null));
        assertFinancial(partial,"PARTIALLY_RECEIVED");
    }

    private void assertInstallmentReads(F f,String expected,Boolean hint)throws Exception {
        // Fixture lookup is outside the API projection. The server never looks up a title per list row.
        UUID id=receivables.findByIdAndCompanyId(f.receivableId(),f.companyId()).orElseThrow().getBillingInstallmentId();
        var before=snapshot(f,false);
        var detail=call(f,get("/api/v1/company/billing-installments/"+id),null,200);
        var list=call(f,get("/api/v1/company/billing-installments").param("contractId",f.contractId().toString()).param("reference","PARC"),null,200);
        assertEquals(1,list.path("totalItems").asInt());assertEquals(id.toString(),list.at("/items/0/id").asText());
        assertEquals("OPEN",detail.path("status").asText());assertEquals("100.00",detail.path("netAmount").asText());
        assertEquals("OPEN",list.at("/items/0/status").asText());assertEquals("100.00",list.at("/items/0/netAmount").asText());
        assertInstallmentPosition(detail,expected,hint);assertInstallmentPosition(list.at("/items/0"),expected,hint);
        assertEquals(before,snapshot(f,false));
    }
    private void assertInstallmentPosition(JsonNode row,String expected,Boolean hint) {
        assertTrue(row.has("receivableFinancialStatusAvailable"));assertTrue(row.has("receivableFinancialStatus"));assertTrue(row.has("receivableReceivedWithWithholding"));
        assertEquals(expected!=null,row.path("receivableFinancialStatusAvailable").asBoolean());
        if(expected==null) {assertTrue(row.path("receivableFinancialStatus").isNull());assertTrue(row.path("receivableReceivedWithWithholding").isNull());}
        else {assertEquals(expected,row.path("receivableFinancialStatus").asText());assertEquals(hint,row.path("receivableReceivedWithWithholding").asBoolean());}
        for(String field:List.of("receivedAmount","outstandingAmount","withheldAmount","creditedAmount","confirmedSettlementAmount","financialPosition"))assertFalse(row.has(field));
    }

    private void assertFinancial(F f,String expected)throws Exception {
        assertInstallmentReads(f,expected,false);
        var data=call(f,get("/api/v1/company/billing-control/contracts/"+f.contractId()),null,200);
        assertEquals(2,data.path("allocations").size());
        for(var row:data.path("allocations")){
            assertEquals("OPEN",row.path("installmentStatus").asText());
            assertTrue(row.path("receivableFinancialStatusAvailable").asBoolean());
            assertEquals(expected,row.path("receivableFinancialStatus").asText());
            assertTrue(row.has("receivableReceivedWithWithholding"));assertFalse(row.path("receivableReceivedWithWithholding").asBoolean());
        }
    }

    @Test void freezesInvoiceRegistrationCashSnapshotAndDoesNotBackfillLegacyInvoices() throws Exception {
        var f=fixture(true);reportPermissions(f);grant(f,AccessModule.RECEIVABLES,AccessAction.EXPORT);
        grant(f,AccessModule.INVOICES,AccessAction.CREATE_DIRECT);
        assertEquals(0,count("invoice_financial_snapshot",f));
        invoices.updateStatus(invoices.findByIdAndCompanyId(f.items().getLast().getInvoiceId(),f.companyId()).orElseThrow().cancel());
        settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("20.00"),ReceivableSettlementPaymentMethod.PIX,null));
        var result=call(f,post("/api/v1/company/invoices"),registrationBody(f),201);
        var id=UUID.fromString(result.at("/invoice/id").asText());
        var snapshot=jdbc.queryForMap("select * from invoice_financial_snapshot where invoice_id=?",id);
        assertEquals(new BigDecimal("100.00"),snapshot.get("contract_net_amount"));
        assertEquals(new BigDecimal("20.00"),snapshot.get("confirmed_received_amount"));
        assertEquals(new BigDecimal("80.00"),snapshot.get("unreceived_cash_amount"));
        assertEquals("BRL",snapshot.get("currency"));assertTrue(snapshot.get("observed_at")!=null);
        settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("10.00"),ReceivableSettlementPaymentMethod.PIX,null));
        invoices.updateStatus(invoices.findByIdAndCompanyId(id,f.companyId()).orElseThrow().cancel());
        assertEquals(snapshot,jdbc.queryForMap("select * from invoice_financial_snapshot where invoice_id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("update invoice_financial_snapshot set confirmed_received_amount=0 where invoice_id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoice_financial_snapshot where invoice_id=?",id));
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Saldo histórico"));assertFalse(text.contains("no registro: 80,00"));
            assertFalse(text.contains("Total recebido em dinheiro:"));
        }
    }

    @Test void capturesInvoiceSnapshotOnlyOnApprovalAndRollsBackSnapshotFailure() throws Exception {
        var f=fixture(true);
        invoices.updateStatus(invoices.findByIdAndCompanyId(f.items().getLast().getInvoiceId(),f.companyId()).orElseThrow().cancel());
        var requester=actor(f,AccessAction.READ);grant(requester,AccessModule.INVOICES,AccessAction.REQUEST_CREATE);
        var approver=actor(f,AccessAction.READ);grant(approver,AccessModule.INVOICES,AccessAction.APPROVE);
        var pending=call(requester,post("/api/v1/company/invoices"),registrationBody(f),202);
        assertEquals(0,count("invoice_financial_snapshot",f));assertEquals(2,count("invoice",f));
        org.mockito.Mockito.doAnswer(invocation->{invocation.callRealMethod();throw new IllegalStateException("Synthetic snapshot failure");})
            .when(snapshotStorage).insert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),500);
        assertEquals(0,count("invoice_financial_snapshot",f));assertEquals(2,count("invoice",f));assertEquals(2,count("invoice_item",f));
        org.mockito.Mockito.reset(snapshotStorage);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals(1,count("invoice_financial_snapshot",f));assertEquals(3,count("invoice",f));
    }

    @Test void reportSeparatesCashWithholdingCreditsOpenTitlesAndFinancialPermissions() throws Exception {
        var f=fixture(false);reportPermissions(f);
        var cash=settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("20.00"),ReceivableSettlementPaymentMethod.PIX,null));
        settlements.save(ReceivableSettlement.reverse(cash,today(),"Estorno sintético"));
        settlements.save(ReceivableSettlement.confirm(f.companyId(),f.receivableId(),f.contractId(),today(),new BigDecimal("30.00"),ReceivableSettlementPaymentMethod.PIX,null));
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),f.receivableId(),"Retenção sintética",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Valor total faturado"));assertFalse(text.contains("Total recebido em dinheiro:"));
        }
        grant(f,AccessModule.RECEIVABLES,AccessAction.EXPORT);
        var extra=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"SEM-NF",null,today(),today().plusDays(30)));
        receivables.save(Receivable.create(f.companyId(),f.contractId(),extra.getId(),UUID.randomUUID(),"TITULO-SEM-NF",null,today(),today().plusDays(30),new BigDecimal("5.00")));
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertFalse(text.contains("Total recebido em dinheiro:"));assertFalse(text.contains("não recebido em dinheiro: 70,00"));
            assertTrue(text.contains("Saldo pendente: 60,00"));assertTrue(text.contains("TITULO-SEM-NF"));assertTrue(text.contains("Sem NF"));
        }
        revoke(f,AccessModule.RECEIVABLES,AccessAction.READ);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertFalse(text.contains("TITULO-SEM-NF"));assertFalse(text.contains("Total recebido em dinheiro:"));
        }
    }

    @Test void reportServiceNetBalancesMatchOfficialCapacityDistributionIncludingRounding() {
        var f=fixture(false);
        for(String amount:List.of("24.35","55.55","0.01")) services.save(ContractService.create(f.companyId(),f.contractId(),"Serviço adicional", "Descrição integral",ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal(amount)));
        reportDiscounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(f.companyId(),f.contractId(),com.renovar.canteiro.io.contracts.domain.DiscountType.FIXED,new BigDecimal("1.23")));
        var state=reportCapacity.calculate(f.companyId(),f.contractId());
        var rows=reportSource.servicePage(f.companyId(),f.contractId(),null,100,reportAmounts.calculate(f.companyId(),f.contractId()));
        for(var row:rows) assertEquals(0,row.commercialBalance().compareTo(state.availableForService(row.id())));
        assertEquals(0,rows.stream().map(r->r.commercialAmount()).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(state.contractNetAmount()));
        assertEquals(5,rows.size());
    }

    private Map<String,Object> registrationBody(F f) {
        return Map.of("contractId",f.contractId(),"number","NF-NOVA","issuedOn",today().minusMonths(2).toString(),
            "items",List.of(Map.of("billingAllocationId",f.items().getLast().getBillingAllocationId(),"allocatedAmount","40.00")));
    }

    @Test void reportPreservesCommittedNetDiscountAndPartialInvoiceWithoutSubtractingGrossCapacity() {
        var f=fixture(false);
        var service=services.save(ContractService.create(f.companyId(),f.contractId(),"Serviço líquido", "Descrição oficial",ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("100.00")));
        reportDiscounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(f.companyId(),f.contractId(),com.renovar.canteiro.io.contracts.domain.DiscountType.FIXED,new BigDecimal("10.00")));
        var installment=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"LIQUIDA",null,today(),today().plusDays(30)));
        var allocation=allocations.save(BillingAllocation.createNetService(f.companyId(),installment.getId(),f.contractId(),service.getId(),null,
            new BigDecimal("20.00"),new BigDecimal("25.00"),new BigDecimal("5.00")).activate());
        var invoice=invoices.save(Invoice.register(f.companyId(),f.contractId(),"NF-PARCIAL",null,null,today(),new BigDecimal("10.00")));
        invoiceItems.save(InvoiceItem.create(f.companyId(),invoice.getId(),f.contractId(),allocation.getId(),service.getId(),null,new BigDecimal("10.00")));
        var state=reportCapacity.calculate(f.companyId(),f.contractId());
        var rows=reportSource.servicePage(f.companyId(),f.contractId(),null,100,reportAmounts.calculate(f.companyId(),f.contractId()));
        var row=rows.stream().filter(r->r.id().equals(service.getId())).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("70.00").compareTo(state.availableForService(service.getId())));
        assertEquals(0,new BigDecimal("90.00").compareTo(row.commercialAmount()));
        assertEquals(0,new BigDecimal("10.00").compareTo(row.invoicedAmount()));
        assertEquals(0,new BigDecimal("80.00").compareTo(row.commercialBalance()));
        assertEquals(0,rows.stream().map(r->r.commercialAmount()).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(state.contractNetAmount()));
    }

    @Test void exportsContractBillingPdfWithPrivateLogoHeadersOfficialTotalsAndOneAudit() throws Exception {
        var f=fixture(true);reportPermissions(f);
        var image=new java.awt.image.BufferedImage(80,30,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,0,80,30);graphics.dispose();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        String key="companies/"+f.companyId()+"/logo-synthetic.png";
        reportStorage.store(key,bytes.toByteArray());jdbc.update("update company set logo=? where id=?",key,f.companyId());
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),f.receivableId(),"Retenção sintética",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        var before=snapshot(f,true);int events=count("audit_event",f);
        byte[] pdf=report(f);
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("CONTROLE DE FATURAMENTO"));assertTrue(text.contains("Empresa sintética"));
            assertTrue(text.contains("NF-0"));assertTrue(text.contains("NF-1"));assertTrue(text.contains("PARC"));
            assertTrue(text.contains("Valor total do contrato"));
            assertTrue(text.contains("Saldo total"));
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
        assertEquals(before,snapshot(f,true));assertEquals(events+1,count("audit_event",f));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where company_id=? and module='BILLING' and action='EXPORT' and actor_user_id=?",Integer.class,f.companyId(),f.userId()));
        reportStorage.delete(key);
    }

    @Test void billingPdfRequiresBothReadAndExportPermissionsAndPreservesTenant() throws Exception {
        var f=fixture(false);reportPermissions(f);
        for(var pair:List.of(new Object[]{AccessModule.BILLING,AccessAction.READ},new Object[]{AccessModule.BILLING,AccessAction.EXPORT},new Object[]{AccessModule.INVOICES,AccessAction.READ},new Object[]{AccessModule.INVOICES,AccessAction.EXPORT})) {
            var actor=actor(f,AccessAction.READ);reportPermissions(actor);revoke(actor,(AccessModule)pair[0],(AccessAction)pair[1]);
            call(actor,get(reportPath(f)),null,403);
        }
        var foreign=fixture(false);reportPermissions(foreign);
        call(foreign,get(reportPath(f)),null,404);
        call(f,get(reportPath(f).replace(f.contractId().toString(),UUID.randomUUID().toString())),null,404);
        mvc.perform(get(reportPath(f))).andExpect(status().isUnauthorized());
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_event where company_id=? and module='BILLING' and action='EXPORT'",Integer.class,f.companyId()));
    }

    @Test void billingPdfPreservesDiscountCancelledHistoryAndUninvoicedAllocation() throws Exception {
        var f=fixture(true);reportPermissions(f);
        reportDiscounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(f.companyId(),f.contractId(),com.renovar.canteiro.io.contracts.domain.DiscountType.FIXED,new BigDecimal("10.00")));
        var cancelled=invoices.findByIdAndCompanyId(f.items().getLast().getInvoiceId(),f.companyId()).orElseThrow();
        invoices.updateStatus(cancelled.cancel());
        try(var document=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("90,00"));
            assertTrue(text.contains("60,00"));
            assertTrue(text.contains("30,00"));
            assertTrue(text.contains("Cancelada"));assertTrue(text.contains("Indisponível"));
            assertFalse(text.contains("Fatura 2"));
        }
        var origin=allocations.findByIdAndCompanyId(f.items().getLast().getBillingAllocationId(),f.companyId()).orElseThrow();
        var rows=reportSource.servicePage(f.companyId(),f.contractId(),null,100,new com.renovar.canteiro.io.contracts.domain.ContractNetAmount(new BigDecimal("100.00"),new BigDecimal("10.00"),new BigDecimal("90.00")));
        assertEquals(null,rows.stream().filter(r->r.id().equals(origin.getContractServiceId())).findFirst().orElseThrow().commercialBalance());
    }

    @Test void billingPdfUsesOneSnapshotWhileConcurrentOfficialNamesChange() throws Exception {
        var f=fixture(false);reportPermissions(f);
        var changed=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation->{
            Object result=invocation.callRealMethod();
            if(changed.compareAndSet(false,true)) java.util.concurrent.CompletableFuture.runAsync(()->{
                jdbc.update("update invoice set number='NF-ALTERADA' where company_id=? and contract_id=?",f.companyId(),f.contractId());
                jdbc.update("update contract_service set name='NOME-ALTERADO' where company_id=? and contract_id=?",f.companyId(),f.contractId());
            }).get(10,TimeUnit.SECONDS);
            return result;
        }).when(reportSource).invoicePage(org.mockito.ArgumentMatchers.eq(f.companyId()),org.mockito.ArgumentMatchers.eq(f.contractId()),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyBoolean());
        try(var document=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(changed.get());assertTrue(text.contains("NF-0"));assertFalse(text.contains("NF-ALTERADA"));assertFalse(text.contains("NOME-ALTERADO"));
        }
        assertEquals("NF-ALTERADA",jdbc.queryForObject("select number from invoice where id=?",String.class,f.items().getFirst().getInvoiceId()));
    }

    @Test void billingPdfRejectsUnprovableInvoiceOriginBeforeStartingStream() throws Exception {
        var f=fixture(false);reportPermissions(f);
        jdbc.update("update invoice_item set contract_service_id=? where id=?",f.items().getLast().getContractServiceId(),f.items().getFirst().getId());
        var error=call(f,get(reportPath(f)),null,409);
        assertEquals("BILLING_REPORT_RECONCILIATION_REQUIRED",error.path("reason").asText());
        jdbc.update("update invoice_item set contract_service_id=? where id=?",f.items().getFirst().getContractServiceId(),f.items().getFirst().getId());
        jdbc.update("update billing_allocation set status='DRAFT' where id=?",f.items().getFirst().getBillingAllocationId());
        assertEquals("BILLING_REPORT_RECONCILIATION_REQUIRED",call(f,get(reportPath(f)),null,409).path("reason").asText());
    }

    private void reportPermissions(F f) {
        grant(f,AccessModule.BILLING,AccessAction.READ);grant(f,AccessModule.BILLING,AccessAction.EXPORT);grant(f,AccessModule.INVOICES,AccessAction.EXPORT);
    }

    @Test void billingPdfKeepsSeveralInstallmentReferencesForTheSameInvoice() throws Exception {
        var f=fixture(false);reportPermissions(f);
        var another=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"SEGUNDA-COBRANCA",null,today(),today().plusDays(30)));
        jdbc.update("update billing_allocation set billing_installment_id=? where id=?",another.getId(),f.items().getLast().getBillingAllocationId());
        try(var document=org.apache.pdfbox.Loader.loadPDF(report(f))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("PARC"));assertTrue(text.contains("SEGUNDA-COBRANCA"));
            assertTrue(text.contains("100,00"));
        }
    }

    @Test void billingPdfDoesNotRecordSuccessWhenRenderingFails() throws Exception {
        var f=fixture(false);reportPermissions(f);
        org.mockito.Mockito.doThrow(new java.io.IOException("Synthetic rendering failure")).when(reportGenerator)
            .write(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var pending=mvc.perform(get(reportPath(f)).with(jwt().jwt(t->t.subject(f.userId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        assertTrue(pending.getAsyncResult(10000) instanceof java.io.IOException);
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_event where company_id=? and module='BILLING' and action='EXPORT'",Integer.class,f.companyId()));
    }
    private String reportPath(F f){return "/api/v1/company/billing-control/contracts/"+f.contractId()+"/report/pdf";}
    private byte[] report(F f) throws Exception {
        var pending=mvc.perform(get(reportPath(f)).with(jwt().jwt(t->t.subject(f.userId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF)).andReturn().getResponse();
        assertEquals("no-store",response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline;"));assertTrue(response.getHeader("Content-Disposition").contains(".pdf"));
        return response.getContentAsByteArray();
    }

    @Test void returnsPagedOfficialOptionsWithoutMutatingAnyFinancialData() throws Exception {
        var f = fixture(false);
        var before = snapshot(f, false);
        var options = call(f, get(base(f) + "/invoice-settlement-options").param("size", "1"), null, 200);
        assertTrue(options.path("eligible").asBoolean()); assertEquals(today().toString(), options.path("maxReceivedOn").asText());
        assertEquals("100.00", options.path("outstandingAmount").asText());
        assertEquals(2, options.at("/items/totalItems").asInt()); assertEquals(1, options.at("/items/items").size());
        assertTrue(options.at("/items/items/0/maximumLineAmount").isString());
        assertEquals(before, snapshot(f, false));
        for (String[] invalid : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"}})
            call(f, get(base(f) + "/invoice-settlement-options").param(invalid[0], invalid[1]), null, 400);
    }

    @Test void proposesEntireTitleFromExistingOriginsIndependentlyOfOptionsPageWithoutWriting() throws Exception {
        var f = fixture(false);
        var before = snapshot(f, false);
        var first = call(f, get(base(f) + "/invoice-settlement-options").param("size", "1"), null, 200).path("totalSettlementProposal");
        var second = call(f, get(base(f) + "/invoice-settlement-options").param("page", "1").param("size", "1"), null, 200).path("totalSettlementProposal");
        assertEquals(first, second); assertTrue(first.path("available").asBoolean());
        assertEquals("100.00", first.path("amount").asText()); assertEquals(2, first.path("items").size());
        assertEquals(List.of("40.00", "60.00"), first.path("items").valueStream().map(item -> item.path("amount").asText()).sorted().toList());
        assertEquals(before, snapshot(f, false));
        var body = body(f, "60.00", "40.00"); body.put("items", json.convertValue(first.path("items"), List.class));
        call(f, post(base(f) + "/invoice-settlements"), body, 201);
        var unavailable = call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("totalSettlementProposal");
        assertFalse(unavailable.path("available").asBoolean()); assertTrue(unavailable.path("amount").isNull());
    }

    @Test void proposesRemainingExactOriginsAfterPartialReceiptWithoutRedistributingHistory() throws Exception {
        var f = fixture(true, true, false);
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        var before = snapshot(f, false);
        var proposal = call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("totalSettlementProposal");
        assertTrue(proposal.path("available").asBoolean()); assertEquals("50.00", proposal.path("amount").asText());
        assertEquals(List.of("20.00", "30.00"), proposal.path("items").valueStream().map(item -> item.path("amount").asText()).sorted().toList());
        assertTrue(proposal.path("items").valueStream().anyMatch(item -> !item.path("measurementContractCompositionId").isNull()));
        assertEquals(before, snapshot(f, false));
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 409);
    }

    @Test void proposesProportionalWithholdingPerExactItemAndPreservesExplicitReceiptReplay() throws Exception {
        for (boolean split : List.of(false, true)) {
            var f = fixture(split);
            retain(f, "10.00");
            var before = snapshot(f, false);
            var options = call(f, get(base(f) + "/invoice-settlement-options").param("size","1"), null, 200);
            var proposal = options.path("totalSettlementProposal");
            assertTrue(proposal.path("available").asBoolean());
            assertEquals("90.00", proposal.path("amount").asText());
            assertEquals(split ? List.of("40.00","50.00") : List.of("36.00","54.00"),
                    proposal.path("items").valueStream().map(item -> item.path("amount").asText()).sorted().toList());
            assertEquals(before, snapshot(f, false));
            var body = body(f,split ? "50.00" : "54.00",split ? "40.00" : "36.00");
            var receipt = call(f, post(base(f) + "/invoice-settlements"), body, 201);
            assertEquals("90.00",receipt.path("confirmedSettlementAmount").asText());
            assertEquals("0.00",receipt.path("outstandingAmount").asText());
            before=snapshot(f,false);
            assertTrue(call(f,post(base(f)+"/invoice-settlements"),body,200).path("replayed").asBoolean());
            assertEquals(before,snapshot(f,false));
        }
    }

    @Test void requiresInvoiceCoverageBeforeProposingTotal() throws Exception {
        var f = fixture(false);
        jdbc.update("update invoice set status='CANCELLED' where id=? and company_id=?", f.items().getFirst().getInvoiceId(), f.companyId());
        var before = snapshot(f, false);
        var proposal = call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("totalSettlementProposal");
        assertFalse(proposal.path("available").asBoolean());
        assertEquals("SETTLEMENT_TOTAL_INVOICE_COVERAGE_REQUIRED", proposal.path("unavailableReason").asText());
        assertTrue(proposal.path("amount").isNull()); assertTrue(proposal.path("items").isEmpty());
        assertEquals(before, snapshot(f, false));
    }

    @Test void preservesManualAmountsAcrossMultipleInvoicesAndReplaysOneReceiptOnly() throws Exception {
        var f = fixture(true);
        var body = body(f, "30.00", "20.00");
        body.put("note", "n".repeat(1000));
        var first = call(f, post(base(f) + "/invoice-settlements"), body, 201);
        assertEquals(1000, first.at("/settlement/note").asText().length());
        assertEquals("50.00", first.path("confirmedSettlementAmount").asText());
        assertEquals("50.00", first.path("outstandingAmount").asText()); assertEquals(2, first.path("items").size());
        assertEquals("30.00", cash(f, 0).path("receivedAmount").asText()); assertEquals("20.00", cash(f, 1).path("receivedAmount").asText());
        var before = snapshot(f, false);
        var repeated = call(f, post(base(f) + "/invoice-settlements"), body, 200);
        assertTrue(repeated.path("replayed").asBoolean()); assertEquals(first.at("/settlement/id"), repeated.at("/settlement/id"));
        assertEquals(before, snapshot(f, false));
        var conflict = new HashMap<>(body); conflict.put("note", "Different input");
        assertEquals("SETTLEMENT_OPERATION_CONFLICT", call(f, post(base(f) + "/invoice-settlements"), conflict, 409).path("reason").asText());
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        assertEquals("RECEIVED", cash(f, 0).path("status").asText()); assertEquals("RECEIVED", cash(f, 1).path("status").asText());
        assertEquals("0.00", cash(f, 0).path("outstandingAmount").asText());
        assertFalse(call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("eligible").asBoolean());
        assertEquals(2, count("receivable_settlement", f));
    }

    @Test void enforcesPermissionsAndTenantBeforeReadingOrWritingOrigins() throws Exception {
        var f = fixture(false); var foreign = fixture(false); retain(f,"10.00"); retain(foreign,"20.00");
        var readOnly = actor(f, AccessAction.READ);
        var noInvoiceRead = actor(f, AccessAction.CREATE_DIRECT); revoke(noInvoiceRead, AccessModule.INVOICES, AccessAction.READ);
        var noReceivableRead = actor(f, AccessAction.CREATE_DIRECT);
        var before = snapshot(f, false);
        call(readOnly, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 403);
        call(noInvoiceRead, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 403);
        call(noReceivableRead, get(base(f) + "/invoice-settlement-options"), null, 403);
        call(foreign, get(base(f) + "/invoice-settlement-options"), null, 404);
        call(foreign, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 404);
        var foreignLine = body(foreign, "30.00", "20.00");
        call(f, post(base(f) + "/invoice-settlements"), foreignLine, 404);
        assertEquals(before, snapshot(f, false));
        for (String path : List.of("/invoice-settlement-options", "/invoice-settlements"))
            mvc.perform(path.endsWith("options") ? get(base(f) + path) : post(base(f) + path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(f, "30.00", "20.00"))))
                    .andExpect(status().isUnauthorized());
    }

    @Test void rejectsNumericMoneyFutureDateDuplicatesAndWrongSumsWithoutAnyWrite() throws Exception {
        var f = fixture(false); var before = snapshot(f, false);
        var numeric = body(f, "30.00", "20.00"); numeric.put("amount", 50.0);
        call(f, post(base(f) + "/invoice-settlements"), numeric, 400);
        var future = body(f, "30.00", "20.00"); future.put("receivedOn", today().plusDays(1).toString());
        assertFalse(call(f, post(base(f) + "/invoice-settlements"), future, 400).path("violations").isMissingNode());
        var wrongSum = body(f, "30.00", "20.00"); wrongSum.put("amount", "99.00");
        call(f, post(base(f) + "/invoice-settlements"), wrongSum, 400);
        var duplicates = body(f, "30.00", "20.00"); duplicates.put("items", List.of(line(f, 0, "25.00"), line(f, 0, "25.00")));
        call(f, post(base(f) + "/invoice-settlements"), duplicates, 400);
        for (String amount : List.of("0.00", "-1.00", "1.001", "01.00", "100000000000000000.00")) {
            var invalid = body(f, "30.00", "20.00"); invalid.put("amount", amount);
            call(f, post(base(f) + "/invoice-settlements"), invalid, 400);
        }
        var tooMany = body(f, "30.00", "20.00"); tooMany.put("items", java.util.Collections.nCopies(101, line(f, 0, "1.00"))); tooMany.put("amount", "101.00");
        call(f, post(base(f) + "/invoice-settlements"), tooMany, 400);
        var longNote = body(f, "30.00", "20.00"); longNote.put("note", "n".repeat(1001));
        call(f, post(base(f) + "/invoice-settlements"), longNote, 400);
        assertEquals(before, snapshot(f, false));
    }

    @Test void rejectsChangedExactOriginAndExcessAtTitleItemAndInvoiceLevels() throws Exception {
        var f = fixture(false); var before = snapshot(f, false);
        var wrongOrigin = body(f, "30.00", "20.00"); var wrong = new HashMap<>(line(f, 0, "30.00")); wrong.put("measurementContractCompositionId", UUID.randomUUID());
        wrongOrigin.put("items", List.of(wrong, line(f, 1, "20.00")));
        call(f, post(base(f) + "/invoice-settlements"), wrongOrigin, 404);
        var wrongAllocation = new HashMap<>(wrong); wrongAllocation.put("measurementContractCompositionId", null); wrongAllocation.put("billingAllocationId", f.items().get(1).getBillingAllocationId());
        wrongOrigin.put("items", List.of(wrongAllocation, line(f, 1, "20.00")));
        call(f, post(base(f) + "/invoice-settlements"), wrongOrigin, 404);
        assertEquals("SETTLEMENT_AMOUNT_EXCEEDS_TITLE", call(f, post(base(f) + "/invoice-settlements"), body(f, "61.00", "40.00"), 409).path("reason").asText());
        assertEquals("SETTLEMENT_AMOUNT_EXCEEDS_ITEM", call(f, post(base(f) + "/invoice-settlements"), body(f, "61.00", "1.00"), 409).path("reason").asText());
        assertEquals(before, snapshot(f, false));
        var inconsistent = fixture(false);
        jdbc.update("update invoice_item set contract_service_id=? where id=?", inconsistent.items().get(1).getContractServiceId(), inconsistent.items().getFirst().getId());
        var inconsistentBefore = snapshot(inconsistent, false);
        call(inconsistent, post(base(inconsistent) + "/invoice-settlements"), body(inconsistent, "30.00", "20.00"), 404);
        assertEquals(inconsistentBefore, snapshot(inconsistent, false));
    }

    @Test void appliesProportionalWithholdingWithoutAutomaticallyDistributingPartialCash() throws Exception {
        var f = fixture(false);
        taxes.save(TaxObligation.register(f.companyId(), f.items().getFirst().getInvoiceId(), f.contractId(), f.receivableId(), "Retenção explícita", null, null, new BigDecimal("10.00"), TaxTreatment.WITHHELD_BY_CUSTOMER));
        var options = call(f, get(base(f) + "/invoice-settlement-options"), null, 200);
        assertEquals("90.00", options.path("outstandingAmount").asText());
        var before = snapshot(f,false);
        assertEquals("SETTLEMENT_AMOUNT_EXCEEDS_ITEM",call(f,post(base(f)+"/invoice-settlements"),body(f,"60.00","30.00"),409).path("reason").asText());
        assertEquals(before,snapshot(f,false));
        var receipt = call(f, post(base(f) + "/invoice-settlements"), body(f, "54.00", "36.00"), 201);
        assertEquals("0.00", receipt.path("outstandingAmount").asText());
        var amounts = receipt.path("items").valueStream().map(row -> row.path("amount").asText()).sorted().toList();
        assertEquals(List.of("36.00", "54.00"), amounts);
        assertEquals("90.00", cash(f, 0).path("receivedAmount").asText());
        assertEquals("RECEIVED", cash(f, 0).path("status").asText());
        var split = fixture(true);
        taxes.save(TaxObligation.register(split.companyId(), split.items().getFirst().getInvoiceId(), split.contractId(), split.receivableId(), "Retenção explícita NF1", null, null, new BigDecimal("10.00"), TaxTreatment.WITHHELD_BY_CUSTOMER));
        before = snapshot(split, false);
        assertEquals("SETTLEMENT_AMOUNT_EXCEEDS_ITEM", call(split, post(base(split) + "/invoice-settlements"), body(split, "55.00", "1.00"), 409).path("reason").asText());
        assertEquals(before, snapshot(split, false));
    }

    @Test void exposesOfficialNetItemAmountsAndRemainingPartialCashThenRestoresOnReversal() throws Exception {
        var f=fixture(false);retain(f,"10.00");grant(f,AccessModule.RECEIVABLES,AccessAction.CANCEL_DIRECT);
        var receipt=call(f,post(base(f)+"/invoice-settlements"),body(f,"30.00","10.00"),201);
        var options=call(f,get(base(f)+"/invoice-settlement-options"),null,200);
        var first=option(options,f.items().getFirst().getId());
        assertEquals("60.00",first.path("invoicedItemAmount").asText());
        assertEquals("6.00",first.path("withheldItemAmount").asText());
        assertEquals("54.00",first.path("netInvoicedItemAmount").asText());
        assertEquals("24.00",first.path("netAvailableItemAmount").asText());
        assertEquals("24.00",first.path("maximumLineAmount").asText());
        assertEquals("26.00",option(options,f.items().get(1).getId()).path("netAvailableItemAmount").asText());
        assertEquals("50.00",options.at("/totalSettlementProposal/amount").asText());
        assertEquals(List.of("24.00","26.00"),options.at("/totalSettlementProposal/items").valueStream().map(row->row.path("amount").asText()).sorted().toList());
        call(f,post(base(f)+"/settlements/"+receipt.at("/settlement/id").asText()+"/reversal"),Map.of("reason","Teste sintético de estorno"),201);
        options=call(f,get(base(f)+"/invoice-settlement-options"),null,200);
        assertEquals("54.00",option(options,f.items().getFirst().getId()).path("netAvailableItemAmount").asText());
        assertEquals("90.00",options.at("/totalSettlementProposal/amount").asText());
    }

    @Test void approvalRechecksProportionalNetItemLimitAfterNewWithholdingWithoutOfficialPartialChanges() throws Exception {
        var f=fixture(false);var requester=actor(f,AccessAction.REQUEST_CREATE);var approver=actor(f,AccessAction.APPROVE);
        var before=snapshot(f,true);
        var pending=call(requester,post(base(f)+"/invoice-settlements"),body(f,"60.00","20.00"),202);
        assertEquals(before,snapshot(f,true));
        retain(f,"10.00");before=snapshot(f,false);
        var error=call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertEquals("SETTLEMENT_AMOUNT_EXCEEDS_ITEM",error.path("reason").asText());
        assertEquals(before,snapshot(f,false));
    }

    @Test void incompatibleHistoricalCashRemainsImmutableAndRequiresReconciliation() throws Exception {
        var f=fixture(false);
        call(f,post(base(f)+"/invoice-settlements"),body(f,"60.00","20.00"),201);
        retain(f,"10.00");var before=snapshot(f,false);
        var options=call(f,get(base(f)+"/invoice-settlement-options"),null,200);
        String reason="SETTLEMENT_WITHHOLDING_HISTORY_REQUIRES_RECONCILIATION";
        assertEquals(reason,options.at("/totalSettlementProposal/unavailableReason").asText());
        for(var row:options.at("/items/items")) {
            assertFalse(row.path("eligible").asBoolean());assertEquals(reason,row.path("unavailableReason").asText());
            for(String field:List.of("maximumLineAmount","withheldItemAmount","netInvoicedItemAmount","netAvailableItemAmount")) assertTrue(row.path(field).isNull());
        }
        assertEquals(reason,call(f,post(base(f)+"/invoice-settlements"),body(f,"1.00","1.00"),409).path("reason").asText());
        assertEquals(before,snapshot(f,false));
    }

    @Test void withholdingCannotBeRedistributedAcrossBillingTitlesOrIncompleteInvoiceBasis() throws Exception {
        var f=fixture(false);
        var installment=installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"Outra cobrança",null,today(),today().plusDays(30)));
        var other=receivables.save(Receivable.create(f.companyId(),f.contractId(),installment.getId(),UUID.randomUUID(),"OUTRA",null,today(),today().plusDays(30),new BigDecimal("100.00")));
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),other.getId(),"Retenção outro título",null,null,new BigDecimal("10.00"),TaxTreatment.WITHHELD_BY_CUSTOMER));
        var before=snapshot(f,false);
        var options=call(f,get(base(f)+"/invoice-settlement-options"),null,200);
        assertEquals("SETTLEMENT_WITHHOLDING_SCOPE_REQUIRES_RECONCILIATION",options.at("/totalSettlementProposal/unavailableReason").asText());
        assertEquals("SETTLEMENT_WITHHOLDING_SCOPE_REQUIRES_RECONCILIATION",call(f,post(base(f)+"/invoice-settlements"),body(f,"1.00","1.00"),409).path("reason").asText());
        assertEquals(before,snapshot(f,false));
        var incomplete=fixture(false);retain(incomplete,"10.00");
        jdbc.update("update invoice set total_amount=101.00 where id=? and company_id=?",incomplete.items().getFirst().getInvoiceId(),incomplete.companyId());
        options=call(incomplete,get(base(incomplete)+"/invoice-settlement-options"),null,200);
        assertEquals("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION",options.at("/totalSettlementProposal/unavailableReason").asText());
    }

    private void retain(F f,String amount) {
        taxes.save(TaxObligation.register(f.companyId(),f.items().getFirst().getInvoiceId(),f.contractId(),f.receivableId(),"Retenção sintética",null,null,new BigDecimal(amount),TaxTreatment.WITHHELD_BY_CUSTOMER));
    }
    private JsonNode option(JsonNode options,UUID item) {
        return options.at("/items/items").valueStream().filter(row->row.path("invoiceItemId").asText().equals(item.toString())).findFirst().orElseThrow();
    }

    @Test void pendingIntentIsIdempotentAndDoesNotChangeAnyOfficialVersionOrBalance() throws Exception {
        var f = fixture(false); var requester = actor(f, AccessAction.REQUEST_CREATE); var approver = actor(f, AccessAction.APPROVE);
        var before = snapshot(f, true); var body = body(f, "30.00", "20.00");
        var pending = call(requester, post(base(f) + "/invoice-settlements"), body, 202);
        assertEquals(3, pending.size()); assertFalse(pending.has("settlement")); assertFalse(pending.has("balance"));
        var repeated = call(requester, post(base(f) + "/invoice-settlements"), body, 202);
        assertEquals(pending.path("changeRequestId"), repeated.path("changeRequestId")); assertTrue(repeated.path("replayed").asBoolean());
        assertEquals(before, snapshot(f, true)); assertEquals(0, count("invoice_settlement_line", f)); assertEquals(1, count("change_request", f));
        call(approver, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 200);
        assertEquals(1, count("receivable_settlement", f)); assertEquals(2, count("invoice_settlement_line", f));
        var replay = call(requester, post(base(f) + "/invoice-settlements"), body, 200); assertTrue(replay.path("replayed").asBoolean());
        assertEquals("50.00", replay.path("outstandingAmount").asText());
    }

    @Test void approvalRechecksCapacityAndRollsBackWhenAnotherReceiptConsumesIt() throws Exception {
        var f = fixture(false); var requester = actor(f, AccessAction.REQUEST_CREATE); var approver = actor(f, AccessAction.APPROVE);
        var pending = call(requester, post(base(f) + "/invoice-settlements"), body(f, "40.00", "30.00"), 202);
        call(f, post(base(f) + "/invoice-settlements"), body(f, "40.00", "30.00"), 201);
        var before = snapshot(f, false);
        call(approver, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 409);
        assertEquals(before, snapshot(f, false));
    }

    @Test void reversesOnlyTheExactOriginalLinesAndNeverRecreatesReceiptOnReplay() throws Exception {
        var f = fixture(true); grant(f, AccessModule.RECEIVABLES, AccessAction.CANCEL_DIRECT);
        var body = body(f, "30.00", "20.00"); var receipt = call(f, post(base(f) + "/invoice-settlements"), body, 201);
        String id = receipt.at("/settlement/id").asText();
        call(f, post(base(f) + "/settlements/" + id + "/reversal"), Map.of("reason", "Correção sintética"), 201);
        assertEquals("0.00", cash(f, 0).path("receivedAmount").asText()); assertEquals("0.00", cash(f, 1).path("receivedAmount").asText());
        assertEquals(4, count("invoice_settlement_line", f));
        assertEquals(2, jdbc.queryForObject("select count(*) from invoice_settlement_line where company_id=? and reversal_of_line_id is not null and amount<0", Integer.class, f.companyId()));
        var before = snapshot(f, false);
        call(f, post(base(f) + "/invoice-settlements"), body, 200);
        assertEquals(before, snapshot(f, false));
        call(f, post(base(f) + "/settlements/" + id + "/reversal"), Map.of("reason", "Repetido"), 409);
    }

    @Test void preservesLegacyCashAsUnavailableUntilAnExactHistoricalReversalRemovesItsEffect() throws Exception {
        var f = fixture(false); grant(f, AccessModule.RECEIVABLES, AccessAction.CANCEL_DIRECT);
        var old = settlements.save(ReceivableSettlement.confirm(f.companyId(), f.receivableId(), f.contractId(), today(), new BigDecimal("20.00"), ReceivableSettlementPaymentMethod.PIX, "Histórico sem NF"));
        var before = snapshot(f, false);
        assertEquals("SETTLEMENT_HISTORY_REQUIRES_RECONCILIATION", call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("unavailableReason").asText());
        assertFalse(cash(f, 0).path("available").asBoolean()); assertTrue(cash(f, 0).path("receivedAmount").isNull());
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 409); assertEquals(before, snapshot(f, false));
        call(f, post(base(f) + "/settlements/" + old.getId() + "/reversal"), Map.of("reason", "Estorno histórico autorizado sintético"), 201);
        assertEquals(0, count("invoice_settlement_line", f));
        call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        assertEquals(2, count("invoice_settlement_line", f));
    }

    @Test void preventsConcurrentExcessAndDuplicateIntent() throws Exception {
        var f = fixture(false);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return rawPost(f, body(f, "40.00", "30.00")); });
            var b = pool.submit(() -> { gate.await(); return rawPost(f, body(f, "40.00", "30.00")); });
            gate.countDown(); var statuses = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(201, 409), statuses); assertEquals(1, count("receivable_settlement", f));
        }
        var second = fixture(false); var body = body(second, "30.00", "20.00");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return rawPost(second, body); });
            var b = pool.submit(() -> { gate.await(); return rawPost(second, body); });
            gate.countDown(); assertEquals(List.of(200, 201), List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1, count("receivable_settlement", second)); assertEquals(1, count("invoice_settlement_operation", second));
    }

    @Test void ledgerCannotBeChangedOrDeletedAndLegacyCreationRemainsBlocked() throws Exception {
        var f = fixture(false); call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        assertThrows(DataAccessException.class, () -> jdbc.update("update invoice_settlement_line set amount=1 where company_id=?", f.companyId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from invoice_settlement_line where company_id=?", f.companyId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from invoice_settlement_operation where company_id=?", f.companyId()));
        var before = snapshot(f, false);
        assertEquals("SETTLEMENT_INVOICE_LINK_REQUIRED", call(f, post(base(f) + "/settlements"), Map.of("receivedOn", today().toString(), "amount", "1.00", "paymentMethod", "PIX"), 409).path("reason").asText());
        assertEquals(before, snapshot(f, false));
    }

    @Test void groupsManualLinesOfTheSameAllocationWithoutLosingEitherInvoiceLink() throws Exception {
        var f = fixture(true, false, true);
        var result = call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        assertEquals(2, result.path("items").size());
        assertEquals(1, count("receivable_settlement_allocation", f));
        assertEquals(new BigDecimal("50.00"), jdbc.queryForObject("select amount from receivable_settlement_allocation where company_id=?", BigDecimal.class, f.companyId()));
        assertEquals("30.00", cash(f, 0).path("receivedAmount").asText()); assertEquals("20.00", cash(f, 1).path("receivedAmount").asText());
    }

    @Test void blocksCreditsRenegotiationsAndUnexplainedHistoricalPaidStateWithoutInventingInvoiceOwnership() throws Exception {
        var credited = fixture(false);
        jdbc.update("insert into receivable_credit(id,company_id,receivable_id,contract_id,amount,reason,actor_user_id) values(?,?,?,?,?,?,?)",
                UUID.randomUUID(), credited.companyId(), credited.receivableId(), credited.contractId(), new BigDecimal("10.00"), "Crédito sem vínculo de NF", credited.userId());
        var renegotiated = fixture(false);
        var original = receivables.findByIdAndCompanyId(renegotiated.receivableId(), renegotiated.companyId()).orElseThrow();
        receivables.updateStatus(original.withStatus(com.renovar.canteiro.io.receivables.domain.ReceivableStatus.RENEGOTIATED));
        receivables.saveRenegotiated(Receivable.create(renegotiated.companyId(), renegotiated.contractId(), original.getBillingInstallmentId(), original.getFinancialGroupId(),
                "Filho", null, today(), today().plusDays(30), new BigDecimal("100.00")), original.getId());
        var unexplained = fixture(false);
        jdbc.update("update receivable set status='RECEIVED' where id=?", unexplained.receivableId());
        for (var f : List.of(credited, renegotiated, unexplained)) {
            var before = snapshot(f, false);
            assertFalse(call(f, get(base(f) + "/invoice-settlement-options"), null, 200).path("eligible").asBoolean());
            call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 409);
            var position = cash(f, 0); assertFalse(position.path("available").asBoolean()); assertTrue(position.path("receivedAmount").isNull());
            assertEquals(before, snapshot(f, false));
        }
    }

    @Test void rejectsSelfApprovalAndApprovalWithoutInvoiceReadPermission() throws Exception {
        var f = fixture(false); var requester = actor(f, AccessAction.REQUEST_CREATE); var approver = actor(f, AccessAction.APPROVE);
        var pending = call(requester, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 202);
        grant(requester, AccessModule.RECEIVABLES, AccessAction.APPROVE); revoke(approver, AccessModule.INVOICES, AccessAction.READ);
        var before = snapshot(f, false);
        for (var actor : List.of(requester, approver)) call(actor, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 403);
        assertEquals(before, snapshot(f, false)); assertEquals(0, count("receivable_settlement", f));
    }

    @Test void pendingReceiptDoesNotPreventDocumentaryCancellationAndApprovalThenFailsAtomically() throws Exception {
        var f = fixture(false); grant(f, AccessModule.INVOICES, AccessAction.CANCEL_DIRECT);
        var requester = actor(f, AccessAction.REQUEST_CREATE); var approver = actor(f, AccessAction.APPROVE);
        var pending = call(requester, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 202);
        call(f, post("/api/v1/company/invoices/" + f.items().getFirst().getInvoiceId() + "/cancellation"), Map.of("reason", "Cancelamento sintético"), 200);
        var before = snapshot(f, false);
        var problem = call(approver, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 409);
        assertEquals("SETTLEMENT_INVOICE_NOT_REGISTERED", problem.path("reason").asText()); assertEquals(before, snapshot(f, false));
    }

    @Test void preservesAcceptedMeasurementCompositionThroughReceiptAndReversal() throws Exception {
        var f = fixture(true, true, false); grant(f, AccessModule.RECEIVABLES, AccessAction.CANCEL_DIRECT);
        var origin = allocations.findByIdAndCompanyId(f.items().getFirst().getBillingAllocationId(), f.companyId()).orElseThrow().getMeasurementContractCompositionId();
        assertTrue(origin != null);
        var result = call(f, post(base(f) + "/invoice-settlements"), body(f, "30.00", "20.00"), 201);
        assertEquals(1, jdbc.queryForObject("select count(*) from invoice_settlement_line where company_id=? and measurement_contract_composition_id=? and amount=30", Integer.class, f.companyId(), origin));
        call(f, post(base(f) + "/settlements/" + result.at("/settlement/id").asText() + "/reversal"), Map.of("reason", "Estorno sintético"), 201);
        assertEquals(1, jdbc.queryForObject("select count(*) from invoice_settlement_line where company_id=? and measurement_contract_composition_id=? and amount=-30 and reversal_of_line_id is not null", Integer.class, f.companyId(), origin));
        assertEquals(new BigDecimal("0.00"), jdbc.queryForObject("select sum(amount) from receivable_settlement_allocation where company_id=? and billing_allocation_id=?", BigDecimal.class, f.companyId(), f.items().getFirst().getBillingAllocationId()));
    }

    private JsonNode cash(F f, int index) throws Exception {
        return call(f, get("/api/v1/company/invoices/" + f.items().get(index).getInvoiceId()), null, 200).at("/invoice/receiptPosition");
    }
    private int rawPost(F f, Map<String, Object> body) throws Exception {
        return mvc.perform(post(base(f) + "/invoice-settlements").with(jwt().jwt(t -> t.subject(f.userId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }
    private JsonNode call(F f, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        request.with(jwt().jwt(t -> t.subject(f.userId().toString())));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result = mvc.perform(request).andExpect(status().is(expected));
        if (expected >= 400) result.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private Map<String, Object> body(F f, String... amounts) {
        var result = new HashMap<String, Object>(); result.put("operationId", UUID.randomUUID()); result.put("receivedOn", today().toString()); result.put("paymentMethod", "PIX");
        result.put("amount", Arrays.stream(amounts).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2).toPlainString());
        var lines = new ArrayList<Map<String, Object>>(); for (int i = 0; i < amounts.length; i++) lines.add(line(f, i, amounts[i])); result.put("items", lines); return result;
    }
    private Map<String, Object> line(F f, int index, String amount) {
        var item = f.items().get(index); var result = new HashMap<String, Object>(); result.put("invoiceId", item.getInvoiceId()); result.put("invoiceItemId", item.getId());
        result.put("billingAllocationId", item.getBillingAllocationId()); result.put("measurementContractCompositionId", allocations.findByIdAndCompanyId(item.getBillingAllocationId(), f.companyId()).orElseThrow().getMeasurementContractCompositionId()); result.put("amount", amount); return result;
    }
    private String base(F f) { return "/api/v1/company/receivables/" + f.receivableId(); }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneOffset.UTC)); }
    private int count(String table, F f) { return jdbc.queryForObject("select count(*) from " + table + " where company_id=?", Integer.class, f.companyId()); }
    private List<Map<String, Object>> snapshot(F f, boolean officialOnly) {
        var tables = new ArrayList<>(List.of("contract", "billing_installment", "billing_allocation", "invoice", "invoice_item", "receivable", "receivable_settlement", "receivable_settlement_allocation", "invoice_settlement_line", "tax_obligation"));
        if (!officialOnly) tables.addAll(List.of("invoice_settlement_operation", "audit_event", "change_request"));
        var rows = new ArrayList<Map<String, Object>>();
        for (String table : tables) rows.addAll(jdbc.queryForList("select '" + table + "' kind,to_jsonb(t)::text data from " + table + " t where company_id=? order by to_jsonb(t)::text", f.companyId()));
        return rows;
    }
    private void grant(F f, AccessModule module, AccessAction action) { rolePermissions.save(RolePermission.create(f.roleId(), permissions.findByModuleAndAction(module, action).orElseThrow().getId())); }
    private void revoke(F f, AccessModule module, AccessAction action) {
        jdbc.update("update role_permission set active=false where role_id=? and permission_id=?", f.roleId(), permissions.findByModuleAndAction(module, action).orElseThrow().getId());
    }
    private F actor(F f, AccessAction... actions) {
        String suffix = UUID.randomUUID().toString(); var user = User.create(suffix + "@example.test", UserType.COMPANY); user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), f.companyId())); var role = roles.save(Role.create(f.companyId(), suffix, null)); userRoles.save(UserRole.create(user.getId(), role.getId(), f.companyId()));
        var actor = new F(f.companyId(), user.getId(), role.getId(), f.contractId(), f.receivableId(), f.items()); grant(actor, AccessModule.INVOICES, AccessAction.READ);
        for (var action : actions) grant(actor, AccessModule.RECEIVABLES, action); return actor;
    }
    private F fixture(boolean splitInvoices) {
        return fixture(splitInvoices, false, false);
    }
    private F fixture(boolean splitInvoices, boolean composition, boolean sameAllocation) {
        return fixture(splitInvoices, composition, sameAllocation, false);
    }
    private F fixture(boolean splitInvoices, boolean composition, boolean sameAllocation, boolean headerDiscount) {
        return fixture(splitInvoices, composition, sameAllocation, headerDiscount, false);
    }
    private F fixture(boolean splitInvoices, boolean composition, boolean sameAllocation, boolean headerDiscount, boolean serviceDiscount) {
        BigDecimal total = new BigDecimal("100.00").multiply(new BigDecimal(headerDiscount ? "0.90" : "1.00")).multiply(new BigDecimal(serviceDiscount ? "0.90" : "1.00")).setScale(2);
        String suffix = UUID.randomUUID().toString(); var company = companies.save(Company.create("Empresa sintética", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Cliente", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Obra", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "Manual", ContractPricingMode.FIXED, "REF", "Contrato", ContractStatus.ACTIVE, null, null, null, CurrencyCode.BRL));
        var installment = installments.save(BillingInstallment.createApproved(company.getId(), contract.getId(), "PARC", null, today().minusDays(1), today().plusDays(30)));
        var title = receivables.save(Receivable.create(company.getId(), contract.getId(), installment.getId(), UUID.randomUUID(), "REC", null, today().minusDays(1), today().plusDays(30), total));
        if (headerDiscount) reportDiscounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(company.getId(),contract.getId(),com.renovar.canteiro.io.contracts.domain.DiscountType.PERCENTAGE,new BigDecimal("10.0000")));
        var items = new ArrayList<InvoiceItem>(); Invoice invoice = null; BillingAllocation sharedAllocation = null;
        for (int index = 0; index < 2; index++) {
            var amount = new BigDecimal(index == 0 ? "60.00" : "40.00");
            BigDecimal serviceNet = serviceDiscount ? amount.multiply(new BigDecimal("0.90")).setScale(2) : amount;
            BigDecimal netAmount = headerDiscount ? serviceNet.multiply(new BigDecimal("0.90")).setScale(2) : serviceNet;
            BillingAllocation allocation = sharedAllocation;
            if (allocation == null || !sameAllocation) {
                var allocationAmount = sameAllocation ? new BigDecimal("100.00") : amount;
                var service = services.save(ContractService.create(company.getId(), contract.getId(), "Serviço " + index, null, ContractServiceStatus.ACTIVE, BigDecimal.ONE, allocationAmount, serviceDiscount ? com.renovar.canteiro.io.contracts.domain.DiscountType.PERCENTAGE : null, serviceDiscount ? new BigDecimal("10.0000") : null));
                UUID compositionId = composition && index == 0 ? composition(company.getId(), work.getId(), contract.getId(), service.getId(), amount) : null;
                allocation = allocations.save((headerDiscount || serviceDiscount ? BillingAllocation.createNetService(company.getId(), installment.getId(), contract.getId(), service.getId(), compositionId, netAmount, serviceNet, serviceNet.subtract(netAmount))
                        : BillingAllocation.createService(company.getId(), installment.getId(), contract.getId(), service.getId(), compositionId, allocationAmount)).activate());
                sharedAllocation = allocation;
            }
            if (invoice == null || splitInvoices) invoice = invoices.save(Invoice.register(company.getId(), contract.getId(), "NF-" + index, null, null, today().minusDays(1), splitInvoices ? netAmount : total));
            items.add(invoiceItems.save(InvoiceItem.create(company.getId(), invoice.getId(), contract.getId(), allocation.getId(), allocation.getContractServiceId(), "Serviço " + index, netAmount)));
        }
        var f = actor(new F(company.getId(), null, null, contract.getId(), title.getId(), items), AccessAction.READ, AccessAction.CREATE_DIRECT);
        return f;
    }
    private UUID composition(UUID company, UUID work, UUID contract, UUID service, BigDecimal amount) {
        var measurement = measurements.save(com.renovar.canteiro.io.measurements.domain.Measurement.create(company, work, contract, "Origem sintética", "MED", null, today()));
        var version = measurementVersions.save(com.renovar.canteiro.io.measurements.domain.MeasurementVersion.create(company, measurement.getId(), 1));
        var item = measurementItems.save(com.renovar.canteiro.io.measurements.domain.MeasurementItem.createSquareMeter(company, version.getId(), 1, "Serviço medido", "Atividade", null, today(), BigDecimal.ONE, amount));
        var group = measurementServices.save(com.renovar.canteiro.io.measurements.domain.MeasurementService.create(company, version.getId(), "Serviço medido", 1));
        version.markSent(); version.markPendingAcceptance(); version.recordExternalAcceptance(true, today(), null); measurementVersions.save(version);
        return compositions.save(com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition.create(company, measurement.getId(), version.getId(), group.getId(), contract, service,
                com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType.CONTRACT_ADDITION, amount, amount, null, List.of(item.getId()), null, null)).getId();
    }
    private record F(UUID companyId, UUID userId, UUID roleId, UUID contractId, UUID receivableId, List<InvoiceItem> items) { }
}
