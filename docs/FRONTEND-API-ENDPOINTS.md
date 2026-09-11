# Catálogo da API para arquitetura do frontend

Documento gerado a partir dos controllers, DTOs, segurança e tratamento de erros existentes no backend em 02/09/2026.

Cobertura validada: 72 operações de controller documentadas em 72 linhas de endpoint; os aliases de usuários totalizam 76 URLs efetivas.

> Fonte de verdade executável: código Java e OpenAPI da aplicação. Este arquivo descreve somente endpoints que já existem; lacunas estão listadas no final.

## 1. Visão geral

- API REST: `http://localhost:8080` por padrão.
- Swagger UI: `/swagger`.
- OpenAPI JSON: `/v3/api-docs`.
- Formato padrão: JSON em `camelCase`, exceto downloads e uploads multipart.
- Datas de negócio: `YYYY-MM-DD`.
- Data/hora: ISO-8601 em UTC, por exemplo `2026-08-24T15:30:00Z`.
- IDs: UUID em texto.
- Valores monetários: número decimal; não usar ponto flutuante no estado financeiro do frontend.
- Rotas `/api/v1/company/**`: tenant derivado do JWT; nunca enviar `companyId` no payload.
- Rotas `/api/v1/platform/**`: destinadas a operadores globais autorizados.
- Não há exclusão física nos endpoints atuais; cancelamentos, reversões e desativações preservam histórico.

### Autenticação e headers

Rotas protegidas usam:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
X-Correlation-Id: <opcional, até 64 caracteres: letras, números, ponto, hífen ou underscore>
```

O backend sempre devolve `X-Correlation-Id`. Rotas públicas:

- `/api/v1/auth/activate`, `/login`, `/refresh`, `/logout` e `/password-reset/**`.
- `/api/v1/catalog`
- `/api/v1/onboarding/**`
- `/api/v1/webhooks/payments`
- `/swagger/**` e `/v3/api-docs/**`

O webhook não usa JWT; exige o header `asaas-access-token` quando a integração Asaas estiver habilitada.

### Aprovação versus aplicação direta

Várias mutações podem ser aplicadas diretamente ou gerar uma solicitação de aprovação:

- `mode: DIRECT`: a entidade é criada/alterada imediatamente.
- `mode: REQUEST_APPROVAL`: normalmente responde `202 Accepted`, a entidade oficial vem `null` e `changeRequestId` identifica a solicitação.
- O frontend não deve presumir sucesso operacional apenas porque a requisição HTTP foi aceita.

### Paginação

Parâmetros comuns:

| Parâmetro | Padrão | Regra |
|---|---:|---|
| `page` | `0` | mínimo `0` |
| `size` | `20` | entre `1` e `100` |
| `sort` | sem ordenação | `campo,asc` ou `campo,desc`; pode ser repetido |

Resposta:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

### Erros

Erros seguem `application/problem+json`:

```json
{
  "type": "urn:canteiro:problem:validation_failed",
  "title": "VALIDATION_FAILED",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED",
  "correlationId": "f5-web-001",
  "violations": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

Códigos atuais: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `BUSINESS_RULE_VIOLATION`, `ACCESS_DENIED`, `UNAUTHENTICATED`, `INVALID_PAGINATION`, `INVALID_FILTER`, `INTERNAL_ERROR`.

## 2. Endpoints

### 2.1 Autenticação

Base: `/api/v1/auth`

| Método | Caminho | Body | Resposta | Status |
|---|---|---|---|---|
| GET | `/me` | — | `AuthenticatedUserResponse` | `200` |
| POST | `/activate` | `ActivateAccountRequest` | vazio | `204` |
| POST | `/login` | `LoginRequest` | `AuthenticationTokensResponse` + refresh cookie | `200` |
| POST | `/refresh` | `{}` + refresh cookie | `AuthenticationTokensResponse` + refresh cookie rotacionado | `200` |
| POST | `/logout` | `{}` + refresh cookie opcional | vazio + expiração do cookie | `204` |
| POST | `/password-reset/request` | `PasswordResetRequest` | vazio | `204` |
| POST | `/password-reset/confirm` | `PasswordResetConfirmationRequest` | vazio | `204` |

Recuperação de senha é concluída por link de e-mail, não por cópia de token: consulte [FRONTEND-PASSWORD-RESET.md](FRONTEND-PASSWORD-RESET.md). O token chega no fragmento `#passwordResetToken=...`, é removido da URL pelo frontend e é enviado somente no corpo de `/password-reset/confirm`.

`GET /me` exige Bearer JWT. Para usuário de empresa, retorna a empresa, papéis, permissões efetivas e `access.level`; `company.logoUrl`, quando presente, aponta para o download autenticado da logo. Para usuário de plataforma, retorna `platformRole` e contexto `PLATFORM`.

O refresh token nunca integra o JSON. A API o mantém em cookie host-only, `HttpOnly`, `Secure`, `SameSite=Strict` e `Path=/api/v1/auth`. `LoginRequest.rememberAccess=false` produz cookie de sessão; `true` adiciona `Max-Age` limitado ao TTL do refresh. A escolha é persistida com o token e preservada na rotação. Frontend e API usam `credentials: include`; CORS aceita somente origens configuradas e nunca `*` com credenciais.

### 2.2 Onboarding da empresa — público

Base: `/api/v1/onboarding/companies`

| Método | Caminho | Body | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CompanyOnboardingCreateRequest` | `CompanyOnboardingResponse` | `201` |

Observação: exige pelo menos um `planId`; consulte `GET /api/v1/catalog` antes de iniciar o onboarding.

A logo não faz parte do onboarding público. Ela só pode ser enviada depois que o proprietário ativar a conta e autenticar-se.

### 2.2.1 Logo da empresa — JWT de empresa

Base: `/api/v1/company/logo`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `multipart/form-data`, campo `file` | `LogoUploadResponse` | `200`, `400`, `403` |
| GET | `/` | — | bytes da imagem | `200`, `404` |

O upload exige a permissão `COMPANY.UPDATE_DIRECT` da própria empresa. Aceita apenas PNG, JPEG ou WebP de até 2 MiB; a API valida o tipo declarado e a assinatura do arquivo. A resposta é `{ "logoUrl": "/api/v1/company/logo" }`. Essa URL exige o mesmo Bearer JWT, portanto o frontend deve baixar a imagem pelo seu cliente HTTP autenticado e renderizá-la como Blob/object URL.

### 2.3 Catálogo público — público

Base: `/api/v1/catalog`

| Método | Caminho | Body | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | — | `PublicCatalogResponse` | `200` |

Retorna somente planos e pacotes ativos com preço vigente na data corrente (`effectiveOn`). Um pacote só aparece se possuir ao menos um plano e todos os seus planos continuarem ativos.

### 2.4 Clientes finais — JWT de empresa

Base: `/api/v1/company/customers`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateFinalCustomerRequest` | `FinalCustomerChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{customerId}` | path UUID | `FinalCustomerResponse` | `200` |
| GET | `/` | `page`, `size`, `sort` | `PageResponse<FinalCustomerResponse>` | `200` |

Ordenação permitida: `name`, `createdAt`.

### 2.5 Obras — JWT de empresa

Base: `/api/v1/company/works`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort` | `PageResponse<WorkResponse>` | `200` |
| POST | `/` | `CreateWorkRequest` | `WorkChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{workId}` | path UUID | `WorkResponse` | `200` |

### 2.6 Contratos — JWT de empresa

Base: `/api/v1/company/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort` | `PageResponse<ContractResponse>` | `200` |
| POST | `/` | `CreateContractRequest` | `ContractChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{contractId}` | path UUID | `ContractResponse` | `200` |
| GET | `/{contractId}/services` | path UUID | `List<ContractServiceResponse>` | `200` |

### 2.7 Funcionários operacionais — JWT de empresa

Base: `/api/v1/company/employees`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateEmployeeRequest` | `EmployeeChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{employeeId}` | path UUID | `EmployeeResponse` operacional | `200` |
| GET | `/` | `page`, `size`, `sort` | `PageResponse<EmployeeResponse>` | `200` |
| POST | `/{employeeId}/access-invitations` | `InviteEmployeeAccessRequest` | `EmployeeAccessInvitationResponse` | `201` |
| POST | `/{employeeId}/addresses` | `EmployeeAddressRequest` | `EmployeeAddressResponse` | `201` |
| GET | `/{employeeId}/addresses` | — | `EmployeeAddressResponse[]` | `200` |
| PUT | `/{employeeId}/addresses/{addressId}` | `EmployeeAddressRequest` | `EmployeeAddressResponse` | `200` |
| DELETE | `/{employeeId}/addresses/{addressId}` | — | — | `204` (exclusão lógica) |
| POST | `/{employeeId}/payment-accounts` | `EmployeePaymentAccountRequest` | `EmployeePaymentAccountResponse` mascarado | `201` |
| GET | `/{employeeId}/payment-accounts` | — | `EmployeePaymentAccountResponse[]` mascarado | `200` |
| POST | `/{employeeId}/payment-accounts/{accountId}/payment-details/reveal` | — | `EmployeePaymentDetailsResponse` completo e pontual | `200`; exige `EMPLOYEES.VIEW_PAYMENT_DETAILS`, gera auditoria |
| PUT | `/{employeeId}/payment-accounts/{accountId}` | `EmployeePaymentAccountRequest` | `EmployeePaymentAccountResponse` mascarado | `200` |
| DELETE | `/{employeeId}/payment-accounts/{accountId}` | — | — | `204` (exclusão lógica) |
| POST | `/{employeeId}/photo` | `multipart/form-data`, campo `file` | `{ photoUrl }` | `200` |
| GET | `/{employeeId}/photo` | — | bytes da imagem | `200` |
| DELETE | `/{employeeId}/photo` | — | — | `204` |

Ordenação permitida: `fullName`, `createdAt`, `updatedAt`.

A foto é opcional e é destinada somente à identificação visual interna. Aceita PNG ou JPEG de até 2 MiB e 2048 × 2048 pixels; não há reconhecimento facial, uso biométrico ou URL pública. O `photoUrl` retornado em `EmployeeResponse` exige o mesmo Bearer JWT e deve ser baixado pelo cliente HTTP autenticado como Blob/object URL. Upload e remoção exigem `EMPLOYEES.UPDATE_DIRECT`; download exige `EMPLOYEES.READ`.

### 2.8 Usuários, papéis e permissões — JWT de empresa

Base: `/api/v1/company/access`

Os caminhos `/users` e `/employees` abaixo são aliases da mesma operação. Para código novo de frontend, prefira `/users` para não confundir usuário de acesso com funcionário operacional.

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/users` ou `/employees` | `CreateCompanyEmployeeRequest` | `AccessUserResponse` | `201` |
| GET | `/users` ou `/employees` | paginação | `PageResponse<AccessUserResponse>` | `200` |
| PATCH | `/users/{userId}/deactivate` ou `/employees/{userId}/deactivate` | path UUID | vazio | `204` |
| PUT | `/users/{userId}/roles` ou `/employees/{userId}/roles` | `ReplaceUserRolesRequest` | `AccessUserResponse` | `200` |
| POST | `/roles` | `CreateRoleRequest` | `RoleResponse` | `201` |
| GET | `/roles` | paginação | `PageResponse<RoleResponse>` | `200` |
| PUT | `/roles/{roleId}` | `UpdateRoleRequest` | `RoleResponse` | `200` |
| PATCH | `/roles/{roleId}/deactivate` | path UUID | vazio | `204` |
| PUT | `/roles/{roleId}/permissions` | `ReplaceRolePermissionsRequest` | `RoleResponse` | `200` |
| GET | `/permissions` | paginação | `PageResponse<PermissionResponse>` | `200` |
| GET | `/change-authorizations/{module}/{operation}` | enums no path | `ChangeAuthorizationResponse` | `200` |

Ordenação:

- usuários: `createdAt`, `updatedAt`;
- papéis: `name`, `createdAt`, `updatedAt`;
- permissões: `module`, `action`, `createdAt`.

O proprietário inicial é retornado normalmente em `GET /users`, com `companyOwner: true`. Exiba-o como **Proprietário protegido** e não ofereça as ações de trocar papéis ou desativar. A API também as bloqueia com `403 ACCESS_DENIED`, portanto a interface não deve depender apenas da ocultação do botão. O papel inicial correspondente vem com `systemProtected: true`; não permitir editar, desativar, trocar permissões ou atribuí-lo a outro usuário.

### 2.9 Solicitações de alteração — JWT de empresa

Base: `/api/v1/company/change-requests`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort`, `search`, `module`, `operation`, `requesterUserId`, `createdFrom`, `createdTo` | `PageResponse<ChangeRequestResponse>` | `200` |
| POST | `/{id}/approve` | `ChangeRequestDecisionRequest` | `ChangeRequestDecisionResponse` | `200` |
| POST | `/{id}/reject` | `ChangeRequestDecisionRequest` | `ChangeRequestDecisionResponse` | `200` |

O solicitante não pode aprovar a própria solicitação. Rejeição exige motivo pelas regras de domínio, embora o DTO ainda não possua validação declarativa.

`search` consulta somente os nomes/títulos seguros de `requester.displayName` e `entity.displayName`; ele não busca CPF, PIS, telefone, endereço, remuneração, conta ou Pix. `module` usa `AuditModule`; `operation` aceita `CREATE`, `UPDATE` ou `CANCEL`; `createdFrom`/`createdTo` usam `YYYY-MM-DD` e incluem todo o dia final.

`ChangeRequestResponse` agora inclui:

```json
{
  "requester": {
    "id": "uuid",
    "displayName": "João Silva",
    "employeeId": "uuid-do-funcionario-vinculado",
    "photoUrl": "/api/v1/company/employees/uuid-do-funcionario-vinculado/photo"
  },
  "entity": { "type": "Employee", "id": null, "displayName": "Marina Souza" },
  "proposedSummary": {
    "fullName": "Marina Souza",
    "jobTitle": "Encarregada",
    "department": "Obras",
    "employmentType": "EMPLOYEE",
    "currentlyEmployed": true,
    "hasDocument": true,
    "hasCompensationAmount": true
  }
}
```

Para um `CREATE` de funcionário pendente, `entity.id` é `null`; use `entity.displayName` para a listagem. Após `POST /{id}/approve`, `ChangeRequestDecisionResponse.appliedEntity` retorna `{ type, id, displayName }` com o funcionário criado, permitindo abrir seu perfil sem nova busca.

`requester.employeeId` e `requester.photoUrl` são opcionais. Eles só existem quando o solicitante está vinculado a funcionário da mesma empresa e há foto cadastrada. `photoUrl` é uma rota autenticada — não é URL pública nem chave de storage — e seu download exige `EMPLOYEES.READ`.

### 2.9.1 Auditoria — JWT de empresa

Base: `/api/v1/company/audit-events`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort` | `PageResponse<AuditEventResponse>` | `200`; exige `AUDIT.READ` |

Para a linha do tempo, use exclusivamente `actorDisplayName` como identificação do responsável. Ele é um nome seguro e legível (por exemplo, o nome completo do funcionário vinculado), nunca e-mail ou UUID. `actorEmail` é um campo de contato separado, disponível quando houver usuário associado; para eventos `SYSTEM`, ambos `actorUserId` e `actorEmail` são nulos e `actorDisplayName` é `Sistema`. Não mostre os dados de `beforeData` ou `afterData`: eles não fazem parte do contrato.

### 2.10 Medições — JWT de empresa

Base: `/api/v1/company/measurements`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateMeasurementRequest` | `MeasurementChangeResponse` | `201` ou `202` |
| GET | `/{measurementId}` | path UUID | `MeasurementDetailsResponse` | `200` |
| POST | `/{measurementId}/versions/{versionId}/items` | `CreateMeasurementItemRequest` | `MeasurementItemChangeResponse` | `201` ou `202` |
| POST | `/{measurementId}/versions/{versionId}/discount` | `CreateMeasurementDiscountRequest` | `MeasurementDiscountChangeResponse` | `201` ou `202` |
| POST | `/{measurementId}/versions/{versionId}/workflow` | `AdvanceMeasurementWorkflowRequest` | `MeasurementWorkflowResponse` | `200` ou `202` |
| POST | `/{measurementId}/revisions` | `CreateMeasurementRevisionRequest` | `MeasurementRevisionResponse` | `201` ou `202` |
| GET | `/{measurementId}/versions/{versionId}/pdf` | path UUIDs | binário PDF + `Content-Disposition` | `200` |
| GET | `/{measurementId}/versions/{versionId}/spreadsheet` | path UUIDs | binário XLSX + `Content-Disposition` | `200` |
| POST | `/{measurementId}/versions/{versionId}/documents` | multipart: `documentType`, `file` | `MeasurementDocumentResponse` | `200` |
| GET | `/{measurementId}/versions/{versionId}/financial-status` | path UUIDs | `MeasurementFinancialStatusResponse` | `200` |
| POST | `/{measurementId}/versions/{versionId}/items/{itemId}/contract-service` | query opcional `justification` | `MeasurementItemConversionResponse` | `200` |

Tipos de upload de medição: `EVIDENCE`, `SPREADSHEET`.

### 2.11 Controle de faturamento — JWT de empresa

Base: `/api/v1/company/billing-control/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | filtros + paginação | `PageResponse<BillingControlSummaryResponse>` | `200` |
| GET | `/{contractId}` | path UUID | `ContractBillingControlResponse` | `200` |

Filtros: `contractId`, `status`, `query`. Ordenação: `name`, `reference`, `status`, `createdAt`.

### 2.12 Parcelas de faturamento — JWT de empresa

Base: `/api/v1/company/billing-installments`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateApprovedBillingInstallmentRequest` | `BillingInstallmentApprovalResponse` | `201` ou `202` |
| POST | `/{installmentId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |

Regras importantes para a UI:

- `SERVICE` exige `contractServiceId` e valor positivo.
- `CONTRACT_DISCOUNT` não aceita serviço e exige valor negativo.
- A parcela deve ter valor líquido positivo.
- Alocações não podem exceder contrato, serviço ou desconto contratual.
- Uma parcela aprovada cria um `Receivable` individual; `financialGroupId` agrupa títulos relacionados.

### 2.13 Notas fiscais — JWT de empresa

Base: `/api/v1/company/invoices`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateRegisteredInvoiceRequest` | `InvoiceRegistrationResponse` | `201` ou `202` |
| POST | `/{invoiceId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |
| POST | `/{invoiceId}/attachments` | multipart: `attachmentType`, `file` | `InvoiceAttachmentResponse` | `200` |

Anexos aceitos: `PDF`, `XML`. A NF pertence a um único contrato e cada item consome somente a capacidade da `BillingAllocation` informada. Registrar NF não registra recebimento.

### 2.14 Impostos da NF — JWT de empresa

Base: `/api/v1/company/invoices/{invoiceId}/tax-obligations`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateTaxObligationRequest` | `TaxObligationCreationResponse` | `201` ou `202` |

Tratamentos:

- `PAYABLE_BY_COMPANY`: cria `Payable` de escopo obra e devolve `payableId`.
- `WITHHELD_BY_CUSTOMER`: exige `receivableId` compatível e reduz o valor líquido a receber.
- `INFORMATIONAL`: apenas registra informação; não cria conta a pagar nem baixa.

### 2.15 Contas a receber e recebimentos — JWT de empresa

Base: `/api/v1/company/receivables`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/{receivableId}/settlements` | `CreateReceivableSettlementRequest` | `ReceivableSettlementApprovalResponse` | `201` ou `202` |
| POST | `/{receivableId}/settlements/{settlementId}/reversal` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `201` ou `202` |
| GET | `/{receivableId}/balance` | path UUID | `ReceivableBalanceResponse` | `200` |

Recebimento pode ser parcial. Estorno cria lançamento reverso; não apaga a baixa original.

### 2.16 Catálogo da plataforma — JWT de proprietário da plataforma

Base: `/api/v1/platform/catalog`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/plans` | — | `List<PlanResponse>` | `200` |
| POST | `/plans` | `CreatePlanRequest` | `PlanResponse` | `201` |
| PATCH | `/plans/{planId}` | `UpdateCatalogItemRequest` | `PlanResponse` | `200` |
| PATCH | `/plans/{planId}/deactivate` | path UUID | vazio | `204` |
| POST | `/plans/{planId}/features` | `AddPlanFeatureRequest` | `CatalogAssignmentResponse` | `201` |
| POST | `/plans/{planId}/prices` | `CreateCatalogPriceRequest` | `CatalogPriceResponse` | `201` |
| GET | `/plans/{planId}/prices` | path UUID | `List<CatalogPriceResponse>` ou Problem Details | `200`, `403`, `404` |
| GET | `/features` | — | `List<PlanFeatureResponse>` | `200` |
| POST | `/features` | `CreatePlanFeatureRequest` | `PlanFeatureResponse` | `201` |
| PATCH | `/features/{featureId}` | `UpdateCatalogItemRequest` | `PlanFeatureResponse` | `200` |
| PATCH | `/features/{featureId}/deactivate` | path UUID | vazio | `204` |
| GET | `/bundles` | — | `List<PlanBundleResponse>` | `200` |
| POST | `/bundles` | `CreatePlanBundleRequest` | `PlanBundleResponse` | `201` |
| PATCH | `/bundles/{bundleId}` | `UpdateCatalogItemRequest` | `PlanBundleResponse` | `200` |
| PATCH | `/bundles/{bundleId}/deactivate` | path UUID | vazio | `204` |
| POST | `/bundles/{bundleId}/plans` | `AddPlanBundleItemRequest` | `CatalogAssignmentResponse` | `201` |
| POST | `/bundles/{bundleId}/prices` | `CreateCatalogPriceRequest` | `CatalogPriceResponse` | `201` |
| GET | `/bundles/{bundleId}/prices` | path UUID | `List<CatalogPriceResponse>` ou Problem Details | `200`, `403`, `404` |

As consultas de preços retornam períodos encerrados, ativos e futuros, sem modificar a vigência. A ordem é `validFrom` decrescente e, em empate, `createdAt` decrescente. Retornam `404` quando o plano ou pacote não existe. `amount` em `CatalogPriceResponse` é uma string decimal canônica (por exemplo, `"99.90"`), e não um número JavaScript.

### 2.17 Administração e assinatura da plataforma — JWT global

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/api/v1/platform/support-users` | `CreatePlatformSupportUserRequest` | `PlatformSupportUserResponse` | `201` |
| GET | `/api/v1/platform/subscriptions/charges` | Query: `page`, `size`, `sort=dueDate,asc|desc`, `eligibleForTrustUnlock` (padrão `true`) | `PageResponse<PlatformChargeTrustUnlockResponse>` | `200` |
| POST | `/api/v1/platform/subscriptions/charges/{chargeId}/trust-unlocks` | `GrantTrustUnlockRequest` | `TrustUnlockResponse` | `201` |

Somente o proprietário da plataforma pode listar cobranças vencidas e conceder desbloqueio de confiança. A listagem retorna empresa identificável, valor decimal canônico (`amount`), vencimento, situação, total de desbloqueios usados, limite fixo de dois, saldo disponível e, quando existir, `activeTrustUnlock.expiresAt`. O backend continua sendo a autoridade de elegibilidade; o POST responde `409` ao exceder o limite.

### 2.18 Empresas da plataforma — JWT de proprietário da plataforma

Base: `/api/v1/platform/companies`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort` | `PageResponse<PlatformCompanySummaryResponse>` | `200` |
| GET | `/{companyId}` | path UUID | `PlatformCompanyDetailsResponse` | `200` |

A listagem permite ordenar por `corporateName`, `tradeName`, `createdAt` e `updatedAt`. O detalhe inclui o histórico de assinaturas, seus planos contratados e o nível de acesso efetivo da empresa.

### 2.19 Webhook de pagamento — público autenticado pelo provedor

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/api/v1/webhooks/payments` | JSON bruto + header `asaas-access-token` | vazio | `200` |

Endpoint condicional: só existe quando `integrations.asaas.enabled=true`. Não deve ser chamado pelo frontend.

## 3. Contratos de entrada

`*` indica campo obrigatório pela validação HTTP.

### Identidade e onboarding

| DTO | Campos |
|---|---|
| `ActivateAccountRequest` | `activationToken*`, `password*` |
| `LoginRequest` | `email*`, `password*`, `rememberAccess` |
| `PasswordResetRequest` | `email*` |
| `PasswordResetConfirmationRequest` | `passwordResetToken*`, `newPassword*` |
| `CompanyOnboardingCreateRequest` | `corporateName*`, `tradeName`, `document*`, `email*`, `phone`, `address`, `ownerEmail*`, `planIds*[]` |

### Cadastros e acesso

| DTO | Campos |
|---|---|
| `CreateFinalCustomerRequest` | `customerType*`, `name*`, `document*`, `contacts[]`, `addresses[]`, `justification` |
| `ContactRequest` | `name*`, `email`, `phone`, `primaryContact` |
| `AddressRequest` | `label`, `postalCode`, `street*`, `number`, `complement`, `district`, `city*`, `state*`, `country`, `primaryAddress` |
| `CreateWorkRequest` | `finalCustomerId*`, `name*`, `reference`, `executionLocationType*`, `executionAddress`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification` |
| `CreateContractRequest` | `workId*`, `reference`, `name*`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification` |
| `CreateEmployeeRequest` | `fullName*`, `jobTitle*`, `documentType*`, `documentNumber*`, `birthDate*`, `employmentType*`, `employmentStartDate*`, `currentlyEmployed*`, `gender*`, `compensationAmount*`, `compensationFrequency*`, `pis` opcional e demais campos opcionais |
| `UpdateEmployeeRequest` | mesmos campos obrigatórios de `CreateEmployeeRequest`, exceto matrícula, que é preservada |
| `EmployeeAddressRequest` | `street*`, `city*`, `country*`, demais campos de endereço e `primaryAddress` |
| `EmployeePaymentAccountRequest` | conta bancária e/ou Pix; `accountCheckDigit` opcional (1–2 alfanuméricos) só com conta; Pix exige `pixReceiverName` e `pixReceiverCity` confirmados do titular, sem inferir dados do funcionário; valores completos somente na entrada |
| `EmployeePaymentDetailsResponse` | conta, dígito e Pix completos somente na ação explícita, permissionada e auditada de pagamento manual; `pixCopyPaste` e `pixQrCode` estáticos, sem valor/cobrança/vencimento, quando titular Pix confirmado; não armazenar, exportar, telemetrizar ou registrar em log |
| `InviteEmployeeAccessRequest` | `email*` |
| `CreateCompanyEmployeeRequest` | `email*` |
| `CreateRoleRequest` / `UpdateRoleRequest` | `name*`, `description` |
| `ReplaceUserRolesRequest` | `roleIds*[]` |
| `ReplaceRolePermissionsRequest` | `permissionIds*[]` |
| `ChangeRequestDecisionRequest` | `decisionReason` |

### Medições

| DTO | Campos |
|---|---|
| `CreateMeasurementRequest` | `workId*`, `contractId`, `reference`, `description`, `measuredOn`, `justification` |
| `CreateMeasurementItemRequest` | `itemNumber* (>0)`, `activity*`, `description`, `chargeType*`, `areaSquareMeters`, `linearMeters`, `kilogramsPerSquareMeter`, `kilogramsPerLinearMeter`, `unitPrice`, `justification` |
| `CreateMeasurementDiscountRequest` | `discountType*`, `discountValue* (>0)`, `justification` |
| `AdvanceMeasurementWorkflowRequest` | `action*`, `externallyAccepted`, `externalAcceptanceOn`, `externalAcceptanceNotes`, `justification` |
| `CreateMeasurementRevisionRequest` | `justification` |

Os campos de cálculo exigidos variam conforme `chargeType`; o frontend deve mostrar apenas os campos aplicáveis.

### Faturamento, NF e recebimento

| DTO | Campos |
|---|---|
| `CreateApprovedBillingInstallmentRequest` | `contractId*`, `reference`, `description`, `issuedOn`, `dueOn*`, `financialGroupId`, `allocations*[]`, `justification` |
| `BillingAllocationRequest` | `contractServiceId`, `type*`, `allocatedAmount*` |
| `CreateRegisteredInvoiceRequest` | `contractId*`, `number*`, `series`, `accessKey`, `issuedOn*`, `items*[]`, `justification` |
| `InvoiceItemRequest` | `billingAllocationId*`, `description`, `allocatedAmount*` |
| `CreateTaxObligationRequest` | `receivableId`, `name*`, `description`, `dueOn`, `amount* (>0)`, `treatment*`, `justification` |
| `CreateReceivableSettlementRequest` | `receivedOn*`, `amount* (>0)`, `paymentMethod*`, `note`, `justification` |
| `CancelFinancialRecordRequest` | `reason*`, `justification` |

### Plataforma

| DTO | Campos |
|---|---|
| `CreatePlanRequest` | `code*`, `name*`, `description` |
| `CreatePlanFeatureRequest` | `code*`, `type*`, `name*`, `description` |
| `CreatePlanBundleRequest` | `code*`, `name*`, `description` |
| `UpdateCatalogItemRequest` | `name*`, `description` |
| `AddPlanFeatureRequest` | `featureId*` |
| `AddPlanBundleItemRequest` | `planId*` |
| `CreateCatalogPriceRequest` | `amount*` (string decimal canônica, por exemplo `"99.90"`), `validFrom*`, `validUntil` |
| `CreatePlatformSupportUserRequest` | `email*` |
| `GrantTrustUnlockRequest` | `reason*`, `expiresAt*` futuro |

## 4. Exemplos dos fluxos financeiros

### Criar parcela com serviço e desconto contratual

```json
{
  "contractId": "00000000-0000-0000-0000-000000000001",
  "reference": "PARC-01",
  "description": "Primeira parcela",
  "issuedOn": "2026-08-24",
  "dueOn": "2026-09-10",
  "financialGroupId": null,
  "allocations": [
    {
      "contractServiceId": "00000000-0000-0000-0000-000000000002",
      "type": "SERVICE",
      "allocatedAmount": 120.00
    },
    {
      "contractServiceId": null,
      "type": "CONTRACT_DISCOUNT",
      "allocatedAmount": -20.00
    }
  ],
  "justification": null
}
```

### Registrar NF referenciando alocações existentes

```json
{
  "contractId": "00000000-0000-0000-0000-000000000001",
  "number": "1234",
  "series": "1",
  "accessKey": null,
  "issuedOn": "2026-08-24",
  "items": [
    {
      "billingAllocationId": "00000000-0000-0000-0000-000000000003",
      "description": "Serviço executado",
      "allocatedAmount": 120.00
    },
    {
      "billingAllocationId": "00000000-0000-0000-0000-000000000004",
      "description": "Desconto contratual",
      "allocatedAmount": -20.00
    }
  ],
  "justification": null
}
```

### Registrar recebimento parcial

```json
{
  "receivedOn": "2026-09-10",
  "amount": 50.00,
  "paymentMethod": "PIX",
  "note": "Primeira baixa",
  "justification": null
}
```

### Registrar retenção do cliente

```json
{
  "receivableId": "00000000-0000-0000-0000-000000000005",
  "name": "ISS retido",
  "description": "Retenção informada na NF",
  "dueOn": null,
  "amount": 5.00,
  "treatment": "WITHHELD_BY_CUSTOMER",
  "justification": null
}
```

## 5. Principais contratos de resposta

### Identidade e autorização

- `AuthenticationTokensResponse`: `accessToken`, `tokenType`, `accessTokenExpiresInSeconds`; o refresh token existe somente no cookie seguro.
- `AccessUserResponse`: `id`, `email`, `status`, `roleIds`, `companyOwner`, `createdAt`, `updatedAt`.
- `RoleResponse`: `id`, `name`, `description`, `active`, `systemProtected`, `permissionIds`, `createdAt`, `updatedAt`.
- `PermissionResponse`: `id`, `module`, `action`, `code`, `active`.
- `ChangeAuthorizationResponse`: `module`, `operation`, `mode`.

### Hierarquia operacional

- `FinalCustomerResponse`: `id`, `customerType`, `name`, `document`, `active`.
- `WorkResponse`: `id`, `finalCustomerId`, `name`, `reference`, `executionLocationType`, `executionAddress`, `status`, datas.
- `ContractResponse`: `id`, `workId`, `reference`, `name`, `status`, datas.
- `EmployeeResponse` operacional: perfil, `registrationNumber` gerada como `ABC-001` quando omitida (três primeiras letras da empresa assinante mais sequência), `documentType`, `documentNumber`, `currentlyEmployed`, `gender`, `ethnicity`, `compensationFrequency` (`MONTHLY`, `DAILY`, `HOURLY`) e `compensationAmount`. O documento é devolvido completo e normalizado somente nas rotas autenticadas da própria empresa. `documentNumberMasked` foi removido do contrato: listagem e detalhe devem usar `documentNumber`.

### Catálogo da plataforma

- `CatalogPriceResponse`: `id`, `catalogItemId`, `amount` (string decimal canônica), `validFrom`, `validUntil`, `createdAt`, `updatedAt`.

### Medições

- `MeasurementDetailsResponse`: medição, versão mais recente, itens, desconto e totais bruto/líquido.
- `MeasurementItemResponse`: parâmetros de fórmula, `totalWeightKg`, `totalAmount`, `calculationFormula`.
- `MeasurementFinancialStatusResponse`: valor dos serviços originados, desconto, líquido, faturado, saldo e posição por serviço.
- Respostas de mutação incluem `changeRequestId` e `authorizationMode`.

### Faturamento

- `ContractBillingControlResponse`: contrato, saldo líquido, serviços e alocações com valores emitidos e disponíveis.
- `BillingInstallmentApprovalResponse`: `billingInstallment`, `receivable`, `changeRequestId`, `mode`.
- `InvoiceRegistrationResponse`: `invoice`, `changeRequestId`, `mode`.
- `ReceivableSettlementApprovalResponse`: `settlement`, `balance`, `changeRequestId`, `mode`.
- `ReceivableBalanceResponse`: `originalAmount`, `withheldAmount`, `netReceivableAmount`, `confirmedSettlementAmount`, `outstandingAmount`, `status`.
- `FinancialCancellationResponse`: `entityId`, `reversalId`, `status`, `changeRequestId`, `mode`.

## 6. Enums necessários no frontend

- `ChangeAuthorizationMode`: `DIRECT`, `REQUEST_APPROVAL`.
- `ChangeOperation`: `CREATE`, `UPDATE`, `CANCEL`.
- `ChangeRequestStatus`: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- `FinalCustomerType`: `INDIVIDUAL`, `LEGAL`.
- `WorkStatus` e `ContractStatus`: `DRAFT`, `OPEN`, `ACTIVE`, `COMPLETED`, `FINALIZED`, `SUSPENDED`, `CANCELLED`.
- `WorkExecutionLocationType`: `FINAL_CUSTOMER_LOCATION`, `PROVIDER_UNIT`, `OTHER_ADDRESS`.
- `MeasurementStatus`: `DRAFT`, `GENERATED`, `SENT`, `PENDING_ACCEPTANCE`, `ACCEPTED`, `FINALIZED`, `REJECTED`, `CANCELLED`.
- `MeasurementVersionStatus`: `DRAFT`, `SENT`, `PENDING_ACCEPTANCE`, `ACCEPTED`, `REJECTED`.
- `MeasurementChargeType`: `SQUARE_METER`, `LINEAR_METER`, `KILOGRAM_PER_SQUARE_METER`, `KILOGRAM_PER_LINEAR_METER`.
- `MeasurementDiscountType`: `FIXED`, `PERCENTAGE`.
- `MeasurementWorkflowAction`: `SEND`, `AWAIT_EXTERNAL_ACCEPTANCE`, `RECORD_EXTERNAL_ACCEPTANCE`, `FINALIZE`.
- `BillingAllocationType`: `SERVICE`, `CONTRACT_DISCOUNT`.
- `BillingAllocationStatus`: `DRAFT`, `ACTIVE`, `CANCELLED`.
- `BillingInstallmentStatus`: `DRAFT`, `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `OVERDUE`, `RENEGOTIATED`, `CANCELLED`.
- `InvoiceStatus`: `DRAFT`, `REGISTERED`, `CANCELLED`.
- `InvoiceItemStatus`: `REGISTERED`, `CANCELLED`.
- `InvoiceAttachmentType`: `PDF`, `XML`.
- `ReceivableStatus`: `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `OVERDUE`, `RENEGOTIATED`, `CANCELLED`.
- `ReceivableSettlementPaymentMethod`: `CASH`, `PIX`, `BANK_TRANSFER`, `CHECK`, `CREDIT_CARD`, `DEBIT_CARD`, `OTHER`.
- `ReceivableSettlementEntryType`: `SETTLEMENT`, `REVERSAL`.
- `ReceivableSettlementStatus`: `CONFIRMED`, `CANCELLED`.
- `TaxTreatment`: `PAYABLE_BY_COMPANY`, `WITHHELD_BY_CUSTOMER`, `INFORMATIONAL`.
- `TaxObligationStatus`: `REGISTERED`, `CANCELLED`.
- `PlanFeatureType`: `MODULE`, `FEATURE`.
- `CatalogPricingSource`: `INDIVIDUAL_PLANS`, `PLAN_BUNDLE`.
- `UserStatus`: `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED`, `INACTIVE`.
- `PlatformUserRole`: `PLATFORM_OWNER`, `PLATFORM_SUPPORT`.

`AccessModule`: `COMPANY`, `USERS`, `EMPLOYEES`, `ROLES`, `CUSTOMERS`, `WORKS`, `CONTRACTS`, `SERVICES`, `DISCOUNTS`, `MEASUREMENTS`, `BILLING`, `INVOICES`, `RECEIVABLES`, `PAYABLES`, `COSTS_EXPENSES`, `REPORTING`, `AUDIT`.

`AccessAction`: `READ`, `CREATE_DIRECT`, `UPDATE_DIRECT`, `REQUEST_CREATE`, `REQUEST_UPDATE`, `REQUEST_CANCEL`, `APPROVE`, `REJECT`, `EXPORT`, `SEND_REPORT`, `MANAGE_USERS`, `MANAGE_ROLES`, `CANCEL_DIRECT`.

## 7. Lacunas atuais que impactam a arquitetura do frontend

Não existem endpoints atuais para:

1. consultar catálogo público de planos antes do onboarding;
2. listar obras;
3. listar contratos pelo módulo de contratos — existe apenas a listagem financeira em `/billing-control/contracts`;
4. listar ou consultar serviços de contrato;
5. definir/alterar desconto contratual pela API;
6. listar medições;
7. listar ou consultar detalhes individuais de parcelas;
8. listar ou consultar NFs já registradas;
9. listar anexos de NF ou baixar o anexo armazenado;
10. listar contas a receber ou consultar seus dados básicos — existe apenas consulta de saldo por ID;
11. listar baixas/recebimentos de uma conta a receber;
12. listar impostos de uma NF;
13. listar solicitações de alteração pendentes;
14. editar clientes, obras, contratos e funcionários;
15. consultar/editar os dados da própria empresa;
16. endpoints de gastos/fornecedores/contas a pagar operacionais;
17. relatórios e exportações da Fase 7.

Essas lacunas devem virar backlog de API antes de fechar navegação, telas de listagem e carregamento inicial do frontend. O frontend não deve acessar banco, storage ou gateway diretamente para contorná-las.

## 8. Sugestão de módulos do cliente HTTP

Uma separação inicial coerente com a API atual:

```text
api/
├── auth
├── onboarding
├── customers
├── works
├── contracts
├── employees
├── access
├── changeRequests
├── measurements
├── billingControl
├── billingInstallments
├── invoices
├── receivables
├── platformCatalog
└── platformAdministration
```

Centralizar no cliente HTTP:

- inclusão e renovação do bearer token;
- rotação segura do refresh token;
- tratamento de `401`, `403`, `409` e `application/problem+json`;
- propagação/exibição de `correlationId` em suporte;
- desserialização de paginação;
- download de `blob` para PDF/XLSX;
- upload `multipart/form-data` sem definir manualmente o boundary;
- distinção entre `201/200` direto e `202` aguardando aprovação.
