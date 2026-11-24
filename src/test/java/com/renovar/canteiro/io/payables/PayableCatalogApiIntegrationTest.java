package com.renovar.canteiro.io.payables;

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
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PayableCatalogApiIntegrationTest extends AbstractPostgresIntegrationTest {
    static final String SUPPLIERS="/api/v1/company/suppliers",CATEGORIES="/api/v1/company/financial-categories";
    @Autowired MockMvc mvc;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired CompanyUserRepository memberships;
    @Autowired PasswordHasher passwords;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @Autowired UserRoleRepository userRoles;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json=new ObjectMapper();

    @Test void supplierCrudNormalizesDocumentPreservesInactiveHistoryAndSafeAuditSnapshots()throws Exception{
        var f=owner();var created=call(f,post(SUPPLIERS),Map.of("name","Fornecedor aço","document","ab-12.345/6"),201).path("supplier");
        String id=created.path("id").asText();assertEquals("AB123456",created.path("document").asText());assertEquals(0,created.path("version").asInt());
        var updated=call(f,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","Fornecedor renomeado","document","ZX-99"),200).path("supplier");
        assertEquals(id,updated.path("id").asText());assertEquals(1,updated.path("version").asInt());
        assertEquals("Fornecedor renomeado",call(f,get(SUPPLIERS+"/"+id),null,200).path("name").asText());
        call(f,delete(SUPPLIERS+"/"+id).param("expectedVersion","1"),null,204);
        assertFalse(call(f,get(SUPPLIERS+"/"+id),null,200).path("active").asBoolean());
        assertEquals(0,call(f,get(SUPPLIERS),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(SUPPLIERS).param("active","false"),null,200).path("totalItems").asInt());
        assertEquals(1,jdbc.queryForObject("select count(*) from supplier where id=?",Integer.class,UUID.fromString(id)));
        String audit=jdbc.queryForObject("select string_agg(coalesce(before_data::text,'')||coalesce(after_data::text,'')||coalesce(metadata::text,''),'') from audit_event where company_id=? and entity_type='Supplier'",String.class,f.company());
        assertFalse(audit.contains("AB123456"));assertFalse(audit.contains("ZX99"));assertTrue(audit.contains("hasDocument"));
        String presentation=jdbc.queryForObject("select entity_display_name from audit_event where company_id=? and entity_id=? and action='CREATE'",String.class,f.company(),UUID.fromString(id));
        assertEquals("Fornecedor aço",presentation);
    }

    @Test void categoryCrudKeepsCodeAndClassificationImmutableAndNeverCreatesPayables()throws Exception{
        var f=owner();long before=jdbc.queryForObject("select count(*) from payable where company_id=?",Long.class,f.company());
        var c=category(f,"epi","Proteção","WORK_COST",201).path("category");String id=c.path("id").asText();assertEquals("EPI",c.path("code").asText());
        var edited=call(f,put(CATEGORIES+"/"+id),Map.of("expectedVersion",0,"name","Equipamentos de proteção","description","Classificação administrativa","type","COMPANY_EXPENSE","code","OTHER"),200).path("category");
        assertEquals("EPI",edited.path("code").asText());assertEquals("WORK_COST",edited.path("type").asText());
        call(f,delete(CATEGORIES+"/"+id).param("expectedVersion","1"),null,204);
        assertFalse(call(f,get(CATEGORIES+"/"+id),null,200).path("active").asBoolean());category(f,"epi","Outro","TAX",409);
        assertEquals(before,jdbc.queryForObject("select count(*) from payable where company_id=?",Long.class,f.company()));
        category(owner(),"epi","Outra empresa","TAX",201);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update financial_category set category_type='TAX' where id=?",UUID.fromString(id)));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from financial_category where id=?",UUID.fromString(id)));
    }

    @Test void enforcesSeparatePermissionsAuthenticationAndTenantForEveryCatalogOperation()throws Exception{
        var f=owner();var other=owner();var denied=actor(f.company(),Map.of());
        String supplier=call(f,post(SUPPLIERS),Map.of("name","Próprio"),201).at("/supplier/id").asText();
        String category=category(f,"TAX","Imposto","TAX",201).at("/category/id").asText();
        for(String route:List.of(SUPPLIERS,CATEGORIES)){mvc.perform(get(route)).andExpect(status().isUnauthorized());assertFalse(call(denied,get(route),null,403).path("correlationId").asText().isBlank());}
        call(denied,post(SUPPLIERS),Map.of("name","Não permitido"),403);category(denied,"X","Não permitido","TAX",403);
        call(actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ))),get(CATEGORIES),null,403);
        call(actor(f.company(),Map.of(AccessModule.COSTS_EXPENSES,List.of(AccessAction.READ))),get(SUPPLIERS),null,403);
        for(var pair:List.of(List.of(SUPPLIERS,supplier),List.of(CATEGORIES,category))){
            String detail=pair.get(0)+"/"+pair.get(1);call(other,get(detail),null,404);
            call(other,put(detail),Map.of("expectedVersion",0,"name","Ataque"),404);call(other,delete(detail).param("expectedVersion","0"),null,404);
            assertEquals(0,call(other,get(pair.get(0)),null,200).path("totalItems").asInt());
        }
        call(f,get(SUPPLIERS).param("companyId",other.company().toString()),null,200);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from supplier where id=?",UUID.fromString(supplier)));
    }

    @Test void validatesLiteralFiltersPagingSortsAndInputWithoutLeakingDocumentsInErrors()throws Exception{
        var f=owner();for(String name:List.of("A%_","Beta","Gama"))call(f,post(SUPPLIERS),Map.of("name",name,"document",name.equals("Beta")?"11.222/33":"ABC"),201);
        assertEquals(1,call(f,get(SUPPLIERS).param("name","%_"),null,200).path("totalItems").asInt());
        assertEquals(1,call(f,get(SUPPLIERS).param("document","11-222/33"),null,200).path("totalItems").asInt());
        var page=call(f,get(SUPPLIERS).param("page","1").param("size","1").param("sort","name,desc"),null,200);assertEquals("Beta",page.at("/items/0/name").asText());
        for(String[] p:new String[][]{{"page","-1"},{"size","0"},{"size","101"},{"sort","document,asc"},{"sort","name,nope"}})call(f,get(SUPPLIERS).param(p[0],p[1]),null,400);
        call(f,post(SUPPLIERS),Map.of("name"," "),400);call(f,post(SUPPLIERS),Map.of("name","Nome","document","<secret>"),400);
        String id=call(f,get(SUPPLIERS),null,200).at("/items/0/id").asText();call(f,delete(SUPPLIERS+"/"+id),null,400);
        category(f,"COST","Custo","WORK_COST",201);category(f,"TAX","Imposto","TAX",201);
        assertEquals(1,call(f,get(CATEGORIES).param("type","TAX"),null,200).path("totalItems").asInt());
        call(f,get(CATEGORIES).param("type","INVALID"),null,400);call(f,post(CATEGORIES),Map.of("code","X","name","Sem tipo"),400);
    }

    @Test void supplierApprovalCreatesUpdatesAndDeactivatesOnlyWhenDecidedAndRejectsSelfApproval()throws Exception{
        var f=owner();var requester=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.REQUEST_CREATE,AccessAction.REQUEST_UPDATE,AccessAction.REQUEST_CANCEL)));
        var approver=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.APPROVE)));
        var pending=call(requester,post(SUPPLIERS),Map.of("name","Pendente","document","99.88"),202);assertTrue(pending.path("supplier").isNull());
        assertEquals(0,call(f,get(SUPPLIERS),null,200).path("totalItems").asInt());String request=pending.path("changeRequestId").asText();
        call(requester,post(approve(request)),Map.of(),403);var decision=call(approver,post(approve(request)),Map.of(),200);String id=decision.at("/appliedEntity/id").asText();assertFalse(id.isBlank());
        var update=call(requester,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","Após aprovação","document","XY77"),202);
        assertEquals("Pendente",call(f,get(SUPPLIERS+"/"+id),null,200).path("name").asText());call(approver,post(approve(update.path("changeRequestId").asText())),Map.of(),200);
        var cancel=call(requester,delete(SUPPLIERS+"/"+id).param("expectedVersion","1"),null,202);assertTrue(call(f,get(SUPPLIERS+"/"+id),null,200).path("active").asBoolean());
        call(approver,post(approve(cancel.path("changeRequestId").asText())),Map.of(),200);assertFalse(call(f,get(SUPPLIERS+"/"+id),null,200).path("active").asBoolean());
        var effective=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.REQUEST_CREATE,AccessAction.APPROVE)));
        var direct=call(effective,post(SUPPLIERS),Map.of("name","Alçada direta efetiva"),201);assertEquals("DIRECT",direct.path("mode").asText());assertTrue(direct.path("changeRequestId").isNull());
    }

    @Test void categoryApprovalAndStaleProposalsAreAtomicAndDuplicateCodesStayTenantScoped()throws Exception{
        var f=owner();var requester=actor(f.company(),Map.of(AccessModule.COSTS_EXPENSES,List.of(AccessAction.REQUEST_CREATE,AccessAction.REQUEST_UPDATE,AccessAction.REQUEST_CANCEL)));
        var approver=actor(f.company(),Map.of(AccessModule.COSTS_EXPENSES,List.of(AccessAction.APPROVE)));
        var p=category(requester,"CAT","Pendente","TAX",202);var duplicate=category(requester,"CAT","Outra proposta","TAX",202);
        assertEquals(0,call(f,get(CATEGORIES),null,200).path("totalItems").asInt());
        var approved=call(approver,post(approve(p.path("changeRequestId").asText())),Map.of(),200);String id=approved.at("/appliedEntity/id").asText();
        call(approver,post(approve(duplicate.path("changeRequestId").asText())),Map.of(),409);
        var stale=call(requester,put(CATEGORIES+"/"+id),Map.of("expectedVersion",0,"name","Pendente"),202);
        call(f,put(CATEGORIES+"/"+id),Map.of("expectedVersion",0,"name","Oficial"),200);
        call(approver,post(approve(stale.path("changeRequestId").asText())),Map.of(),409);
        assertEquals("Oficial",call(f,get(CATEGORIES+"/"+id),null,200).path("name").asText());
        var cancel=call(requester,delete(CATEGORIES+"/"+id).param("expectedVersion","1"),null,202);
        call(approver,post(approve(cancel.path("changeRequestId").asText())),Map.of(),200);assertFalse(call(f,get(CATEGORIES+"/"+id),null,200).path("active").asBoolean());
    }

    @Test void serializesConcurrentSupplierUpdatesAndCategoryCodeCreation()throws Exception{
        var f=owner();String id=call(f,post(SUPPLIERS),Map.of("name","Concorrência"),201).at("/supplier/id").asText();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var a=executor.submit(()->{gate.await();return code(f,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","A"));});
            var b=executor.submit(()->{gate.await();return code(f,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","B"));});gate.countDown();
            var statuses=new ArrayList<>(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS)));statuses.sort(Integer::compare);assertEquals(List.of(200,409),statuses);
            var next=new CountDownLatch(1);var body=Map.of("code","CONCURRENT","name","Categoria","type","TAX");
            var c=executor.submit(()->{next.await();return code(f,post(CATEGORIES),body);});var d=executor.submit(()->{next.await();return code(f,post(CATEGORIES),body);});next.countDown();
            statuses=new ArrayList<>(List.of(c.get(60,TimeUnit.SECONDS),d.get(60,TimeUnit.SECONDS)));statuses.sort(Integer::compare);assertEquals(List.of(201,409),statuses);
        }
        call(f,delete(SUPPLIERS+"/"+id).param("expectedVersion","0"),null,409);
    }

    @Test void exportsExecutableCatalogContractWithGovernanceStatusesAndVersionRequirements()throws Exception{
        String text=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode root=json.readTree(text);
        for(String base:List.of(SUPPLIERS,CATEGORIES)){
            assertTrue(root.path("paths").path(base).path("get").path("responses").has("200"));
            for(String code:List.of("201","202","400","401","403","409"))assertTrue(root.path("paths").path(base).path("post").path("responses").has(code),base+code);
            for(String code:List.of("200","202","403","404","409"))assertTrue(root.path("paths").path(base+"/{id}").path("put").path("responses").has(code));
            assertFalse(root.path("paths").path(base+"/{id}").path("delete").path("responses").path("204").has("content"));
        }
        for(String type:List.of("SupplierUpdateRequest","FinancialCategoryUpdateRequest"))assertTrue(root.at("/components/schemas/"+type+"/required").toString().contains("expectedVersion"));
        assertFalse(root.at("/components/schemas/FinancialCategoryUpdateRequest/properties").has("type"));
        assertFalse(root.at("/components/schemas/SupplierCreateRequest/properties").has("companyId"));
        String export=System.getenv("PAYABLE_CATALOG_OPENAPI_EXPORT_PATH");if(export!=null&&!export.isBlank()){Path target=Path.of(export);Files.createDirectories(target.toAbsolutePath().getParent());Files.writeString(target,text);}
    }

    @Test void supplierContactIsOptionalValidatedPreservedByApprovalAndExcludedFromAudit()throws Exception{
        var f=owner();var body=Map.of("name","Fornecedor contato","contactName","Contato responsável","contactPhone","+55 62 99999-0000","contactEmail","contact-private@example.com");
        var created=call(f,post(SUPPLIERS),body,201).path("supplier");String id=created.path("id").asText();
        assertEquals("Contato responsável",created.path("contactName").asText());
        assertEquals("contact-private@example.com",call(f,get(SUPPLIERS+"/"+id),null,200).path("contactEmail").asText());
        var requester=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.REQUEST_UPDATE)));
        var approver=actor(f.company(),Map.of(AccessModule.PAYABLES,List.of(AccessAction.APPROVE)));
        var pending=call(requester,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","Fornecedor contato","contactName","Contato novo","contactEmail","new-private@example.com"),202);
        assertEquals("Contato responsável",call(f,get(SUPPLIERS+"/"+id),null,200).path("contactName").asText());
        call(approver,post(approve(pending.path("changeRequestId").asText())),Map.of(),200);
        var saved=call(f,get(SUPPLIERS+"/"+id),null,200);assertEquals("Contato novo",saved.path("contactName").asText());assertTrue(saved.path("contactPhone").isNull());
        call(f,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",0,"name","Conflito"),409);
        call(f,post(SUPPLIERS),Map.of("name","Erro","contactEmail","invalid-email"),400);
        call(f,post(SUPPLIERS),Map.of("name","Erro","contactPhone","x".repeat(41)),400);
        call(owner(),get(SUPPLIERS+"/"+id),null,404);
        String audit=jdbc.queryForObject("select string_agg(coalesce(before_data::text,'')||coalesce(after_data::text,'')||coalesce(metadata::text,''),'') from audit_event where company_id=?",String.class,f.company());
        for(String value:List.of("contact-private","new-private","Contato responsável","+55 62"))assertFalse(audit.contains(value));
        var cleared=call(f,put(SUPPLIERS+"/"+id),Map.of("expectedVersion",1,"name","Fornecedor contato"),200).path("supplier");assertTrue(cleared.path("contactEmail").isNull());
    }

    JsonNode category(Actor f,String code,String name,String type,int status)throws Exception{return call(f,post(CATEGORIES),Map.of("code",code,"name",name,"type",type),status);}
    String approve(String id){return "/api/v1/company/change-requests/"+id+"/approve";}
    Actor owner(){String suffix=UUID.randomUUID().toString();var company=companies.save(Company.create("Empresa "+suffix,null,Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),suffix+"@example.com",null,null,null));return actor(company.getId(),Map.of(
            AccessModule.PAYABLES,List.of(AccessAction.READ,AccessAction.CREATE_DIRECT,AccessAction.UPDATE_DIRECT,AccessAction.CANCEL_DIRECT),
            AccessModule.COSTS_EXPENSES,List.of(AccessAction.READ,AccessAction.CREATE_DIRECT,AccessAction.UPDATE_DIRECT,AccessAction.CANCEL_DIRECT)));}
    Actor actor(UUID company,Map<AccessModule,List<AccessAction>> grants){var u=User.create(UUID.randomUUID()+"@example.com",UserType.COMPANY);u.activate(passwords.hash("Canteiro#2026Seguro"),Instant.now());u=users.save(u);
        memberships.save(CompanyUser.create(u.getId(),company));var role=roles.save(Role.create(company,"Perfil "+UUID.randomUUID(),null));
        grants.forEach((module,actions)->actions.forEach(a->rolePermissions.save(RolePermission.create(role.getId(),permissions.findByModuleAndAction(module,a).orElseThrow().getId()))));userRoles.save(UserRole.create(u.getId(),role.getId(),company));return new Actor(company,u.getId());}
    JsonNode call(Actor f,MockHttpServletRequestBuilder request,Object body,int expected)throws Exception{request.with(jwt().jwt(t->t.subject(f.user().toString())));if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String result=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return json.readTree(result.isBlank()?"{}":result);}
    int code(Actor f,MockHttpServletRequestBuilder request,Object body)throws Exception{return mvc.perform(request.with(jwt().jwt(t->t.subject(f.user().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();}
    record Actor(UUID company,UUID user) { }
}
