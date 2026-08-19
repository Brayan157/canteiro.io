import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const postmanDirectory = path.join(root, "postman");
const environmentPath = path.join(postmanDirectory, "canteiro-io.local.postman_environment.json");
const collectionPath = path.join(postmanDirectory, "canteiro-io.bootstrap.postman_collection.json");
const environment = JSON.parse(fs.readFileSync(environmentPath, "utf8"));

function setEnvironmentValue(key, value, secret = false) {
  const existing = environment.values.find(entry => entry.key === key);
  if (existing) {
    existing.value = value;
    existing.enabled = true;
    existing.type = secret ? "secret" : "default";
    return;
  }
  environment.values.push({ key, value, type: secret ? "secret" : "default", enabled: true });
}

function ensureSecretEnvironmentValue(key) {
  const existing = environment.values.find(entry => entry.key === key);
  if (existing) {
    existing.enabled = true;
    existing.type = "secret";
    return;
  }
  environment.values.push({ key, value: "", type: "secret", enabled: true });
}

setEnvironmentValue("baseUrl", "http://localhost:8081");
setEnvironmentValue("mailpitUrl", "http://localhost:8025");
setEnvironmentValue("platformOwnerEmail", "owner@canteiro.local");
ensureSecretEnvironmentValue("platformOwnerPassword");
setEnvironmentValue("companyOwnerEmail", "");
ensureSecretEnvironmentValue("companyOwnerPassword");
setEnvironmentValue("companyOwnerActivationToken", "", true);
setEnvironmentValue("employeeEmail", "");
ensureSecretEnvironmentValue("employeePassword");
setEnvironmentValue("employeeActivationToken", "", true);
setEnvironmentValue("platformAccessToken", "", true);
setEnvironmentValue("companyOwnerAccessToken", "", true);
setEnvironmentValue("employeeAccessToken", "", true);
setEnvironmentValue("changeRequestId", "");
setEnvironmentValue("customerRequestCreatePermissionId", "");
setEnvironmentValue("requesterRoleId", "");
setEnvironmentValue("employeeUserId", "");
setEnvironmentValue("companyDocument", "");
setEnvironmentValue("customerDocument", "");
setEnvironmentValue("runId", "");
setEnvironmentValue("resetRunId", "true");

function event(lines, listen = "test") {
  return [{ listen, script: { type: "text/javascript", exec: lines } }];
}

function postmanUrl(url) {
  const [pathname, queryString = ""] = url.split("?", 2);
  const query = [...new URLSearchParams(queryString)].map(([key, value]) => ({ key, value }));
  return {
    raw: `{{baseUrl}}${url}`,
    host: ["{{baseUrl}}"],
    path: pathname.split("/").filter(Boolean),
    query
  };
}

function jsonRequest(name, method, url, body, scripts = [], description = "", preRequest = []) {
  const item = {
    name,
    request: {
      method,
      header: [{ key: "Content-Type", value: "application/json" }],
      url: postmanUrl(url),
      body: { mode: "raw", raw: JSON.stringify(body, null, 2), options: { raw: { language: "json" } } },
      description
    },
    response: []
  };
  if (preRequest.length) item.event = [...event(preRequest, "prerequest"), ...event(scripts)];
  else if (scripts.length) item.event = event(scripts);
  return item;
}

function withoutAuthentication(item) {
  item.request.auth = { type: "noauth" };
  return item;
}

function getRequest(name, url, scripts = [], description = "", preRequest = []) {
  const item = { name, request: { method: "GET", header: [], url: postmanUrl(url), description }, response: [] };
  if (preRequest.length) item.event = [...event(preRequest, "prerequest"), ...event(scripts)];
  else if (scripts.length) item.event = event(scripts);
  return item;
}

const expect = status => `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`;
const saveJson = (variable, expression) => `pm.environment.set('${variable}', ${expression});`;
const generateRun = [
  "if (!pm.environment.get('runId') || pm.environment.get('resetRunId') === 'true') {",
  "  const runId = Date.now().toString();",
  "  pm.environment.set('runId', runId);",
  "  pm.environment.set('resetRunId', 'false');",
  "  pm.environment.set('companyOwnerEmail', `owner-${runId}@empresa.local`);",
  "  pm.environment.set('employeeEmail', `funcionario-${runId}@empresa.local`);",
  "  pm.environment.set('companyDocument', runId.padStart(14, '0').slice(-14));",
  "  pm.environment.set('customerDocument', (String(Number(runId) + 1)).padStart(14, '0').slice(-14));",
  "}"
];
const ownerToken = ["pm.environment.set('accessToken', pm.environment.get('companyOwnerAccessToken')); "];

const platform = [
  withoutAuthentication(jsonRequest("1. Login da plataforma", "POST", "/api/v1/auth/login", {
    email: "{{platformOwnerEmail}}", password: "{{platformOwnerPassword}}"
  }, [expect(200), "const body = pm.response.json();", saveJson("platformAccessToken", "body.accessToken"), saveJson("accessToken", "body.accessToken")],
  "Gera identificadores únicos para que o cenário possa ser executado mais de uma vez.", generateRun)),
  jsonRequest("2. Criar feature de medições", "POST", "/api/v1/platform/catalog/features", {
    code: "MEDICOES_{{runId}}", type: "MODULE", name: "Medições", description: "Módulo para cenário automatizado"
  }, [expect(201), "const body = pm.response.json();", saveJson("featureId", "body.id")]),
  jsonRequest("3. Criar plano", "POST", "/api/v1/platform/catalog/plans", {
    code: "PLANO_MEDICOES_{{runId}}", name: "Plano Medições {{runId}}", description: "Plano de teste ponta a ponta"
  }, [expect(201), "const body = pm.response.json();", saveJson("planId", "body.id")]),
  jsonRequest("4. Vincular feature ao plano", "POST", "/api/v1/platform/catalog/plans/{{planId}}/features", {
    featureId: "{{featureId}}"
  }, [expect(201)]),
  jsonRequest("5. Criar preço do plano", "POST", "/api/v1/platform/catalog/plans/{{planId}}/prices", {
    amount: 99.9, validFrom: "2026-01-01", validUntil: null
  }, [expect(201)])
];

const company = [
  withoutAuthentication(jsonRequest("6. Cadastrar empresa", "POST", "/api/v1/onboarding/companies", {
    corporateName: "Empresa Medições {{runId}} Ltda.", tradeName: "Empresa Medições {{runId}}",
    document: "{{companyDocument}}", email: "empresa-{{runId}}@canteiro.local", phone: "11999990000",
    address: "Rua da Medição, 100", ownerEmail: "{{companyOwnerEmail}}", planIds: ["{{planId}}"]
  }, [expect(201), "const body = pm.response.json();", saveJson("companyId", "body.companyId")],
  "O proprietário recebe o token de ativação por e-mail; a API nunca o devolve por segurança.")),
  withoutAuthentication(jsonRequest("7. Ativar proprietário da empresa (preencher token do e-mail)", "POST", "/api/v1/auth/activate", {
    activationToken: "{{companyOwnerActivationToken}}", password: "{{companyOwnerPassword}}"
  }, [expect(204)], "Abra o Mailpit em {{mailpitUrl}}, copie o token de ativação da mensagem e preencha companyOwnerActivationToken no environment.")),
  withoutAuthentication(jsonRequest("8. Login do proprietário da empresa", "POST", "/api/v1/auth/login", {
    email: "{{companyOwnerEmail}}", password: "{{companyOwnerPassword}}"
  }, [expect(200), "const body = pm.response.json();", saveJson("companyOwnerAccessToken", "body.accessToken"), saveJson("accessToken", "body.accessToken")]))
];

const employeeAndApproval = [
  jsonRequest("9. Criar funcionário", "POST", "/api/v1/company/access/employees", {
    email: "{{employeeEmail}}"
  }, [expect(201), "const body = pm.response.json();", saveJson("employeeUserId", "body.id")], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("10. Criar papel: solicitante de clientes", "POST", "/api/v1/company/access/roles", {
    name: "Solicitante de clientes {{runId}}", description: "Pode apenas solicitar cadastro de cliente"
  }, [expect(201), "const body = pm.response.json();", saveJson("requesterRoleId", "body.id")], "Executar com o token do proprietário da empresa.", ownerToken),
  getRequest("11. Localizar permissão CUSTOMERS / REQUEST_CREATE", "/api/v1/company/access/permissions?page=0&size=100", [
    expect(200), "const permission = pm.response.json().items.find(item => item.module === 'CUSTOMERS' && item.action === 'REQUEST_CREATE');",
    "pm.test('Permissão CUSTOMERS/REQUEST_CREATE existe', () => pm.expect(permission).to.exist);",
    saveJson("customerRequestCreatePermissionId", "permission.id")
  ], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("12. Dar somente permissão de solicitar cliente", "PUT", "/api/v1/company/access/roles/{{requesterRoleId}}/permissions", {
    permissionIds: ["{{customerRequestCreatePermissionId}}"]
  }, [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("13. Vincular papel ao funcionário", "PUT", "/api/v1/company/access/employees/{{employeeUserId}}/roles", {
    roleIds: ["{{requesterRoleId}}"]
  }, [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken),
  withoutAuthentication(jsonRequest("14. Ativar funcionário (preencher token do e-mail)", "POST", "/api/v1/auth/activate", {
    activationToken: "{{employeeActivationToken}}", password: "{{employeePassword}}"
  }, [expect(204)], "Abra o Mailpit, copie o token enviado ao funcionário e preencha employeeActivationToken no environment.")),
  withoutAuthentication(jsonRequest("15. Login do funcionário", "POST", "/api/v1/auth/login", {
    email: "{{employeeEmail}}", password: "{{employeePassword}}"
  }, [expect(200), "const body = pm.response.json();", saveJson("employeeAccessToken", "body.accessToken"), saveJson("accessToken", "body.accessToken")])),
  jsonRequest("16. Funcionário solicita cliente", "POST", "/api/v1/company/customers", {
    customerType: "LEGAL", name: "Cliente Aceite {{runId}} Ltda.", document: "{{customerDocument}}",
    contacts: [{ name: "Contato do Cliente", email: "contato-{{runId}}@cliente.local", phone: "11988880000", primaryContact: true }],
    addresses: [{ label: "Obra", postalCode: "01001000", street: "Praça da Sé", number: "1", city: "São Paulo", state: "SP", country: "BR", primaryAddress: true }],
    justification: "Cadastro solicitado pelo funcionário"
  }, [expect(202), "const body = pm.response.json();", saveJson("changeRequestId", "body.changeRequestId"), "pm.test('Solicitação pendente criada', () => pm.expect(body.changeRequestId).to.exist);"]),
  jsonRequest("17. Proprietário aprova cliente", "POST", "/api/v1/company/change-requests/{{changeRequestId}}/approve", {
    decisionReason: "Cliente conferido e aprovado"
  }, [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken),
  getRequest("18. Localizar cliente aprovado", "/api/v1/company/customers?page=0&size=100", [
    expect(200), "const customer = pm.response.json().items.find(item => item.document === pm.environment.get('customerDocument'));",
    "pm.test('Cliente aprovado encontrado', () => pm.expect(customer).to.exist);",
    saveJson("customerId", "customer.id")
  ], "Executar com o token do proprietário da empresa.", ownerToken)
];

const measurement = [
  jsonRequest("19. Criar obra", "POST", "/api/v1/company/works", {
    finalCustomerId: "{{customerId}}", name: "Obra Cobertura {{runId}}", reference: "OBRA-{{runId}}",
    executionLocationType: "FINAL_CUSTOMER_LOCATION", executionAddress: null, status: "ACTIVE",
    startedOn: "2026-08-19", expectedCompletionOn: null, completedOn: null
  }, [expect(201), "const body = pm.response.json();", saveJson("workId", "body.work.id")], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("20. Criar contrato", "POST", "/api/v1/company/contracts", {
    workId: "{{workId}}", reference: "CONTRATO-{{runId}}", name: "Contrato de cobertura", status: "OPEN",
    startedOn: "2026-08-19", expectedCompletionOn: null, completedOn: null
  }, [expect(201), "const body = pm.response.json();", saveJson("contractId", "body.contract.id")], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("21. Criar medição", "POST", "/api/v1/company/measurements", {
    workId: "{{workId}}", contractId: "{{contractId}}", reference: "MED-{{runId}}",
    description: "Medição da cobertura metálica", measuredOn: "2026-08-19"
  }, [expect(201), "const body = pm.response.json();", saveJson("measurementId", "body.measurement.id"), saveJson("versionId", "body.version.id")], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("22. Adicionar item em m²", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/items", {
    itemNumber: 1, activity: "Telhado", description: "Instalação de telha metálica galvanizada",
    chargeType: "SQUARE_METER", areaSquareMeters: 120, unitPrice: 85
  }, [expect(201), "const body = pm.response.json();", saveJson("itemId", "body.item.id")], "Valor esperado: 120 × 85 = 10.200,00.", ownerToken),
  jsonRequest("23. Aplicar desconto de cabeçalho", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/discount", {
    discountType: "PERCENTAGE", discountValue: 5, justification: "Desconto comercial"
  }, [expect(201)], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("24. Enviar medição", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/workflow", {
    action: "SEND", externallyAccepted: null, externalAcceptanceOn: null, externalAcceptanceNotes: null
  }, [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("25. Aguardar aceite externo", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/workflow", {
    action: "AWAIT_EXTERNAL_ACCEPTANCE", externallyAccepted: null, externalAcceptanceOn: null, externalAcceptanceNotes: null
  }, [expect(200)], "Antes do próximo passo, use o request de upload de documento da coleção completa para anexar a evidência do aceite.", ownerToken),
  jsonRequest("26. Registrar aceite externo", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/workflow", {
    action: "RECORD_EXTERNAL_ACCEPTANCE", externallyAccepted: true, externalAcceptanceOn: "2026-08-19",
    externalAcceptanceNotes: "Aceite recebido externamente e evidência anexada"
  }, [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("27. Converter item aceito em serviço", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/items/{{itemId}}/contract-service", {}, [
    expect(200), "const body = pm.response.json();", "pm.test('Primeira conversão não é repetida', () => pm.expect(body.alreadyConverted).to.eql(false));"
  ], "Executar com o token do proprietário da empresa.", ownerToken),
  jsonRequest("28. Repetir conversão: testar idempotência", "POST", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/items/{{itemId}}/contract-service", {}, [
    expect(200), "const body = pm.response.json();", "pm.test('Conversão idempotente', () => pm.expect(body.alreadyConverted).to.eql(true));"
  ], "Executar com o token do proprietário da empresa.", ownerToken),
  getRequest("29. Consultar saldo da medição", "/api/v1/company/measurements/{{measurementId}}/versions/{{versionId}}/financial-status", [expect(200)], "Executar com o token do proprietário da empresa.", ownerToken)
];

const collection = {
  info: {
    name: "Canteiro.io — Cenário completo de medição",
    description: "Execute as pastas na ordem. A auditoria é conferida diretamente na tabela audit_event; não existe endpoint de leitura de auditoria.\n\nConsulta SQL: SELECT occurred_at, actor_user_id, module, action, entity_type, entity_id, before_data, after_data, metadata FROM audit_event WHERE module IN ('MEASUREMENTS', 'SERVICES', 'CUSTOMERS') ORDER BY occurred_at DESC;",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  auth: { type: "bearer", bearer: [{ key: "token", value: "{{accessToken}}", type: "string" }] },
  item: [
    { name: "1. Plataforma: catálogo", item: platform },
    { name: "2. Empresa: onboarding e proprietário", item: company },
    { name: "3. Funcionário: solicitação e aceite de cliente", item: employeeAndApproval },
    { name: "4. Medição: aceite, conversão e saldo", item: measurement }
  ]
};

environment._postman_exported_at = new Date().toISOString();
environment._postman_exported_using = "Canteiro.io guided scenario generator";
fs.writeFileSync(environmentPath, JSON.stringify(environment, null, 2) + "\n");
fs.writeFileSync(collectionPath, JSON.stringify(collection, null, 2) + "\n");
console.log("Generated guided Postman collection with 29 requests.");
