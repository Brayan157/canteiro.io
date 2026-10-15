# Catálogo da API para arquitetura do frontend

Documento baseado nos controllers, DTOs, segurança e tratamento de erros do backend. Seções de medições e cadeia financeira revisadas em 14/10/2026.

Aliases de acesso continuam a ser URLs distintas para a mesma operação de negócio. A cobertura executável é validada por `OpenApiContractIntegrationTest`; confira sempre o OpenAPI da instância publicada antes de gerar tipos.

> Fonte de verdade executável: código Java e OpenAPI da aplicação. Este arquivo descreve somente endpoints que já existem; lacunas estão listadas no final.

> Planejamento não executável: a futura Fase 10 de vínculos, readmissão, aviso prévio, conformidade e documentos de funcionário está detalhada em [FRONTEND-EMPLOYEE-LIFECYCLE-PLANNED.md](FRONTEND-EMPLOYEE-LIFECYCLE-PLANNED.md). Essas rotas ainda não existem e não devem gerar tipos manuais no frontend.

> Medição contínua implementada: grade acumulada, identidade persistida, destino contratual, composição incremental, financeiro e relatórios estão no [guia de integração](FRONTEND-MEASUREMENT-CONTINUOUS.md). Gere tipos somente do OpenAPI executável/exportado.

> Atualização publicada em localhost:8081 em 14/10/2026, com V84 aplicada: `currency` BRL/USD/EUR, `GET /api/v1/company/currencies` e relatórios de dez colunas. Contratos, defaults, imutabilidade, erros, reimportação e hash do OpenAPI vivo estão no [guia de moedas e relatórios](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md). Dinheiro permanece decimal canônico em texto; moeda é código ISO separado. A [F04-41 foi concluída em 15/10/2026](F04-41-LOCAL-PUBLICATION.md), com sincronização/tipos e validação do frontend de moedas e PDF/XLSX reais com filename oficial. Logo real permanece aguardando cadastro pelo usuário/interface do frontend, com fallback seguro.

## 1. Visão geral

- API REST: `http://localhost:8080` por padrão; no Docker local publicado em 14/10/2026, o frontend acessa `http://localhost:8081`.
- Swagger UI: `/swagger`.
- OpenAPI JSON: `/v3/api-docs`.
- Formato padrão: JSON em `camelCase`, exceto downloads e uploads multipart.
- Datas de negócio: `YYYY-MM-DD`.
- Data/hora: ISO-8601 em UTC, por exemplo `2026-08-24T15:30:00Z`.
- IDs: UUID em texto.
- Valores monetários: os contratos decimais canônicos publicados usam strings como `"1250.50"`; preservar precisão e usar o schema de cada DTO. Existem DTOs legados com `number`, que não autorizam cálculo financeiro local nem conversão dos contratos canônicos para ponto flutuante.
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

O backend sempre devolve `X-Correlation-Id`. O complemento CORS foi publicado em 15/10/2026 e também expõe explicitamente `Content-Disposition` às origens já autorizadas, para leitura do filename pelo navegador. Não substituir o título oficial por nome fabricado no cliente. [Estado e testes](F04-42-DOWNLOAD-CORS-AND-BRANDING.md).

O [storage privado local](F08-23-LOCAL-PRIVATE-STORAGE.md) foi configurado e testado em 15/10/2026, sem mudança de contrato HTTP. Uploads continuam pelas rotas autenticadas; não enviar companyId, acessar o volume pelo frontend nem usar URL pública. Nenhuma logo foi cadastrada pelo agente.

Complemento exclusivo PDF **publicado e verificado em 15/10/2026**: nome/CNPJ da emitente aparecem também quando existe logo, no acumulado/por versão e prévias. Sem campo/rota nova e sem mudança de XLSX; o frontend continua consumindo o Blob oficial. [Estado da implementação/publicação e provas](FRONTEND-MEASUREMENT-PDF-EMITTER.md).

Rotas públicas:

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
| PUT | `/{customerId}` | `UpdateFinalCustomerRequest` | `FinalCustomerChangeResponse` | `200` direto ou `202` aprovação |
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<FinalCustomerResponse>` | `200` |
| GET | `/{customerId}/contacts` | — | `FinalCustomerContactResponse[]` | `200` |
| POST | `/{customerId}/contacts` | `FinalCustomerContactRequest` | `FinalCustomerContactChangeResponse` | `201` direto ou `202` aprovação |
| PUT | `/{customerId}/contacts/{contactId}` | `FinalCustomerContactRequest` | `FinalCustomerContactChangeResponse` | `200` direto ou `202` aprovação |
| PUT | `/{customerId}/contacts/{contactId}/primary` | — | `FinalCustomerContactChangeResponse` | `200` direto ou `202` aprovação |
| DELETE | `/{customerId}/contacts/{contactId}` | `justification` opcional | vazio ou `FinalCustomerChangeRequestResponse` | `204` direto ou `202` aprovação |
| GET | `/{customerId}/addresses` | — | `FinalCustomerAddressResponse[]` | `200` |
| POST | `/{customerId}/addresses` | `FinalCustomerAddressRequest` | `FinalCustomerAddressChangeResponse` | `201` direto ou `202` aprovação |
| PUT | `/{customerId}/addresses/{addressId}` | `FinalCustomerAddressRequest` | `FinalCustomerAddressChangeResponse` | `200` direto ou `202` aprovação |
| PUT | `/{customerId}/addresses/{addressId}/primary` | — | `FinalCustomerAddressChangeResponse` | `200` direto ou `202` aprovação |
| DELETE | `/{customerId}/addresses/{addressId}` | `justification` opcional | vazio ou `FinalCustomerChangeRequestResponse` | `204` direto ou `202` aprovação |

Filtros da listagem:

| Parâmetro | Tipo | Regra |
|---|---|---|
| `name` | string | contém, sem diferenciar maiúsculas/minúsculas |
| `document` | string | contém; CPF/CNPJ é normalizado para dígitos antes da busca |
| `customerType` | `INDIVIDUAL` ou `LEGAL` | tipo de cliente |
| `active` | boolean | situação do cadastro |

Ordenação permitida: `name`, `document`, `customerType`, `active`, `createdAt`, `updatedAt`.

`UpdateFinalCustomerRequest` contém `customerType`, `name`, `document` e `justification`. A edição não altera obras/contratos; ela atualiza somente o perfil comercial do cliente. Com alçada direta, retorna `200` e `customer`; sem ela, retorna `202`, `customer: null` e `changeRequestId`.

Os contatos exigem `name` e ao menos `email` ou `phone`. Endereços exigem `street`, `city` e `state`; `country` assume `BR` quando omitido. Listagens retornam apenas registros ativos, priorizando o principal. O `DELETE` é sempre desativação lógica: o frontend remove o item da tela após `204`, mas não deve supor exclusão física. Para uma resposta `202`, nenhum dado oficial mudou; aguarde a aprovação usando `changeRequestId`.

### 2.5 Obras — JWT de empresa

Base: `/api/v1/company/works`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<WorkResponse>` | `200` |
| POST | `/` | `CreateWorkRequest` | `WorkChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{workId}` | path UUID | `WorkResponse` | `200` |
| PUT | `/{workId}` | `UpdateWorkRequest` | `WorkChangeResponse` | `200` direto ou `202` aprovação |

Filtros da listagem:

| Query | Tipo | Uso |
|---|---|---|
| `name` | string | trecho do nome da obra |
| `reference` | string | trecho da referência/código da obra |
| `finalCustomerId` | UUID | cliente final da obra |
| `finalCustomerName` | string | trecho do nome do cliente final; permite localizar obras sem conhecer UUID |
| `finalCustomerDocument` | string | trecho do CPF/CNPJ do cliente final; a API normaliza pontuação antes da pesquisa |
| `status` | `WorkStatus` | situação da obra |
| `executionLocationType` | `WorkExecutionLocationType` | local do cliente final, unidade da prestadora ou outro endereço |
| `startedFrom` / `startedTo` | `YYYY-MM-DD` | intervalo inclusivo da data de início |
| `expectedCompletionFrom` / `expectedCompletionTo` | `YYYY-MM-DD` | intervalo inclusivo da previsão de conclusão |

Ordenação permitida: `name`, `reference`, `status`, `executionLocationType`, `startedOn`, `expectedCompletionOn`, `completedOn`, `createdAt`, `updatedAt`.

`UpdateWorkRequest` contém `name`, `reference`, `executionLocationType`, `executionAddress`, `status`, datas e `justification`. Ele não recebe `finalCustomerId`: a edição preserva o cliente final da obra e evita movimentação silenciosa na hierarquia comercial.

### 2.6 Contratos — JWT de empresa

Base: `/api/v1/company/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<ContractResponse>` | `200` |
| POST | `/` | `CreateContractRequest` | `ContractChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{contractId}` | path UUID | `ContractResponse` | `200` |
| PUT | `/{contractId}` | `UpdateContractRequest` | `ContractChangeResponse` | `200` direto ou `202` aprovação |
| GET | `/{contractId}/services` | path UUID | `List<ContractServiceResponse>` | `200` |
| POST | `/{contractId}/services` | `UpsertContractServiceRequest` | `ContractServiceChangeResponse` | `201` direto ou `202` aprovação |
| PUT | `/{contractId}/services/{serviceId}` | `UpsertContractServiceRequest` | `ContractServiceChangeResponse` | `200` direto ou `202` aprovação |
| PUT | `/{contractId}/services/{serviceId}/discount` | `SetContractDiscountRequest` | `ContractServiceChangeResponse` | `200` direto ou `202` aprovação |
| POST | `/{contractId}/services/{serviceId}/discount/reversal` | `ReverseDiscountRequest` | `ContractServiceChangeResponse` | `200` direto ou `202` aprovação |
| PUT | `/{contractId}/discount` | `SetContractDiscountRequest` | `ContractServiceChangeResponse` | `200` direto ou `202` aprovação |
| POST | `/{contractId}/discount/reversal` | `ReverseDiscountRequest` | `ContractServiceChangeResponse` | `200` direto ou `202` aprovação |

Modelos de serviço para seleção: `GET /api/v1/company/service-templates?includeInactive=false`. A lista é do tenant autenticado e retorna somente modelos ativos por padrão.

Filtros da listagem:

| Query | Tipo | Uso |
|---|---|---|
| `name` | string | trecho do nome do contrato |
| `reference` | string | trecho da referência/código do contrato |
| `workId` | UUID | obra à qual o contrato pertence |
| `workName` | string | trecho do nome da obra; permite localizar contratos sem conhecer UUID |
| `finalCustomerName` | string | trecho do nome do cliente final da obra; pode ser combinado com `workName` |
| `status` | `ContractStatus` | situação do contrato |
| `startedFrom` / `startedTo` | `YYYY-MM-DD` | intervalo inclusivo da data de início |
| `expectedCompletionFrom` / `expectedCompletionTo` | `YYYY-MM-DD` | intervalo inclusivo da previsão de conclusão |
| `completedFrom` / `completedTo` | `YYYY-MM-DD` | intervalo inclusivo da data de conclusão |

Ordenação permitida: `name`, `reference`, `status`, `startedOn`, `expectedCompletionOn`, `completedOn`, `createdAt`, `updatedAt`.

`CreateContractRequest` e `UpdateContractRequest` incluem `contractIdentifier`, uma identificação livre de negócio (por exemplo, `0001/2027` ou `Contrato padrão`). Esse identificador **não é único**, inclusive quando se repetir em contratos de clientes finais distintos. `UpdateContractRequest` também contém `reference`, `name`, `status`, datas e `justification`. Ele não recebe `workId`: a obra do contrato é preservada para não alterar silenciosamente o encadeamento comercial, de medição e de faturamento.

`UpsertContractServiceRequest` contém `sourceServiceTemplateId` opcional, `name`, `description`, `status`, `quantity`, `unitPrice`, `discountType`, `discountValue` e `justification`. Na criação, o modelo informado deve pertencer à empresa e fica registrado como origem. Na edição, ele pode ser reenviado somente se for o mesmo modelo de origem; não pode ser trocado. A edição nunca recebe nem troca `contractId`. `SetContractDiscountRequest` exige `discountType`, `discountValue` e `justification`.

Todo valor de quantidade ou dinheiro dessas rotas é texto decimal canônico: `quantity` usa quatro casas (`"12.5000"`) e valores monetários duas (`"150.00"`). O frontend não calcula descontos, valores líquidos, totais ou saldos: a resposta direta traz o serviço oficial e os totais oficiais do contrato. Respostas `202` trazem somente `changeRequestId` e `mode`, sem serviço ou contrato fictício.

Para desfazer um desconto, o frontend deve usar a rota `.../discount/reversal`, com `ReverseDiscountRequest`:
```json
{ "justification": "Desconto comercial cancelado após renegociação." }
```
Ela não usa valor zero e não apaga dados: o desconto do serviço é removido do cálculo oficial e o desconto de contrato é desativado logicamente. Em ambos os casos, a auditoria preserva antes/depois, autor, motivo e origem. Para repetir um desconto anterior, envie novamente o valor desejado ao `PUT` normal; a API nunca faz rollback automático de eventos antigos.

### 2.7 Funcionários operacionais — JWT de empresa

Base: `/api/v1/company/employees`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateEmployeeRequest` | `EmployeeChangeResponse` | `201` direto ou `202` aprovação |
| GET | `/{employeeId}` | path UUID | `EmployeeResponse` operacional | `200` |
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<EmployeeResponse>` | `200` |
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

Filtros da listagem:

| Parâmetro | Tipo | Regra |
|---|---|---|
| `name` | string | contém em `fullName` ou `preferredName`, sem diferenciar maiúsculas/minúsculas |
| `document` | string | contém; o documento é normalizado para dígitos antes da busca |
| `jobTitle` | string | contém, sem diferenciar maiúsculas/minúsculas |
| `department` | string | contém, sem diferenciar maiúsculas/minúsculas |
| `employmentType` | `EmployeeEmploymentType` | tipo de vínculo |
| `compensationFrequency` | `MONTHLY`, `DAILY` ou `HOURLY` | modalidade da remuneração |
| `compensationMin` | decimal | limite inferior inclusivo da remuneração |
| `compensationMax` | decimal | limite superior inclusivo da remuneração; deve ser maior ou igual ao mínimo |
| `currentlyEmployed` | boolean | empregado atualmente na empresa |
| `active` | boolean | cadastro ativo |

`compensationMin` e `compensationMax` devem usar decimal em texto, por exemplo `3500.00`; não use ponto flutuante no estado do frontend. Os filtros de documento e remuneração só funcionam dentro do tenant autenticado e com `EMPLOYEES.READ`.

Ordenação permitida: `fullName`, `jobTitle`, `department`, `employmentType`, `compensationAmount`, `compensationFrequency`, `currentlyEmployed`, `active`, `createdAt`, `updatedAt`.

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

As exportações PDF e XLSX são respostas HTTP em streaming. O frontend continua fazendo download pelas mesmas URLs e tipos MIME; não há alteração de payload ou de contrato JSON. Para arquivos grandes, consumir a resposta como download/`Blob` e não tentar interpretá-la como JSON.

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | paginação, filtros e ordenação abaixo | `PageResponse<MeasurementResponse>` | `200`; `400`/`403` em Problem Details; exige `MEASUREMENTS.READ` |
| POST | `/` | `CreateMeasurementRequest` | `MeasurementChangeResponse` | `201` direto ou `202` para aprovação; `400`/`403`/`404` em Problem Details |
| PUT | `/{measurementId}/link` | `LinkMeasurementRequest` | `MeasurementChangeResponse` | `200` direto ou `202` para aprovação; `400`/`403`/`404`/`409` em Problem Details |
| DELETE | `/{measurementId}` | query opcional `justification` | sem corpo direto ou `MeasurementCancellationResponse` | `204` direto/idempotente ou `202` para aprovação; `400`/`403`/`404`/`409` em Problem Details |
| GET | `/{measurementId}` | path UUID | `MeasurementDetailsResponse` | `200`; `403`/`404` em Problem Details |
| GET | `/{measurementId}/versions` | path UUID | `MeasurementVersionHistoryResponse[]` | `200`; histórico por versão decrescente, `403`/`404` em Problem Details; exige `MEASUREMENTS.READ` |
| GET | `/{measurementId}/versions/{versionId}` | path UUIDs | `MeasurementVersionDetailsResponse` | `200`; itens, serviços e totais oficiais da versão selecionada; `403`/`404` em Problem Details; exige `MEASUREMENTS.READ` |
| POST | `/{measurementId}/versions/{versionId}/items` | `CreateMeasurementItemRequest` | `MeasurementItemChangeResponse` | `201` ou `202` |
| POST | `/{measurementId}/versions/{versionId}/items/insert` | `InsertMeasurementItemRequest` | `MeasurementItemCorrectionResponse` | `201` direto ou `202` para aprovação; `400`/`403`/`404`/`409` em Problem Details |
| PUT | `/{measurementId}/versions/{versionId}/items/reorder` | `ReorderMeasurementItemsRequest` | `MeasurementItemReorderResponse` | `200` direto ou `202` para aprovação; `400`/`403`/`404`/`409` em Problem Details |
| PUT | `/{measurementId}/versions/{versionId}/items/{itemId}` | `UpdateMeasurementItemRequest` | `MeasurementItemCorrectionResponse` | `200` direto ou `202` para aprovação |
| DELETE | `/{measurementId}/versions/{versionId}/items/{itemId}` | query opcional `justification` (máximo 2.000 caracteres) | `MeasurementItemCorrectionResponse` | `200` direto ou `202` para aprovação; `400`/`403`/`404`/`409` em Problem Details; exclusão lógica |
| POST | `/{measurementId}/versions/{versionId}/items/{itemId}/corrective-revision` | `CorrectAcceptedMeasurementItemRequest` | `MeasurementItemCorrectionResponse` | `201` direto ou `202` para aprovação |
| POST | `/{measurementId}/versions/{versionId}/discount` | `CreateMeasurementDiscountRequest` | `MeasurementDiscountChangeResponse` | `201` ou `202` |
| POST | `/{measurementId}/versions/{versionId}/workflow` | `AdvanceMeasurementWorkflowRequest` | `MeasurementWorkflowResponse` | `200` ou `202` |
| POST | `/{measurementId}/revisions` | `CreateMeasurementRevisionRequest` | `MeasurementRevisionResponse` | `201` ou `202`; aceita versão anterior `ACCEPTED` ou `REJECTED` |
| GET | `/{measurementId}/versions/{versionId}/pdf` | path UUIDs | binário PDF + `Content-Disposition` | `200` |
| GET | `/{measurementId}/versions/{versionId}/spreadsheet` | path UUIDs | binário XLSX + `Content-Disposition` | `200` |
| GET | `/{measurementId}/versions/{versionId}/spreadsheet/preview` | path UUIDs | binário PDF oficial + `Content-Disposition: inline` | `200`; `403`/`404` em Problem Details; exige `MEASUREMENTS.EXPORT` |
| POST | `/{measurementId}/versions/{versionId}/items/import/preview` | multipart `file` XLSX | `MeasurementSpreadsheetImportPreviewResponse` | `200`; valida sem alterar itens; `400`/`403`/`404`/`409` em Problem Details |
| POST | `/{measurementId}/versions/{versionId}/items/import/confirm` | multipart `file`, `spreadsheetHash`, `justification?` | `MeasurementSpreadsheetImportApplyResponse` | `200` direto ou `202` aprovação; `400`/`403`/`404`/`409` em Problem Details |
| POST | `/{measurementId}/versions/{versionId}/documents` | multipart: `documentType`, `file` | `MeasurementDocumentResponse` | `200` |
| GET | `/{measurementId}/versions/{versionId}/financial-status` | path UUIDs | `MeasurementFinancialStatusResponse` | `200` |
| GET | `/{measurementId}/financial-summary` | path UUID | `MeasurementFinancialSummaryResponse` | `200`; exige simultaneamente `MEASUREMENTS.READ`, `BILLING.READ` e `RECEIVABLES.READ`; `403`/`404` em Problem Details |
| GET | `/{measurementId}/accumulated` | path UUID | `MeasurementAccumulatedResponse` | `200`; grade contínua com aceitos e avanço atual; financeiro protegido pode ser nulo |
| GET / POST | `/{measurementId}/services` | `CreateMeasurementContinuousServiceRequest` no POST | `MeasurementContinuousServiceResponse[]` / response individual | `200` consulta; `201` direto ou `202` |
| PUT | `/{measurementId}/services/{continuousServiceId}/destination` | `UpdateMeasurementServiceDestinationRequest` com `expectedVersion` | `MeasurementContinuousServiceResponse` | `200` ou `202`; destino persistido |
| PUT | `/{measurementId}/versions/{versionId}/items/{itemId}/service` | `AssignMeasurementItemServiceRequest` | `MeasurementContinuousServiceResponse` | `200` ou `202`; somente DRAFT atual |
| GET | `/{measurementId}/contract-compositions` | path UUID | `MeasurementCompositionState[]` | `200`; estados oficiais/pedidos independentes do aceite |
| POST | `/{measurementId}/contract-compositions/apply` | sem payload obrigatório | `MeasurementCompositionState[]` | `200`; cada origem informa aplicação/pedido/falta de alçada |
| GET | `/{measurementId}/accumulated/pdf` | path UUID | PDF attachment | `200`; streaming, MEASUREMENTS.EXPORT |
| GET | `/{measurementId}/accumulated/spreadsheet` | path UUID | XLSX attachment | `200`; relatório acumulado, não importável |
| GET | `/{measurementId}/accumulated/spreadsheet/preview` | path UUID | PDF inline | `200`; mesmo conteúdo acumulado |
| POST | `/{measurementId}/versions/{versionId}/items/{itemId}/contract-service` | query opcional `justification` | `MeasurementItemConversionResponse` | `200` |
| GET | `/{measurementId}/versions/{versionId}/services` | path UUIDs | `MeasurementServiceGroupResponse[]` | `200`; exige `MEASUREMENTS.READ`; IDs estáveis dos grupos de serviço da versão aceita; `403`/`404` em Problem Details |
| POST | `/{measurementId}/versions/{versionId}/services/{measurementServiceId}/contract-compositions` | `ComposeMeasurementServiceRequest` | `MeasurementContractCompositionResponse` | `201` direto, `200` idempotente ou `202` por aprovação; `400`/`403`/`404`/`409` em Problem Details |

Tipos de upload de medição: `EVIDENCE`, `SPREADSHEET`.

Na listagem, o tenant vem exclusivamente do JWT. Não envie `companyId` pela URL.

| Parâmetro | Tipo | Regra |
|---|---|---|
| `page` | inteiro | índice iniciado em zero; padrão `0` |
| `size` | inteiro | entre `1` e `100`; padrão `20` |
| `sort` | string repetível | `<campo>,asc` ou `<campo>,desc`; campos: `measuredOn`, `reference`, `status`, `createdAt`, `updatedAt`; padrão `measuredOn,desc` |
| `workId` | UUID | obra exata da empresa autenticada |
| `contractId` | UUID | contrato exato da empresa autenticada |
| `reference` | string | busca parcial, sem distinção entre maiúsculas e minúsculas |
| `status` | `MeasurementStatus` | situação exata da medição |
| `measuredOnFrom` | `YYYY-MM-DD` | primeira data de medição, inclusiva |
| `measuredOnTo` | `YYYY-MM-DD` | última data de medição, inclusiva; não pode ser anterior a `measuredOnFrom` |

`title` é obrigatório na criação: texto não vazio de 1 a 200 caracteres. Ele é o nome legível da medição e não substitui `reference`, que permanece um código opcional. `workId` e `contractId` são opcionais; porém, nunca envie `contractId` sem `workId`, e o contrato deve pertencer à obra. Envie, por exemplo, `"title": "Medição de estrutura — setembro"` sem os dois vínculos para iniciar uma medição avulsa.

`MeasurementResponse` contém `id`, `workId` opcional, `contractId` opcional, `title`, `reference`, `description`, `measuredOn`, `status`, `createdAt` e `updatedAt`. Ele é usado tanto nos itens de `GET /` como em `MeasurementDetailsResponse.measurement` de `GET /{measurementId}` e em `MeasurementChangeResponse.measurement` de `POST /`. Portanto, exiba o título em listagens, detalhe e retorno imediato de criação/aprovação, sem buscar outro campo ou montar um nome no cliente. Parâmetros inválidos retornam `400 application/problem+json`; ausência de `MEASUREMENTS.READ` retorna `403 application/problem+json`. Ambos seguem o Problem Details padronizado e podem incluir `correlationId`.

#### Histórico e detalhe de versões

`GET /{measurementId}` continua retornando somente a versão operacional mais recente em `latestVersion`. Para a trilha completa, chame `GET /{measurementId}/versions`: a resposta é uma lista em ordem decrescente de `versionNumber`, com `id`, `versionNumber`, `previousVersionId`, `status`, `externalAcceptanceOn`, `createdAt`, `updatedAt`, `totalWeightKg`, `grossAmount`, `discountAmount` e `netAmount`. Os quatro totais são textos decimais canônicos e correspondem exclusivamente aos itens oficiais ativos daquela versão.

Quando o usuário abrir uma entrada da trilha, chame `GET /{measurementId}/versions/{versionId}`. A resposta traz `measurement`, `version`, `items`, `services`, desconto e os totais oficiais da **versão selecionada**, mesmo que exista uma revisão mais nova. Não resolva versões por UUID no cliente nem tente recompor totais localmente. As consultas de histórico e detalhe retornam `200` para uma versão pertencente ao tenant; `403` é falta de `MEASUREMENTS.READ` e `404` cobre medição/versão fora do tenant ou inexistente.

Uma revisão comum após `ACCEPTED` permanece incremental e não copia itens aceitos. Use `GET /{measurementId}/accumulated` para a grade contínua: aceitos anteriores e propostas atuais estão no mesmo corpo, com origem, referência de leitura, editabilidade e totais oficiais separados. O frontend não soma versões. Histórico e detalhe por versão continuam disponíveis.

A consulta de serviços, a grade e os relatórios acumulados excluem somente sobras de migração logicamente retiradas pela V83. Serviços intencionalmente vazios continuam presentes. Títulos repetidos não autorizam união: reutilize `continuousServiceId` nas novas atividades. Organize serviço → atividades → `acceptedTotals`/`pendingTotals` do serviço → totais gerais oficiais. PDF/XLSX incluem esses subtotais sem modificar os relatórios históricos por versão.

`GET /{measurementId}/versions/{versionId}/services` devolve os grupos congelados daquela versão (`measurementServiceId`). A identidade entre avanços é `continuousServiceId`, criada/consultada em `/{measurementId}/services` e enviada nos próximos itens. Título nunca define destino ou vínculo financeiro.

Para vincular depois, chame `PUT /{measurementId}/link` com `{"workId":"uuid","contractId":"uuid-opcional","justification":"..."}`. A operação exige `MEASUREMENTS.UPDATE`, deriva a empresa do JWT, valida toda a cadeia e pode responder `202`; nesse caso, o dado oficial permanece inalterado e a resposta traz somente `changeRequestId`/`authorizationMode`. Uma obra já vinculada não pode ser trocada. É permitido vincular somente a obra primeiro e acrescentar o contrato depois; um contrato já vinculado não pode ser substituído silenciosamente.

Antes de existir obra, o frontend pode criar/consultar a medição, incluir itens e desconto, anexar evidências, exportar PDF/XLSX e registrar o fluxo de envio/aceite externo. `FINALIZE`, conversão de item em serviço contratual e qualquer operação financeira exigem obra e contrato; a API responde `409 application/problem+json` quando faltar o vínculo.

#### Formação do valor do contrato por medição

`ContractResponse.pricingMode` é `FIXED` para preço negociado e `MEASUREMENT_BASED` para preço formado progressivamente por medições aceitas. No primeiro caso a composição é um `CONTRACT_ADDITION`; no segundo é `MEASUREMENT_PRICING`. O modo é escolhido na criação do contrato e não deve ser inferido no cliente.

A escolha do destino é persistida no serviço contínuo antes do aceite. O workflow de aceite, o vínculo posterior com contrato e a definição oficial do destino coordenam a composição dos incrementos aceitos, com autorização própria de SERVICES. Preparar um rascunho não altera valor comercial. Consulte `/{measurementId}/contract-compositions` para distinguir aplicação, aprovação pendente e falta de destino/alçada. Use o [guia de medição contínua](FRONTEND-MEASUREMENT-CONTINUOUS.md) para o fluxo principal; o frontend não deve criar serviços ou recalcular valores para reconstruir essa composição.

O endpoint explícito de composição por grupo de versão continua disponível. Após aceitar uma versão, `GET /{measurementId}/versions/{versionId}/services` retorna `MeasurementServiceGroupResponse.id`, estável para aquela origem aceita. Entre avanços, reutilize `continuousServiceId`; nunca use `serviceTitle` como chave. Na operação explícita, envie `CREATE_NEW_SERVICE` sem `contractServiceId`, ou `ADD_TO_EXISTING_SERVICE` com o serviço de destino:

```json
{
  "action": "ADD_TO_EXISTING_SERVICE",
  "contractId": "uuid-do-contrato-vinculado",
  "contractServiceId": "uuid-do-servico-de-destino",
  "justification": "Complemento mensal aceito pelo cliente"
}
```

A versão precisa estar `ACCEPTED`, e a medição já precisa estar vinculada ao mesmo contrato. Uma repetição retorna `200` com a composição existente, sem duplicar valores. Em `202`, só `changeRequestId` e `authorizationMode` são oficiais: não altere totais ou saldo no cliente até a aprovação. `grossAmount`, `netAmount` e `totalWeightKg` são textos decimais canônicos e calculados no servidor; o desconto de cabeçalho é rateado pelo backend por valor bruto e ordem estável. `ContractServiceResponse.measurementCompositionAmount` soma as composições aceitas, e `effectiveNetAmount` é valor-base líquido mais composições. O frontend não calcula desconto, saldo ou rateio.

#### Projeção financeira acumulada da medição

`GET /{measurementId}/financial-summary` preserva os cinco valores e acrescenta `remainingToReceive`, `financialAdjustmentAmount`, `acceptedUncomposedAmount`, `financialAvailability` e `asOf` UTC. `services[]` consolida por `continuousServiceId` e contém `compositionIds`; IDs singulares legados ficam nulos quando há várias origens. Dinheiro é string decimal canônica. Exige `MEASUREMENTS.READ`, `BILLING.READ` e `RECEIVABLES.READ`.

A consulta acumulada exige `MEASUREMENTS.READ`; sem contrato ou permissão financeira, informa `NO_CONTRACT` ou `NOT_AUTHORIZED` com financeiro nulo, mantendo itens/totais documentais. `PARTIALLY_COMPOSED` indica aceito ainda não aplicado. Origem aceita sem composição não é faturável.

Para faturamento originado em medição, envie `measurementContractCompositionId` junto de `contractServiceId` na alocação `SERVICE`. A mesma transação valida limite por composição e contrato. NF referencia a alocação; baixas são atribuídas e persistidas por alocação com fechamento exato dos centavos e resíduos limitados à capacidade. Estorno copia as parcelas negativas da baixa original. Somente a base negociada sem origem pode omitir o ID de composição.

Versões não aceitas e `202` não alteram saldo comercial ou financeiro. Aceite e vínculo posterior coordenam somente incrementos aceitos com destino persistido e autorização SERVICES própria. Consulte `/{measurementId}/contract-compositions` para distinguir aplicação, pedido pendente, falta de destino e falta de alçada. Correções resolvem a origem vigente; redução com faturamento ativo exige reversão antes do aceite.

O resumo simples é **Valor total da medição (aceito), Já recebido e Falta receber**. Exiba `acceptedAmount`, `receivedAmount` e `remainingToReceive` sem calcular no frontend. O terceiro inclui aceito não faturado e faturado ainda devido, após ajustes. Créditos e renegociação preservam a origem e não geram cash fictício; consulte o [guia atual](FRONTEND-MEASUREMENT-CONTINUOUS.md).

Relatórios completos: `GET /{measurementId}/accumulated/pdf`, `/accumulated/spreadsheet` e `/accumulated/spreadsheet/preview`. Prévia em PDF inline do mesmo conteúdo acumulado. Exigem `MEASUREMENTS.EXPORT`, streaming, tenant e auditoria. O XLSX acumulado é relatório e é rejeitado pelo importador; o XLSX por versão sincroniza somente a DRAFT atual.

`GET /{measurementId}/versions/{versionId}/spreadsheet/preview` devolve a prévia oficial em PDF dos mesmos dados da planilha, para exibição no navegador. O PDF usa a mesma marca privada (logo ou nome da empresa emitente), título centralizado e grupos consecutivos `SERVIÇO: {serviceTitle}` da exportação XLSX; isso é somente apresentação, sem novo payload. Trate a resposta como `Blob` PDF; ela usa `Content-Disposition: inline` e não cria URL pública. A chamada exige `MEASUREMENTS.EXPORT`, mantém isolamento por tenant e registra a exportação. Ausência de permissão ou de medição/versão no tenant retorna `403` ou `404` em `application/problem+json`.

#### Importação e sincronização de XLSX

Para sincronizar itens de uma versão `DRAFT`, comece por `POST /{measurementId}/versions/{versionId}/items/import/preview` com `multipart/form-data` e o campo `file`. Use uma planilha exportada pela própria API: ela traz uma coluna oculta **Item ID**, que é a única chave estável de cada linha. O frontend não deve usar `itemNumber`, título do serviço ou atividade como chave. Para criar uma linha, deixe o **Item ID** vazio; para alterar ou mover uma linha de serviço, mantenha o ID que veio na exportação. Linha ativa que desaparecer do arquivo será apresentada como `INACTIVATE` e, quando confirmada, ficará inativa — nunca é apagada fisicamente.

A exportação XLSX oficial agora apresenta o título cadastrado centralizado e, à esquerda, a logo privada da empresa emitente ou o nome fantasia/razão social quando não houver logo utilizável. Itens consecutivos são separados por linhas visuais mescladas `SERVIÇO: {serviceTitle}`. Essas linhas são apenas apresentação: não devem ser editadas nem convertidas em itens. Cada linha de item continua trazendo **Serviço** preenchido e a coluna oculta **Item ID**; o mesmo arquivo agrupado pode ser reenviado ao fluxo de importação.

A prévia não altera dado oficial e devolve `spreadsheetHash`, `valid`, `lineErrors`, `differences` (`CREATE`, `UPDATE`, `MOVE_SERVICE`, `REORDER`, `INACTIVATE`) e os totais oficiais simulados. Corrija qualquer linha com erro e só então peça confirmação explícita. O frontend deve reenviar o **mesmo arquivo** para `POST .../items/import/confirm`, junto de `spreadsheetHash` retornado na prévia. Se o arquivo foi modificado, a API devolve `409`; gere outra prévia. Em aplicação direta, `200` devolve todos os itens ativos, a versão e totais oficiais. Sem alçada direta, `202` devolve somente `changeRequestId` e `authorizationMode`: nada oficial muda até a aprovação.

Importação é permitida somente em versão `DRAFT`. Para versão aceita, enviada, pendente de aceite ou finalizada, crie antes uma nova revisão com `POST /{measurementId}/revisions`, exporte essa revisão e importe nela. A API recalcula pesos, valores, desconto e totais; fórmulas/colunas de total do Excel não são aceitas como autoridade.

Para excluir, chame `DELETE /{measurementId}` e envie `justification` somente quando houver uma explicação operacional. A API nunca apaga o registro: muda o status para `CANCELLED` e preserva versões, itens, anexos e auditoria. Medição sem contrato — avulsa ou vinculada apenas à obra — pode ser cancelada. Se `contractId` estiver preenchido, a resposta é `409 application/problem+json`. Com alçada direta a resposta é `204`; com solicitação de aprovação é `202` com `changeRequestId` e `authorizationMode`, e o status oficial continua inalterado até a aprovação.

No detalhe da medição, valores financeiros são sempre strings decimais canônicas — nunca `number`: `discountValue`, `grossAmount`, `discountAmount`, `netAmount`, `items[].unitPrice`, `items[].totalAmount` e `services[].grossAmount`. O peso oficial também é texto decimal de quatro casas em `totalWeightKg`, `items[].totalWeightKg` e `services[].totalWeightKg`. Exemplos: `"1250.50"`, `"50.00"`, `"25.00"` e `"1636.0000"`. Preserve-os como texto ou decimal de precisão arbitrária no cliente; não os converta para ponto flutuante.

`MeasurementDetailsResponse.services` resume somente as linhas oficiais ativas da versão exibida, agrupadas por `serviceTitle` e ordenadas pelo título do serviço: `serviceTitle`, `itemCount`, `grossAmount` e `totalWeightKg`. `totalWeightKg` será `null` se aquele serviço contiver exclusivamente linhas de m², metro linear ou unidade. O desconto de cabeçalho **não é rateado** entre serviços: use `grossAmount` por serviço e os totais `discountAmount`/`netAmount` da medição. Se houver necessidade futura de líquido por serviço, ela dependerá de uma regra oficial de rateio publicada pelo backend.

Na criação de item (`POST /{measurementId}/versions/{versionId}/items`), envie `serviceTitle` para agrupar visualmente atividades do mesmo serviço e `measuredOn` para a data da atividade. `unitPrice` é obrigatório e deve ser texto decimal positivo com duas casas, por exemplo `"unitPrice": "25.00"`. O backend rejeita número JSON, `double`/`float`, zero, valor negativo e formato não canônico.

O frontend deve calcular a prévia de cada linha conforme `chargeType` e pode enviar `previewTotalWeightKg` (quatro casas) e `previewTotalAmount` (duas casas). Esses campos não são autoridade: o backend recalcula com precisão decimal e responde `400` quando a prévia divergir. Para `SQUARE_METER`, `LINEAR_METER` e `UNIT`, envie `previewTotalWeightKg` omitido ou `"0.0000"`. Para `UNIT`, `unitQuantity` decimal positiva (quatro casas no máximo) é obrigatória e calcula `previewTotalAmount = unitQuantity × unitPrice`; área, metragem e parâmetros de quilograma devem ser `null`/omitidos. Para `SQUARE_METER`, `LINEAR_METER`, `KILOGRAM_PER_SQUARE_METER` e `KILOGRAM_PER_LINEAR_METER`, `unitQuantity` é opcional e representa somente peças/unidades executadas — por exemplo, uma curva instalada. Quando enviada, ela é decimal positiva com no máximo quatro casas, mas **não** altera fórmula, peso, valor, prévias ou totais oficiais. Use sempre os totais devolvidos pela API após salvar.

Para inserir uma atividade entre linhas já existentes, use `POST /{measurementId}/versions/{versionId}/items/insert`; não faça vários `PUT` para trocar números. Envie o `insertAfterItemId` da linha ativa imediatamente anterior e o objeto `item`, sem `itemNumber`: a API calcula a posição, desloca a sequência ativa inteira em uma só transação e devolve o novo item, a versão e totais oficiais em `201`. Itens inativos não entram na sequência. Em `202`, a API retorna somente `changeRequestId` e `authorizationMode`; nenhuma posição ou total oficial foi modificado.

Para mover várias linhas ou reagrupar atividades entre serviços, use `PUT /{measurementId}/versions/{versionId}/items/reorder` na DRAFT atual. Envie **todos** os itens ativos, uma única vez cada, já na ordem final; em cada entrada informe `itemId`, o novo `serviceTitle` e, opcionalmente, `continuousServiceId` para manter/mover a identidade contínua oficial. Não envie `itemNumber`: a API atribui `1, 2, 3...` internamente e preserva os itens inativos com seus números históricos. A operação inteira é atômica. A resposta direta `200` traz a lista ordenada, a versão e os totais oficiais; em `202`, traz somente `changeRequestId` e `authorizationMode`, sem qualquer alteração oficial.

#### Correção de itens e revisões

Enquanto a versão estiver em `DRAFT`, use `PUT /{measurementId}/versions/{versionId}/items/{itemId}` para substituir os dados de uma linha, com o mesmo formato de `CreateMeasurementItemRequest`. Para retirar uma linha rascunho, use `DELETE /{measurementId}/versions/{versionId}/items/{itemId}?justification=...`; a justificativa é opcional e aceita até 2.000 caracteres. A API não faz exclusão física: devolve a linha com `active: false`, preserva a referência de importação XLSX, auditoria e histórico, e a remove da grade normal, dos totais, exportações e relatórios oficiais. Um novo item pode reutilizar o número de uma linha inativa.

Com alçada direta, a resposta é `200` e contém `item` (com `active: false`), `version` e os totais oficiais recalculados (`totalWeightKg`, `grossAmount`, `discountAmount` e `netAmount`). Na mesma transação, a API renumera todos os itens ainda ativos pela ordem oficial para a sequência contínua `1..N`; o item inativo mantém seu número histórico e não participa mais da sequência. Portanto, após um `200`, recarregue/use a grade oficial e **não** chame `items/reorder` como segunda etapa. Quando a ação exigir aprovação, a resposta é `202` com `changeRequestId` e `authorizationMode`; mantenha item, numeração e totais atuais até a aprovação. Para versões `ACCEPTED`, `SENT` ou `PENDING_ACCEPTANCE`, o `DELETE` retorna `409 application/problem+json`: crie uma revisão corretiva e envie `action: "VOID"`, pois a versão histórica permanece congelada.

Itens de versão `ACCEPTED` nunca são alterados. Para corrigir, substituir ou anular um deles, use `POST /{measurementId}/versions/{versionId}/items/{itemId}/corrective-revision`. A operação cria uma nova versão `DRAFT`, copia as linhas da última versão aceita e liga as cópias a seus itens de origem por `sourceItemId`. Envie `"action": "REPLACE"` e todos os campos da linha para corrigir/substituir, ou `"action": "VOID"` e `justification` para anular a linha na nova revisão. Em uma substituição `UNIT`, `unitQuantity` continua obrigatória e positiva. O original aceito permanece congelado. Uma resposta direta retorna `201`, `item`, `version` e os totais oficiais `totalWeightKg`, `grossAmount`, `discountAmount` e `netAmount`, todos textos decimais canônicos; sob aprovação, retorna somente `202`, `changeRequestId` e `authorizationMode` até a decisão.

Quando o aceite externo for rejeitado, envie `POST /{measurementId}/versions/{versionId}/workflow` com `"action": "RECORD_EXTERNAL_ACCEPTANCE"`, `"externallyAccepted": false`, `externalAcceptanceOn`, `externalAcceptanceNotes` não vazio e, opcionalmente, `correctionItemIds`. Cada ID deve ser de um item **ativo** da própria versão; a API não aceita item de outra versão/empresa, item inativo ou ID repetido. A resposta e o detalhe passam a retornar `latestVersion.externalAcceptanceNotes` e `latestVersion.correctionItemIds`, para a tela destacar o motivo e as linhas que precisam de correção. Esses dados pertencem à versão rejeitada e não podem ser sobrescritos. Depois de criada a revisão, `GET /{measurementId}` também expõe `correctionRequest` com o `rejectedVersionId`, motivo, data e itens sinalizados da rejeição de origem, para que a tela continue mostrando o contexto mesmo após recarregar a nova grade `DRAFT`.

Após uma rejeição, `POST /{measurementId}/revisions` cria uma nova versão `DRAFT` ligada por `previousVersionId` à versão rejeitada. Em aplicação direta, retorna `201`, a nova versão e `items` com as cópias oficiais de todos os itens ativos da versão rejeitada, em ordem de item. Cada cópia preserva a origem por `sourceItemId`; a versão rejeitada permanece intacta. Em `202`, retorna somente o identificador da solicitação e o modo de autorização, sem criar versão ou itens oficiais. A grade deve editar a nova versão e reenviar pelo fluxo normal de aceite.

`POST /{measurementId}/revisions` também continua sendo a revisão incremental após uma versão aceita. Ela retorna `201` quando criada diretamente e `202` quando depende de aprovação — não use mais `200` como status de sucesso dessa rota.

```json
{
  "itemNumber": 1,
  "serviceTitle": "Serviço linha de vinhaça",
  "activity": "Fabricar",
  "description": "Curva inox 40 polegadas",
  "measuredOn": "2026-06-04",
  "chargeType": "KILOGRAM_PER_LINEAR_METER",
  "linearMeters": 12,
  "unitQuantity": 1,
  "kilogramsPerLinearMeter": 128,
  "unitPrice": "3.00",
  "previewTotalWeightKg": "1536.0000",
  "previewTotalAmount": "4608.00"
}
```

Exemplo de item por unidade:

```json
{
  "itemNumber": 2,
  "serviceTitle": "Instalação de válvulas",
  "activity": "Instalar válvulas",
  "measuredOn": "2026-06-04",
  "chargeType": "UNIT",
  "unitQuantity": 12.5,
  "unitPrice": "25.00",
  "previewTotalWeightKg": "0.0000",
  "previewTotalAmount": "312.50"
}
```

### 2.11 Controle de faturamento — JWT de empresa

Base: `/api/v1/company/billing-control/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | filtros + paginação | `PageResponse<BillingControlSummaryResponse>` | `200` |
| GET | `/{contractId}` | path UUID | `ContractBillingControlResponse` | `200` |

Filtros: `contractId`, `status`, `query`. Ordenação: `name`, `reference`, `status`, `createdAt`.

No detalhe `GET /{contractId}`, todos os valores monetários são strings decimais canônicas, sem símbolo de moeda ou formatação local (por exemplo, `"99.90"`). Não os converta para `number` no cliente. Os totais oficiais do contrato são `serviceSubtotal` (subtotal dos serviços antes do ajuste contratual), `contractDiscountAmount` (desconto/ajuste oficial do contrato) e `contractNetAmount` (líquido após o ajuste). Também retornam `activeAllocatedAmount`, `availableAmount`, os valores de `services` e `allocations`, incluindo `registeredInvoiceAmount` e `availableInvoiceAmount`.

A listagem paginada também usa strings em `contractNetAmount`, `activeAllocatedAmount` e `availableAmount`. Os serviços em `GET /api/v1/company/contracts/{contractId}/services` expõem a base `netAmount`, a composição aceita `measurementCompositionAmount` e a posição oficial `effectiveNetAmount`. Não reconstruir o valor do contrato somando medições no frontend. Evidências e ajustes desta revisão: [F05-14](F05-14-FINANCIAL-INVARIANTS-REVIEW.md).

### 2.12 Parcelas de faturamento — JWT de empresa

Base: `/api/v1/company/billing-installments`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateApprovedBillingInstallmentRequest` | `BillingInstallmentApprovalResponse` | `201` ou `202` |
| POST | `/{installmentId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |

Regras importantes para a UI:

- `SERVICE` exige `contractServiceId` e valor positivo.
- Em `SERVICE`, `measurementContractCompositionId` é obrigatório para valores originados de composição aceita de medição; só a base negociada disponível pode omiti-lo. A composição deve pertencer ao mesmo tenant, `contractServiceId` e contrato, sem ultrapassar sua capacidade aceita vigente.
- `CONTRACT_DISCOUNT` não aceita serviço e exige valor negativo.
- A parcela deve ter valor líquido positivo.
- Alocações não podem exceder contrato, serviço ou desconto contratual.
- Uma parcela aprovada cria um `Receivable` individual; `financialGroupId` agrupa títulos relacionados.

`allocations[].allocatedAmount` e `receivable.originalAmount` são strings decimais canônicas com duas casas, inclusive ajustes negativos como `"-20.00"`. Serviço cancelado não admite novo faturamento (`409`). Edição/desconto de serviço não pode reduzi-lo abaixo do seu comprometimento aprovado, mesmo havendo saldo em outro serviço do contrato. Sua base negociada também precisa cobrir as alocações sem origem de medição; uma composição aceita não pode encobrir esse excesso. Desconto contratual não pode ser reduzido/revertido abaixo das alocações negativas ativas que ele sustenta; estorne/cancele primeiro essas alocações pelo fluxo oficial.

### 2.13 Notas fiscais — JWT de empresa

Base: `/api/v1/company/invoices`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateRegisteredInvoiceRequest` | `InvoiceRegistrationResponse` | `201` ou `202` |
| POST | `/{invoiceId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |
| POST | `/{invoiceId}/attachments` | multipart: `attachmentType`, `file` | `InvoiceAttachmentResponse` | `200` |

Anexos aceitos: `PDF`, `XML`. A NF pertence a um único contrato e cada item consome somente a capacidade da `BillingAllocation` informada. Registrar NF não registra recebimento.

`invoice.totalAmount` e `invoice.items[].allocatedAmount` são strings decimais canônicas. A mesma alocação pode ser documentada parcialmente em várias NFs até seu limite; a NF não consome novamente o saldo contratual. A situação ativa e o saldo da alocação são verificados sob lock transacional, também na aprovação.

### 2.14 Impostos da NF — JWT de empresa

Base: `/api/v1/company/invoices/{invoiceId}/tax-obligations`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/` | `CreateTaxObligationRequest` | `TaxObligationCreationResponse` | `201` ou `202` |

Tratamentos:

- `PAYABLE_BY_COMPANY`: cria `Payable` de escopo obra e devolve `payableId`.
- `WITHHELD_BY_CUSTOMER`: exige `receivableId` compatível e reduz o valor líquido a receber.
- `INFORMATIONAL`: apenas registra informação; não cria conta a pagar nem baixa.

`taxObligation.amount` é string decimal canônica, não número JSON.

### 2.15 Contas a receber e recebimentos — JWT de empresa

Base: `/api/v1/company/receivables`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| POST | `/{receivableId}/settlements` | `CreateReceivableSettlementRequest` | `ReceivableSettlementApprovalResponse` | `201` ou `202` |
| POST | `/{receivableId}/settlements/{settlementId}/reversal` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `201` ou `202` |
| GET | `/{receivableId}/balance` | path UUID | `ReceivableBalanceResponse` | `200` |
| POST | `/{receivableId}/credits` | `CreateReceivableCreditRequest` | `ReceivableCreditResponse` | `201` ou `202`; crédito justificado sem baixa fictícia |
| POST | `/{receivableId}/credits/{creditId}/reversal` | `CancelFinancialRecordRequest` | `ReceivableCreditResponse` | `201` ou `202`; estorno imutável |
| GET | `/{receivableId}/renegotiations` | path UUID | `ReceivableRenegotiationResponse` | `200`; títulos descendentes e históricos |
| POST | `/{receivableId}/renegotiations` | `RenegotiateReceivableRequest` | `ReceivableRenegotiationResponse` | `200` ou `202`; somente saldo ainda devido |
| POST | `/{receivableId}/renegotiations/reversal` | `CancelFinancialRecordRequest` | `ReceivableRenegotiationResponse` | `200` ou `202`; reabre original após reversão de efeitos descendentes |

Recebimento pode ser parcial. Estorno cria lançamento reverso; não apaga a baixa original.

`settlement.amount` e os campos monetários de `ReceivableBalanceResponse` (`originalAmount`, `withheldAmount`, `creditedAmount`, `netReceivableAmount`, `confirmedSettlementAmount`, `outstandingAmount`) são strings decimais canônicas com duas casas. As rotas de criação de parcela, NF, imposto e baixa documentam `201` direto ou `202` pendente; cancelamentos de parcela/NF usam `200`/`202`, e estorno de baixa usa `201`/`202`. `400`, `403`, `404` e `409` usam Problem Details. O `202` não tem efeito financeiro oficial.

Crédito aparece em `balance.creditedAmount` e não representa recebimento nem libera faturamento. Renegociação preserva parcela, grupo financeiro e alocações de origem; não some valores originais de títulos históricos renegociados com os títulos descendentes para formar saldo. Use a projeção oficial de medição e o [guia de integração](FRONTEND-MEASUREMENT-CONTINUOUS.md).

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
| `UpdateFinalCustomerRequest` | `customerType*`, `name*`, `document*`, `justification` |
| `ContactRequest` | `name*`, `email`, `phone`, `primaryContact` |
| `AddressRequest` | `label`, `postalCode`, `street*`, `number`, `complement`, `district`, `city*`, `state*`, `country`, `primaryAddress` |
| `CreateWorkRequest` | `finalCustomerId*`, `name*`, `reference`, `executionLocationType*`, `executionAddress`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification` |
| `UpdateWorkRequest` | `name*`, `reference`, `executionLocationType*`, `executionAddress`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification`; não aceita `finalCustomerId` |
| `CreateContractRequest` | `workId*`, `reference`, `name*`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification` |
| `UpdateContractRequest` | `reference`, `name*`, `status*`, `startedOn`, `expectedCompletionOn`, `completedOn`, `justification`; não aceita `workId` |
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
| `CreateMeasurementRequest` | `workId` opcional, `contractId` opcional (exige `workId` compatível), `title*` (texto não vazio, 1–200 caracteres), `reference`, `description`, `measuredOn`, `justification` |
| `LinkMeasurementRequest` | `workId*`, `contractId` opcional, `justification` |
| `CreateMeasurementItemRequest` / `UpdateMeasurementItemRequest` | `itemNumber* (>0)`, `serviceTitle*`, `activity*`, `description`, `measuredOn*`, `chargeType*`, `areaSquareMeters`, `linearMeters`, `kilogramsPerSquareMeter`, `kilogramsPerLinearMeter`, `unitQuantity` (decimal positiva, até quatro casas; obrigatória e usada na fórmula em `UNIT`, opcional e somente operacional nos demais tipos), `unitPrice*` (string decimal positiva com duas casas), `previewTotalWeightKg` (string opcional com quatro casas), `previewTotalAmount` (string opcional com duas casas), `justification` |
| `InsertMeasurementItemRequest` | `insertAfterItemId*` da linha ativa e `item*` (`MeasurementItemDataRequest`, com os campos de item exceto `itemNumber`) |
| `ReorderMeasurementItemsRequest` | `items*[]`: todos os itens ativos, exatamente uma vez e na ordem final; cada entrada contém `itemId*` e `serviceTitle*`. `justification` opcional. |
| `CorrectAcceptedMeasurementItemRequest` | `action*` (`REPLACE` ou `VOID`), os campos da linha em `REPLACE` — inclusive `unitQuantity` positiva obrigatória em `UNIT` ou opcional como registro operacional nos demais tipos — e `justification*` |
| `CreateMeasurementDiscountRequest` | `discountType*`, `discountValue* (>0)`, `justification` |
| `AdvanceMeasurementWorkflowRequest` | `action*`, `externallyAccepted`, `externalAcceptanceOn`, `externalAcceptanceNotes`, `justification` |
| `CreateMeasurementRevisionRequest` | `justification` |

Os campos de cálculo exigidos variam conforme `chargeType`; o frontend deve mostrar apenas os campos aplicáveis.

### Faturamento, NF e recebimento

| DTO | Campos |
|---|---|
| `CreateApprovedBillingInstallmentRequest` | `contractId*`, `reference`, `description`, `issuedOn`, `dueOn*`, `financialGroupId`, `allocations*[]`, `justification` |
| `BillingAllocationRequest` | `contractServiceId`, `measurementContractCompositionId` opcional (somente `SERVICE`, mesma composição/serviço/contrato), `type*`, `allocatedAmount*` |
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

- `MeasurementDetailsResponse`: `measurement` (`MeasurementResponse`, incluindo `title`), versão mais recente, itens, desconto, `totalWeightKg` e totais bruto/líquido. Peso e valores são strings decimais canônicas.
- `MeasurementItemResponse`: `serviceTitle`, `activity`, `description`, `measuredOn`, parâmetros da fórmula e `unitQuantity`; em `UNIT`, ela compõe a fórmula. Nos demais tipos, quando preenchida, é somente a quantidade operacional de peças/unidades executadas e não altera valores ou peso. `unitPrice`, `totalWeightKg` e `totalAmount` são strings decimais canônicas. Em `UNIT`, `totalWeightKg` é `null`.
- `MeasurementItemReorderResponse`: em `200`, `items` na ordem oficial, `version` e totais oficiais; em `202`, somente `changeRequestId` e `authorizationMode`.
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
- `MeasurementChargeType`: `SQUARE_METER`, `LINEAR_METER`, `KILOGRAM_PER_SQUARE_METER`, `KILOGRAM_PER_LINEAR_METER`, `UNIT`.
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

1. listar ou consultar detalhes individuais de parcelas;
2. listar ou consultar NFs já registradas;
3. listar anexos de NF ou baixar o anexo armazenado;
4. listar contas a receber ou consultar seus dados básicos — existe apenas consulta de saldo por ID;
5. listar baixas/recebimentos de uma conta a receber;
6. listar impostos de uma NF;
7. consultar/editar todos os dados cadastrais da própria empresa;
8. endpoints de gastos/fornecedores/contas a pagar operacionais;
9. relatórios gerais, exportação DOCX e envio de relatório por e-mail. As exportações PDF/XLSX de uma versão de medição já existem e não integram esta lacuna;
10. consulta e PDF/XLSX/prévia acumulados da medição, com aceitos anteriores e avanço atual no mesmo corpo e totais separados;
11. identidade de serviço contínuo entre avanços e escolha persistida de destino contratual antes do aceite;
12. coordenação de composição incremental autorizada no aceite/vínculo posterior, com pendências consultáveis e idempotência também de solicitações;
13. extensão do resumo com saldo total `remainingToReceive`, disponibilidade e `asOf`, tratamento completo de correções/ajustes/estornos e origem obrigatória para faturamento de composições. O resumo atual com cinco campos não conclui essas lacunas.

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
