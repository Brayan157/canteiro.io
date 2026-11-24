# Catálogo da API para arquitetura do frontend

Documento baseado nos controllers, DTOs, segurança e tratamento de erros do backend. Seções de medições, cadeia financeira e privacidade operacional revisadas em 22/10/2026.

Complemento de 26/10/2026: leitura paginada/detalhe básico de recebíveis F05-25 / FRONT-F08-11 na seção 2.15 e no [guia de integração](FRONTEND-RECEIVABLE-READ.md). O export gerado permite atualizar tipos; a publicação na instância local é uma etapa separada.

Histórico contextual de recebimentos/estornos F05-26 / FRONT-F08-15 também na seção 2.15 e em [guia próprio](FRONTEND-RECEIVABLE-SETTLEMENT-HISTORY.md). Leitura apenas, sem nova operação financeira nem publicação da API.

Cobrança por serviço FRONT-F08-19/F05-27 e tributo por valor ou base/alíquota FRONT-F08-20/F05-28 concluídos: 38 testes distintos/8 suítes e 21 reexecuções direcionadas aprovados; Checkstyle e OpenAPI final 3.1.0, 142 paths/243 schemas. [Guia efetivo, hash e provas](FRONTEND-SERVICE-BILLING-AND-TAX.md). Prioridade: base primeiro, depois data oficial de aceite; alíquota até quatro casas e dinheiro HALF_UP em duas casas. Gere os tipos do export definitivo; legado compatível e publicação da instância separada.

Aliases de acesso continuam a ser URLs distintas para a mesma operação de negócio. A cobertura executável é validada por `OpenApiContractIntegrationTest`; confira sempre o OpenAPI da instância publicada antes de gerar tipos.

> Fonte de verdade executável: código Java e OpenAPI da aplicação. Este arquivo descreve somente endpoints que já existem; lacunas estão listadas no final.

> Planejamento não executável: a futura Fase 10 de vínculos, readmissão, aviso prévio, conformidade e documentos de funcionário está detalhada em [FRONTEND-EMPLOYEE-LIFECYCLE-PLANNED.md](FRONTEND-EMPLOYEE-LIFECYCLE-PLANNED.md). Essas rotas ainda não existem e não devem gerar tipos manuais no frontend.

> Medição contínua implementada: grade acumulada, identidade persistida, destino contratual, composição incremental, financeiro e relatórios estão no [guia de integração](FRONTEND-MEASUREMENT-CONTINUOUS.md). Gere tipos somente do OpenAPI executável/exportado.

> Atualização publicada em localhost:8081 em 14/10/2026, com V84 aplicada: `currency` BRL/USD/EUR, `GET /api/v1/company/currencies` e relatórios de dez colunas. Contratos, defaults, imutabilidade, erros, reimportação e hash do OpenAPI vivo estão no [guia de moedas e relatórios](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md). Dinheiro permanece decimal canônico em texto; moeda é código ISO separado. A [F04-41 foi concluída em 15/10/2026](F04-41-LOCAL-PUBLICATION.md), com sincronização/tipos e validação do frontend de moedas e PDF/XLSX reais com filename oficial. Logo real permanece aguardando cadastro pelo usuário/interface do frontend, com fallback seguro.

## 1. Visão geral

O primeiro recorte de privacidade operacional está em validação: a listagem de funcionários agora usa projeção mínima e não aceita filtros/ordenações documentais ou salariais. O perfil completo e a remuneração permanecem no detalhe autenticado. Consulte [o guia de integração e pendências](FRONTEND-PRIVACY-REMEDIATION.md) antes de adaptar telas ou persistir filtros.

Cadastros da Fase 6 concluídos: `GET/POST /api/v1/company/suppliers`, `GET/PUT/DELETE /api/v1/company/suppliers/{id}` e rotas equivalentes em `/api/v1/company/financial-categories`. [Guia de fornecedores/categorias e snapshot F06-01](FRONTEND-PAYABLE-CATALOGS.md).

Contas a pagar operacionais incluem criação, lista e detalhe com escopo WORK/COMPANY e moeda oficial. [Guia de títulos](FRONTEND-PAYABLES.md). O saldo oficial também é consultável em `GET /api/v1/company/payables/{payableId}/balance`. A subrota `/settlements` lista o histórico e registra pagamentos parciais/totais. `POST /settlements/{settlementId}/reversals` registra estorno integral vinculado. [Guia de baixas, estornos e aprovação](FRONTEND-PAYABLE-SETTLEMENTS.md).

Caixa realizado por empresa e por obra em `GET /api/v1/company/cash-flow/realized` e `GET /api/v1/company/cash-flow/works/{workId}/realized`. As duas rotas recebem `from` e `to` e retornam entradas, saídas e variação líquida por moeda. [Contrato de caixa realizado](FRONTEND-REALIZED-CASH.md).

O acumulado por obra está em `GET /api/v1/company/cash-flow/works/{workId}/realized/accumulated`. O realizado por obra, por período ou acumulado, retorna `cashResultPercentage` por moeda quando houver entradas líquidas positivas e acesso às duas fontes. [Contrato do acumulado e do percentual](FRONTEND-REALIZED-CASH.md).

Caixa previsto por vencimento em `GET /api/v1/company/cash-flow/due` e `GET /api/v1/company/cash-flow/works/{workId}/due`. O parâmetro `horizonDays` delimita os próximos vencimentos a partir do dia UTC atual. As rotas retornam vencidos e próximos vencimentos pelo saldo residual oficial, com cobertura por moeda e escopo. [Contrato de caixa previsto](FRONTEND-CASH-DUE.md).

Comparação oficial de pagamentos em `GET /api/v1/company/cash-flow/payments/comparison` e `GET /api/v1/company/cash-flow/works/{workId}/payments/comparison`. Use `from` e `to` para comparar os pagamentos líquidos de estornos com o intervalo anterior de igual duração. [Contrato de comparação](FRONTEND-CASH-PAYMENTS-COMPARISON.md).

As variantes com sufixo `/by-category` detalham a comparação por categoria e moeda, incluindo categorias inativas e `Sem categoria`. Exigem `COSTS_EXPENSES.READ` além das permissões das rotas gerais. [Contrato por categoria](FRONTEND-CASH-PAYMENTS-BY-CATEGORY.md).

O PDF financeiro de empresa e obra está em `GET /api/v1/company/cash-flow/report/pdf` e `GET /api/v1/company/cash-flow/works/{workId}/report/pdf`. Recebe `from`, `to`, `currency` e `horizonDays`, com alçada de exportação por fonte. [Contrato do PDF](FRONTEND-CASH-FLOW-PDF.md).

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

#### 2.5.1 Diário de Obra (F13-02)

Base por obra: `/api/v1/company/works/{workId}/diary`. O backend deriva a empresa da sessão e exige tanto a permissão do papel em `WORK_DIARY` quanto a concessão vigente para a operação naquela obra.

| Operação | Caminho | Acesso |
|---|---|---|
| Consultar fuso | GET `/time-zone` | `READ` e concessão `READ` |
| Configurar fuso IANA | PUT `/time-zone` | `WORKFORCE.MANAGE_USERS`. O fuso fica fixo após o primeiro diário efetivo. |
| Consultar diário do dia | GET `/{date}` | `READ` e concessão `READ` |
| Selecionar equipe | GET `/{date}/employees` | Mesma leitura, com nome e ID operacionais. Candidatos avulsos exigem concessão `REGISTER_OCCASIONAL`. |
| Ler totais oficiais | GET `/time-summary?from=...&through=...` | Mesma leitura. Soma em minutos por funcionário e serviço, com datas inclusivas. |
| Ler revisões | GET `/{date}/revisions` e `/{date}/revisions/{revision}` | Mesma leitura. A lista usa limite e cursor exclusivo `before`. |
| Consultar auditoria da obra | GET `/audit` | Mesma leitura e `AUDIT.READ`. Cada evento retorna `actorDisplayName` com a identificação segura e mantém `actorId`. A lista global de auditoria omite eventos do Diário, cujo acesso depende da obra. |
| Criar ou corrigir | POST ou PUT `/{date}` | Alçada de criação ou edição e concessão `CREATE` ou `UPDATE`. |
| Fechar ou reabrir | POST `/{date}/close` ou `/{date}/reopen` | Alçada de edição e concessão específica `CLOSE` ou `REOPEN`. |

`date`, `from` e `through` são datas locais `YYYY-MM-DD`. Cada comando informa um UUID `operationId`. Uma correção envia a revisão atual em `expectedRevision`, o motivo e as coleções completas de atividades e ocorrências. A atividade pode ser geral ou apontar o `serviceId` pertencente à obra. A contribuição envia um ID estável e `employeeId`, com `minutes` em inteiro positivo ou `start` e `end` completos com deslocamento da obra. Participação avulsa informa `occasionalReason`. O fechamento e a reabertura enviam a versão e motivo, sem coleções. Um fechamento sem execução explícita informa `noExecutionReason`.

O retorno da mutação contém `diaryId`, revisão aplicada ou ID/situação da solicitação, e o indicador de repetição. `201` cria o Diário, `200` confirma aplicação ou um resultado já decidido, e `202` indica solicitação pendente sem alteração no Diário ou nos totais oficiais. A decisão usa `/api/v1/company/change-requests/{id}/approve` ou `/reject`, com permissão de decisão e concessão correspondente na obra. Conflitos de versão, intenção, duplicidade e sobreposição verificável retornam `409`. A área de auditoria e o histórico respeitam a revogação do acesso à obra.

Os comandos mínimos de administração ficam em `/api/v1/company/workforce`. `WORKFORCE.MANAGE_USERS` permite registrar `POST /employments` e `POST /assignments` e encerrá-los via `PUT /employments/{employmentId}/end` e `PUT /assignments/{assignmentId}/end`, com data e versão. `WORKFORCE.MANAGE_ROLES` permite `POST /grants` e `PUT /grants/{grantId}/revoke`. Lotação, vínculo histórico e concessão são registros independentes.

O papel protegido ativo do proprietário inclui as permissões ativas do catálogo `WORKFORCE.MANAGE_USERS`, `WORKFORCE.MANAGE_ROLES`, `WORK_DIARY.READ`, `WORK_DIARY.CREATE_DIRECT` e `WORK_DIARY.UPDATE_DIRECT`. Para proprietários existentes, a API aplica essa regra em tempo de execução, sem regravar atribuições. Os códigos aparecem em `GET /api/v1/auth/me` e os IDs oficiais aparecem na matriz do papel protegido. Operar o Diário exige concessão vigente da operação na obra, inclusive para o proprietário. O modo de assinatura somente consulta continua bloqueando mutações.

Para incluir na equipe a partir da obra selecionada, use `POST /api/v1/company/workforce/assignments/batch` com `workId`, `employeeIds` (de 1 a 100) e `startedOn` (data comum no formato `YYYY-MM-DD`). Requer `WORKFORCE.MANAGE_USERS`. O backend resolve o vínculo de trabalho compatível de cada funcionário e devolve `201` com as lotações abertas. Um reenvio da mesma obra, pessoa e início reutiliza a lotação ainda aberta. O lote é atômico e os resultados devem ser associados por `employeeId`.

Um conflito no lote retorna `409`. Os motivos são `EMPLOYMENT_REQUIRED` quando falta vínculo na data, `OPEN_EMPLOYMENT_REQUIRED` quando o vínculo encontrado já tem fim e `ASSIGNMENT_PERIOD_CONFLICT` quando há sobreposição de lotação. A violação `employeeIds` identifica a pessoa pelo UUID. O cadastro do vínculo com a empresa permanece disponível para registrar a data real de admissão quando necessário.

Para registrar a saída da obra, use `PUT /api/v1/company/workforce/assignments/{assignmentId}/end` com `endedOn` e `expectedVersion`. A saída é o último dia incluído na lotação, que permanece no histórico. Para transferir, encerre a lotação anterior e faça outra com início posterior ao último dia na origem. Encerrar uma lotação por transferência, pedido de demissão ou desligamento registra a saída da obra. A baixa do vínculo com a empresa continua uma ação própria.

#### Horários observados, equipe, paralisação e clima

POST e PUT de conteúdo aceitam os seguintes campos opcionais. A mesma revisão efetiva os retorna nas consultas atual e histórica.

`GET /api/v1/company/works/{workId}/diary/operations` inclui `fieldObservationsSupported: true` a partir da V101. Ative a edição desses campos somente quando a resposta trouxer o valor `true`. A lista de operações efetivas continua governando as ações disponíveis.

| Campo | Escrita | Leitura |
| --- | --- | --- |
| `activities[].timing` | `{start,end?}`. A data é a do Diário. O fim fica vazio enquanto a atividade estiver em andamento. | `{startsAt,endsAt}` em UTC. |
| `activities[].participants` | `[{employeeId,occasionalReason?}]`. Use `entries: []` quando ainda não houver apontamentos. Funcionário avulso precisa da autorização por obra e de justificativa, que pode vir do seu apontamento na atividade. | União sem repetição da equipe informada e dos funcionários com apontamentos. Inclui a referência histórica do vínculo, lotação e justificativa, quando aplicáveis. |
| `occurrences[].stoppage` | `{start,resumedAt?,crewDepartedAt?}`. Informe somente um desfecho. Sem ambos, a paralisação está em andamento. | `{startsAt,resumedAt,crewDepartedAt,status,durationMinutes}`. Situações `ONGOING`, `RESUMED` ou `CREW_DEPARTED_WITHOUT_RESUMING`. Os minutos são calculados até o desfecho observado e ficam nulos enquanto estiver em andamento. |
| `occurrences[].timing` | A partir da V103, `{start,end?}` registra a duração da própria ocorrência, inclusive sem paralisação. Omissão ou `null` preserva o horário anterior na correção. | `{startsAt,endsAt,durationMinutes}`. Sem fim, os minutos ficam nulos. A ocorrência antiga sem horário retorna `timing: null`. Habilite a edição apenas com `occurrenceTimingSupported: true` em `/diary/operations`. |
| `occurrences[].clearStoppage` | `true` corrige a ocorrência para sem paralisação. Deve vir sem objeto `stoppage` e mantém o mesmo ID. | A nova revisão retorna `stoppage: null`. O histórico anterior preserva a paralisação. |
| `weatherConditions` | Lista de condições distintas entre `SUNNY`, `CLOUDY` e `RAIN`. Envie `[]` para limpar. | Lista de condições observadas. Registros antigos sem clima retornam `[]`. |

Horários de entrada usam deslocamento válido no fuso da obra, precisão de minuto e limites do dia local. O instante de início deve ter ocorrido e o desfecho fica dentro desse dia. Em PUT, omitir ou enviar `null` em `timing`, `participants`, `stoppage` ou `weatherConditions` preserva a observação anterior da mesma atividade, ocorrência ou revisão. A lista explícita de participantes substitui a seleção manual, e a leitura sempre inclui a participação comprovada pelos apontamentos existentes. Alterações continuam sujeitas à alçada e versionamento do Diário. Clima, paralisação ou duração da atividade não calculam atraso, não lançam e não deduzem minutos-pessoa.

#### Evidência fotográfica privada

A base é `/api/v1/company/works/{workId}/diary/{date}/photos`. Use `photoEvidenceSupported: true` para detectar a leitura a partir da V102. Na V103, `itemPhotoEvidenceSupported: true` habilita a inclusão nos cartões dos itens salvos. A galeria do dia permite consultar fotos, inclusive as gerais legadas. A resposta de operações continua governando as alçadas.

| Operação | Contrato |
| --- | --- |
| `POST /photos` | `multipart/form-data` com `file`, `operationId` (UUID), `expectedRevision` (inteiro) e exatamente um UUID entre `activityId` e `occurrenceId`. Ausência dos dois ou envio dos dois retorna `400`. O item precisa existir nessa revisão aberta e na mesma obra e data, ou a API retorna `404`. Envie um arquivo por chamada após salvar o Diário. O servidor verifica o formato real e decodifica JPEG ou PNG, até 8 MiB, 6.000 pixels por lado e 16 milhões de pixels no total. Retorna `201` com metadados, ou `200` para reenvio idêntico pelo mesmo autor. Alterar o arquivo, item ou revisão sob o mesmo UUID retorna `409`. |
| `GET /photos` | Array de metadados. Sem filtros, lista todas as fotos visíveis do dia, inclusive de revisões anteriores e fotos gerais. Um filtro `activityId` ou `occurrenceId` sem `revision` lista fotos visíveis do mesmo item em qualquer revisão, desde que o item ainda exista na revisão atual. Cada foto conserva a revisão de origem. `revision` explícita filtra pela revisão de origem e inclui retiradas. `offset` padrão 0 e `limit` padrão 20, de 1 a 100. |
| `GET /photos/{photoId}/content` | Bytes originais e MIME detectado pelo servidor, com `Cache-Control: no-store` e `nosniff`. Use requisição autenticada para carregar o conteúdo. Foto retirada retorna `404`. |
| `PUT /photos/{photoId}/withdraw` | JSON com `expectedRevision` da revisão aberta atual e `reason` obrigatório. Retira a foto da galeria e bloqueia a leitura do arquivo pela API. Preserva origem, autoria, metadados históricos e arquivo privado. Repita com o mesmo autor e motivo para obter a retirada existente. |

Cada objeto da lista contém `id`, `revision` de origem, `activityId`, `occurrenceId`, `contentType`, `sizeBytes`, `uploadedBy`, `uploadedAt`, `withdrawnBy`, `withdrawnAt` e `withdrawalReason`. Fotos gerais legadas retornam ambos os IDs de item nulos e permanecem acessíveis pela galeria e pelo histórico. O vínculo de uma foto não muda quando o item é corrigido, repetido em nova revisão ou removido. A API omite nome original, URL e chave de armazenamento. Os bytes são preservados como evidência, inclusive os metadados que a câmera incluir no arquivo.

Leitura exige `WORK_DIARY.READ` e concessão vigente `READ` na obra. Upload e retirada exigem alçada direta de atualização no Diário e concessão `UPDATE` vigente na obra. Essas ações não abrem solicitação `202`. Uma revisão já fechada só aceita novas ações de foto após reabertura. O anexo referencia a revisão que estava aberta no upload e não cria uma revisão de conteúdo. A perda da concessão bloqueia inclusive o histórico. Upload, retirada e acesso ao conteúdo são auditados.

As consultas administrativas usam `offset` padrão 0 e `limit` padrão 20, de 1 a 100. `GET /employments?employeeId=...` retorna os vínculos históricos. `GET /assignments?workId=...&from=...&through=...` retorna lotações cuja vigência intersecta o intervalo inclusivo, inclusive as encerradas, junto ao nome operacional. Ambas exigem `WORKFORCE.MANAGE_USERS`. `GET /grants?workId=...&userId=...` exige `WORKFORCE.MANAGE_ROLES` e lista concessões, inclusive encerradas e revogadas, com identificação do usuário. O filtro `userId` é opcional.

`GET /api/v1/company/works/{workId}/diary/operations` retorna as operações efetivas do usuário autenticado na obra e o instante UTC da consulta. Combina concessões vigentes e permissões ativas do papel. `mode` informa `DIRECT` ou `REQUEST_APPROVAL` nos comandos que têm alçada de alteração. A operação ausente está indisponível no instante consultado. A API revalida o escopo a cada comando.

O apontamento manual e a consulta de horas usam a mesma fonte efetiva, `work_time_effective`, que seleciona apenas a revisão atual. As revisões anteriores preservam histórico. `totalMinutes` fica nulo sem apontamentos ou declaração explícita de dia sem execução. A integração de planejamento comparativo e a apuração de custos seguem F13-03 a F13-05. [Contrato funcional](SDD-MVP-Gestao-Financeira.md#contrato-funcional-f13-01).

#### 2.5.2 Comparação do Diário com o plano (F13-03)

Na base `/api/v1/company/works/{workId}/diary/{date}`, `PUT /plan` registra a versão diária. Exige concessão `PLAN` na obra e alçada de atualização em `WORK_DIARY`. Envie `operationId`, `expectedVersion` (0 para o primeiro plano), `reason` e `schedules`. Cada linha tem um `serviceId` válido na obra ou representa o trabalho geral. A linha pode informar minutos-pessoa planejados em inteiro e uma janela com início/fim no fuso da obra. Horários que atravessam a virada do dia pertencem às respectivas datas.

Cada marco tem ID, título e data prevista. Para registrar conclusão observada, informe a data e a revisão fechada do Diário da mesma obra que contém atividade do serviço, ou atividade geral para um marco geral. A revisão de plano congela essa referência. A resposta `202` cria uma solicitação pendente. Aprovação revalida autorização, evidência, fuso e versão vigente. O mesmo `operationId` com o mesmo conteúdo devolve a aplicação anterior ou a solicitação correspondente.

`GET /comparison` exige `WORK_DIARY.READ` e concessão `READ` na obra. Por padrão compara o último plano oficial e a revisão efetiva daquela data. `planVersion` e `diaryRevision` selecionam referências históricas explícitas. O resultado distingue plano ausente, Diário ausente e declaração de dia sem execução.

- **Minutos-pessoa** somam contribuições individuais. **Minutos ativos** usam a união dos intervalos, incluindo trabalho simultâneo uma única vez. Duração sem intervalo entra nos minutos-pessoa. Nessa condição, horário e minutos ativos ficam indisponíveis na linha afetada.
- **Atraso de início** usa o primeiro horário real verificável e o início planejado da mesma zona. **Atraso de cronograma** usa dias entre o vencimento do marco e a data da conclusão registrada. As ocorrências da revisão são retornadas uma vez como contexto. Sua presença confirma o registro do motivo informado.

#### 2.5.3 Resultado gerencial do Diário (F13-05)

`GET /api/v1/company/works/{workId}/diary/managerial-result` recebe `currency` (`BRL`, `USD` ou `EUR`), `from` e `to` inclusivos até hoje UTC. Por padrão retorna a obra. Informe `contractId` para detalhar o contrato e adicione `serviceId` para detalhar o serviço. Ambos são validados contra a cadeia da obra e do tenant. Exige concessão `READ_FINANCIAL` na obra e permissões `WORKS.READ` e `REPORTING.READ`.

| Visão | Fontes e data-base | Permissões adicionais |
|---|---|---|
| `projected` | Faturamento líquido da aplicação oficial da cobrança, por alocações ativas na data UTC de registro. Gastos WORK ativos pela data UTC de registro do título. Tributos a pagar pela empresa pela data UTC de registro da obrigação, contados pela origem TaxObligation uma vez. | `BILLING.READ`, `PAYABLES.READ` e `INVOICES.READ` |
| `recordedCash` | Baixas confirmadas de recebíveis, pagamentos e estornos, com o sinal oficial pela data efetiva de cada evento. Atribuição a serviço só quando as linhas de recebimento reconciliam o evento integral com origens exatas. | `RECEIVABLES.READ` e `PAYABLES.READ` |

É um recorte dos fatos atualmente registrados e efetivos no período de cada base. Cancelamentos vigentes saem do previsto. A consulta informa `calculatedAt` em UTC e não reconstrói o estado de um corte histórico. Valores são strings decimais de duas casas na moeda consultada. Os campos `directIncome`, `directExpense` e `directTax` mostram os valores registrados diretamente no nível consultado. No previsto, `expense` traz os gastos WORK e `tax` é a parcela tributária deduplicada separadamente. No caixa, `tax` é nulo e pagamentos de impostos aparecem na despesa quando baixados.

`RECORDED` cobre os fatos registrados na base aplicada. `PARTIAL` entrega `attributedResult` e explica fatos no nível superior ou renegociação de gasto sem conciliação. Resultado integral e margem ficam nulos nesses casos. Com base registrada coberta, a margem do previsto é o resultado dividido pelo faturamento aprovado positivo. Uma visão com `NO_PERMISSION` tem valores nulos. Sem acesso a nenhuma das duas bases, a resposta é `403`. Alocações inexistentes ficam no nível comprovado e gastos COMPANY ficam fora da obra. `effectiveLaborCostCoverage=NO_RECONCILED_SOURCE` sinaliza a falta de uma fonte conciliável de folha. Horas do Diário ficam disponíveis como execução operacional e a API não estima custo nem duplica obrigações de mão de obra registradas.

### 2.6 Contratos — JWT de empresa

Complemento F03-19 (05/11/2026): contrato executável gerado e validado, publicado em `localhost:8081` às09:25:11 de05/11/2026 (São Paulo), com [guia de encerramento, situação e descontos](FRONTEND-CONTRACT-LIFECYCLE.md).

Feedback complementar F03-19 publicado/verificado em8081 em05/11/2026 às12:54:46SãoPaulo: a elegibilidade preserva motivo financeiro autorizado do serviço já COMPLETED. No resumo do contrato, `CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED` diferencia origem/crédito que exigem conciliação de `CONTRACT_HAS_UNSETTLED_SERVICES`; o primeiro tem prioridade entre serviços. Sem novo campo/rota ou liberação de guarda, e sem indicar dinheiro ausente pela mera falta de atribuição histórica. ExportF42E4998/151paths/263schemas e pacoteD704704F, com21testes distintos/23execuções aprovados; contrato/ativação no guia.


| Método | Rota relativa a `/api/v1/company/contracts` | Contrato | Respostas |
| --- | --- | --- | --- |
| GET | `/{contractId}/closure-eligibility` | `ContractClosureEligibilityResponse`; page/size e serviceId opcional; resumo integral antes do filtro; CONTRACTS.READ + SERVICES.READ |200;400/401/403/404 Problem Details |
| PUT | `/{contractId}/status` | `UpdateContractStatusRequest` → `ContractChangeResponse`; somente status, completedOn opcional e justificativa |200/202;400/403/404/409 Problem Details |
| PUT | `/{contractId}/services/{serviceId}/status` | `UpdateContractServiceStatusRequest` → `ContractServiceChangeResponse`; somente status e justificativa |200/202;400/403/404/409 Problem Details |

CANCELLED exige alçada CANCEL; demais alterações usam UPDATE.202 não modifica dado oficial e revalida na aprovação; requests não reenviam preços/descontos/perfil. `eligible` e `authorizationMode` podem ser null; decisões financeiras exigem BILLING.READ + RECEIVABLES.READ + INVOICES.READ. Novo desconto é bloqueado por saldo oficial de cobrança aberto mesmo sem NF; líquido inferior ao caixa confirmado líquido de estornos também é bloqueado. Descontos vigentes e limites mais restritivos são preservados. Motivos controlados, reabertura explícita e disponibilidade local estão no guia.


Base: `/api/v1/company/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<ContractResponse>` | `200` |
| POST | `/` | `CreateContractRequest` | `ContractChangeResponse` | `201` direto, `202` aprovação ou `409` número em uso na obra |
| GET | `/{contractId}` | path UUID | `ContractResponse` | `200` |
| PUT | `/{contractId}` | `UpdateContractRequest` | `ContractChangeResponse` | `200` direto, `202` aprovação ou `409` conflito |
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

Ordenação permitida: `name`, `reference`, `status`, `startedOn`, `expectedCompletionOn`, `completedOn`, `createdAt`, `updatedAt`, `openFirst`.

Envie `sort=openFirst,asc` para colocar `OPEN` e `ACTIVE` no primeiro grupo antes de paginar. As demais situações continuam na listagem, conforme os filtros. Com `openFirst`, outros campos de ordenação valem dentro de cada grupo, com desempate por `createdAt,desc` e `id,asc`. `openFirst,desc` inverte a prioridade dos grupos. Chamadas sem `openFirst` mantêm o comportamento anterior.

`CreateContractRequest` e `UpdateContractRequest` incluem `contractIdentifier`, uma identificação livre de negócio (por exemplo, `0001/2027` ou `Contrato padrão`). Esse identificador **não é único**, inclusive quando se repetir em contratos de clientes finais distintos. `UpdateContractRequest` também contém `reference`, `name`, `status`, datas e `justification`. Ele não recebe `workId`: a obra do contrato é preservada para não alterar silenciosamente o encadeamento comercial, de medição e de faturamento.

O campo `reference` representa o número do contrato no relatório por obra. É opcional, aceita texto de até 100 caracteres, preserva pontuação e caixa e remove espaços nas pontas de novas referências. Números iguais podem existir em obras diferentes. Na mesma obra, cadastro e alteração para número já em uso retornam `409 application/problem+json` com `code=BUSINESS_RULE_VIOLATION`, `reason=CONTRACT_REFERENCE_ALREADY_EXISTS` e `violations[].field=reference`. Uma edição que mantém o número legado continua permitida. O `202` não reserva número. A aprovação revalida a disponibilidade e devolve o mesmo `409` se houver conflito.

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
| GET | `/` | `page`, `size`, `sort`, filtros abaixo | `PageResponse<EmployeeListItemResponse>` mínimo | `200` |
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

Atualização de privacidade de 22/10/2026: a lista HTTP retorna apenas `id`, `fullName`, `preferredName`, `jobTitle`, `department`, `registrationNumber`, `employmentType`, `currentlyEmployed`, `active`, `userId` e `photoUrl`. Documento/PIS, telefone, nascimento, gênero/etnia e remuneração não integram `EmployeeListItemResponse`. **Salário continua disponível no perfil/detalhe `EmployeeResponse`**, bem como os demais campos de cadastro; não tratar a ausência na lista como cadastro vazio nem enviar `null` no PUT completo para apagar dados. Não buscar o detalhe de todos os funcionários para reconstruir uma tabela de dados pessoais. [Recorte, limites e pendências](FRONTEND-PRIVACY-REMEDIATION.md).

| Parâmetro | Tipo | Regra |
|---|---|---|
| `name` | string | contém em `fullName` ou `preferredName`, sem diferenciar maiúsculas/minúsculas |
| `jobTitle` | string | contém, sem diferenciar maiúsculas/minúsculas |
| `department` | string | contém, sem diferenciar maiúsculas/minúsculas |
| `employmentType` | `EmployeeEmploymentType` | tipo de vínculo |
| `currentlyEmployed` | boolean | empregado atualmente na empresa |
| `active` | boolean | cadastro ativo |

Por privacidade, a listagem não aceita filtros de documento, PIS ou remuneração. Não encaminhar esses valores para a URL, `history.state`, armazenamento do navegador, telemetria ou logs de frontend. A consulta do perfil autenticado continua sendo a fonte de dados de RH quando a tela tiver autorização para mostrá-los.

Ordenação permitida: `fullName`, `jobTitle`, `department`, `employmentType`, `currentlyEmployed`, `active`, `createdAt`, `updatedAt`.

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

Para a linha do tempo, use `actorDisplayName` como identificação do responsável. A API prefere o nome do funcionário ligado à conta na mesma empresa. Quando a conta é de um proprietário sem vínculo, a identificação é `Proprietário da empresa`; para outro membro sem nome vinculado, `Usuário da empresa`. O e-mail de contato continua em `actorEmail`. Para eventos `SYSTEM`, a identificação é `Sistema` e `actorUserId` e `actorEmail` são nulos. Em eventos de cadastro de cliente, `entityDisplayName` é o nome do cliente da empresa. Rótulos legados indisponíveis podem ser preenchidos na leitura com dados da própria empresa, preservando o evento original. Os dados de `beforeData` e `afterData` são internos à auditoria.

Em `Role` e `RolePermissions`, `entityDisplayName` identifica o papel. Em `User` e `UserRoles`, identifica a conta afetada pelo nome do funcionário vinculado na mesma empresa ou pelo rótulo da conta sem vínculo. O nome capturado no evento continua visível após renomeações.

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

Atualização F05-30 validada e publicada somente em localhost:8081 em 27/10/2026: resumo da lista e detalhe acrescentam `receivedAmountAvailable` e `receivedAmount` (decimal canônico string ou null). O dinheiro confirmado líquido de estornos exige `RECEIVABLES.READ` adicional; sem ela false/null, não zero fictício. Não somar títulos/versões no cliente nem contar retenções/créditos/NFs como caixa. [Contrato e evidências](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md).

Base: `/api/v1/company/billing-control/contracts`

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | filtros + paginação | `PageResponse<BillingControlSummaryResponse>` | `200` |
| GET | `/{contractId}` | path UUID | `ContractBillingControlResponse` | `200` |
| GET | `/{contractId}/measurement-origins` | `measurementId`, `contractServiceId`, paginação, `sort=createdAt,desc` | `PageResponse<BillableMeasurementOriginResponse>` | `200`, `400`, `401`, `403`, `404` |
| GET | `/{contractId}/report/pdf` | path UUID | PDF binário inline, filename UTF-8, no-store | `200`, `400`, `401`, `403`, `404`, `409` |

**Correção documental posterior àV91 publicada em8081 às14:49:55SãoPaulo em29/10/2026,JARCF376B05:** mantém a rotaHTTP; grade com nºcontrato, parcelasNF sequenciais, saldo porserviço, resumo contratado/faturado/saldo eNFtabular com saldo acumulado na base contratual atual por emissão/ID. Não usa snapshotcaixaV91 como saldoNF. Sem nova migration. [Guia atualizado](FRONTEND-CONTRACT-BILLING-PDF.md). [Ativação atual e provas](F07-04-DOCUMENTARY-LOCAL-ACTIVATION-2026-10-29.md). O parágrafo seguinte é registro da publicação anterior386004E6, agora substituída.

**Relatório corrigido publicado na API local8081 em29/10/2026 às14:30:04SãoPaulo**, JAR386004E6.../V91 após autorização humana confirmada e backup privado verificado. Descrição completa, valor/saldo comercial líquido por serviço segundoF05-21, NFs emcolunas, resumo de dinheiroCONFIRMED líquido deestornos, histórico imutável de novasNFs no registro (antigas semcaptura) e cobranças abertas inclusive semNF. Quatro permissõesbase BILLING.READ+EXPORT/INVOICES.READ+EXPORT; RECEIVABLES.READ+EXPORT adicionais habilitam caixa/histórico/títulos. Sem ambas, indisponibilidade explícita e PDFbase disponível. Contratado menos caixa não é dívida dos títulos. Snapshot consistente, janelas limitadas, cache temporário e auditoria preservados. OpenAPIvivo200 e401anônimo conferidos. [Guia](FRONTEND-CONTRACT-BILLING-PDF.md). [Ativação](F07-04-REVISION-LOCAL-ACTIVATION-2026-10-29.md).

Filtros: `contractId`, `status`, `query`. Ordenação: `name`, `reference`, `status`, `createdAt`, `openFirst`. Use `sort=openFirst,asc` para a mesma prioridade e o mesmo desempate da listagem de contratos.

Na coluna **Número do contrato**, o PDF individual usa `reference`. Quando esse campo está vazio, o PDF individual continua exibindo `contractIdentifier` como fallback.

A consulta `measurement-origins` tem filtros/ordenação próprios e exige `BILLING.READ` **e** `MEASUREMENTS.READ`. Retorna composições aceitas/aplicadas, não revertidas, sem redução não reconciliada e com capacidade positiva. Cada linha preserva `measurementContractCompositionId`, destino `contractServiceId` imutável, medição/versão/grupo e `availableToBillAmount` decimal string. Vários avanços do mesmo serviço permanecem linhas distintas. Não reserva saldo; o POST de parcela mantém suas validações transacionais. [Contrato completo e exemplo](FRONTEND-BILLABLE-MEASUREMENT-ORIGINS.md).

No detalhe `GET /{contractId}`, todos os valores monetários são strings decimais canônicas, sem símbolo de moeda ou formatação local (por exemplo, `"99.90"`). Não os converta para `number` no cliente. Os totais oficiais do contrato são `serviceSubtotal` (subtotal dos serviços antes do ajuste contratual), `contractDiscountAmount` (desconto/ajuste oficial do contrato) e `contractNetAmount` (líquido após o ajuste). Também retornam `activeAllocatedAmount`, `availableAmount`, os valores de `services` e `allocations`, incluindo `registeredInvoiceAmount` e `availableInvoiceAmount`.

A listagem paginada também usa strings em `contractNetAmount`, `activeAllocatedAmount` e `availableAmount`. Os serviços em `GET /api/v1/company/contracts/{contractId}/services` expõem a base `netAmount`, a composição aceita `measurementCompositionAmount` e a posição oficial `effectiveNetAmount`. Não reconstruir o valor do contrato somando medições no frontend. Evidências e ajustes desta revisão: [F05-14](F05-14-FINANCIAL-INVARIANTS-REVIEW.md).

### 2.12 Parcelas de faturamento — JWT de empresa

Correção F05-20 publicada/verificada em localhost:8081 (05/11/2026 às12:08:09SãoPaulo): GET de lista/detalhe acrescenta `receivableFinancialStatusAvailable`, `receivableFinancialStatus` e `receivableReceivedWithWithholding`, com a semântica do título original e READ financeiro adicional, preservando `status` cadastral e `netAmount` histórico. ExportADD231B9, 151paths/263schemas, com nulabilidade e enum explícitos; testes/ativação no [guia de cobranças](FRONTEND-BILLING-INSTALLMENT-READ.md).

Base: `/api/v1/company/billing-installments`

> F05-21 concluída: novo fluxo com valor positivo líquido, sem desconto manual. Consulte `GET /api/v1/company/billing-control/contracts/{contractId}/net-capacity` e envie `POST /api/v1/company/billing-installments/net`, com `operationId`, `capacityVersion` e alocações de `netAmount`. [Guia completo, OpenAPI e provas](FRONTEND-NET-BILLING.md). O backend distribui somente o desconto não comprometido, por base/composição exata; não calcular capacidades no navegador. Export executável validado (127 paths/205 schemas), ainda sem publicação na instância. As rotas legadas abaixo continuam compatíveis.

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `contractId`, `reference`, `status`, `dueFrom`, `dueTo`, paginação/ordenação controladas | `PageResponse<BillingInstallmentSummaryResponse>` | `200`, `400`, `401`, `403` |
| GET | `/{installmentId}` | path UUID | `BillingInstallmentDetailsResponse` | `200`, `400`, `401`, `403`, `404` |
| POST | `/` | `CreateApprovedBillingInstallmentRequest` | `BillingInstallmentApprovalResponse` | `201` ou `202` |
| POST | `/{installmentId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |

As leituras exigem `BILLING.READ`, incluem histórico/canceladas e retornam `netAmount` oficial em string decimal, sem exigir soma no cliente. No detalhe, alocações preservam serviço/nome contratual e `measurementContractCompositionId`. Referências `{ id, reference }` de recebíveis exigem adicionalmente `RECEIVABLES.READ`; sem ela, `receivableReferencesAvailable=false` e `receivables=[]`. Não há saldo/baixas de recebíveis nessa resposta. [Filtros, ordenação, DTOs e regras completas](FRONTEND-BILLING-INSTALLMENT-READ.md). São contratos implementados/exportados; este recorte não publica/reinicia a API local.

Regras importantes para a UI:

- `SERVICE` exige `contractServiceId` e valor positivo.
- Em `SERVICE`, `measurementContractCompositionId` é obrigatório para valores originados de composição aceita de medição; só a base negociada disponível pode omiti-lo. A composição deve pertencer ao mesmo tenant, `contractServiceId` e contrato, sem ultrapassar sua capacidade aceita vigente.
- `CONTRACT_DISCOUNT` não aceita serviço e exige valor negativo.
- A parcela deve ter valor líquido positivo.
- Alocações não podem exceder contrato, serviço ou desconto contratual.
- Uma parcela aprovada cria um `Receivable` individual; `financialGroupId` agrupa títulos relacionados.

`allocations[].allocatedAmount` e `receivable.originalAmount` são strings decimais canônicas com duas casas, inclusive ajustes negativos legados como `"-20.00"`. Serviço cancelado não admite novo faturamento (`409`). Edição/desconto de serviço não pode reduzi-lo abaixo da capacidade comprometida, mesmo havendo saldo em outro serviço. A base negociada cobre suas alocações sem origem; uma composição aceita não encobre esse excesso. Desconto contratual deve manter lastro dos ajustes negativos ativos e dos descontos congelados em alocações líquidas; estorne/cancele primeiro pelo fluxo oficial. No novo fluxo, `allocatedAmount` é positivo líquido e `netBillingGrossAmount`/`netBillingDiscountAmount` preservam a base/desconto; no legado esses metadados são nulos. O resumo da medição já retorna capacidade líquida e falta receber com esses ajustes; não recalcular no frontend.

### 2.13 Notas fiscais — JWT de empresa

Complemento de cobrança por serviço F05-27: `GET /api/v1/company/billing-control/contracts/{contractId}/net-capacity/services` → `ServiceNetBillingCapacityResponse` com capacidade completa/versionada, cobertura e bloqueios separados, por BILLING.READ + MEASUREMENTS.READ; `POST /api/v1/company/billing-installments/net/by-service` recebe `CreateServiceNetBillingRequest` (`services[].mode=ALL_AVAILABLE|PARTIAL`, valor parcial string) e responde `BillingInstallmentApprovalResponse` (`201` direto/`200` replay/`202` proposta sem reserva). Resolução oficial base → aceite no backend, com origens preservadas até NF/título/baixa/estorno; não enviar composição/rateio manual. [Contrato completo](FRONTEND-SERVICE-BILLING-AND-TAX.md). As rotas abaixo continuam compatíveis.

Base: `/api/v1/company/invoices`

F05-35 validada/publicada somente em 8081 em 27/10/2026: `GET /contracts` → `PageResponseInvoiceContractResponse`, filtros `search/page/size/sort`; `GET /contracts/{contractId}` → contexto `InvoiceContractResponse`, ambos somente INVOICES.READ e com identificação mínima dos contratos que possuem NFs oficiais. Depois usar o `GET /` abaixo com `contractId` exato. [Contrato, campos, hashes e provas](FRONTEND-INVOICE-CONTRACT-INDEX.md).

Complemento F05-34: os GETs de lista/detalhe acrescentam `receiptPosition` no cabeçalho, separado do status documental. Com F05-32/V90 publicada localmente em29/10, informa posição UNPAID/PARTIALLY_RECEIVED/RECEIVED/SETTLED_BY_ADJUSTMENTS somente com vínculo NF/item comprovável; legado sem prova mantém indisponibilidade/motivo e valores null; `INVOICES.READ` não concede acesso financeiro. [Guia aditivo, estado de validação/publicação e permissões](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md).

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `contractId`, `number`, `series`, `contractSearch`, `installmentReference`, `status`, `issuedFrom`, `issuedTo`, `page`, `size`, `sort` | `PageResponseInvoiceSummaryResponse` | `200` |
| GET | `/{invoiceId}` | — | `InvoiceDetailsResponse` (`invoice`, `items`) | `200` |
| GET | `/{invoiceId}/attachments` | `page`, `size`, `sort` | `PageResponseInvoiceAttachmentResponse` | `200` |
| GET | `/{invoiceId}/attachments/{attachmentId}/download` | — | PDF/XML binário autenticado | `200` |
| POST | `/` | `CreateRegisteredInvoiceRequest` | `InvoiceRegistrationResponse` | `201` ou `202` |
| POST | `/{invoiceId}/cancellation` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `200` ou `202` |
| POST | `/{invoiceId}/attachments` | multipart: `attachmentType=PDF`, `file` PDF | `InvoiceAttachmentResponse` | `200`, `409 INVOICE_PDF_ALREADY_ATTACHED`, `415 INVOICE_ATTACHMENT_PDF_REQUIRED` |

Correção F05-36 publicada/verificada somente em 8081 em 28/10/2026 sob nova autorização: upload força persistência antes da projeção para devolver `createdAt` UTC e os oito metadados completos; não aceitar null nem reenviar automaticamente após resposta inconclusiva. Cancelamento mantém a rota e `200/202`, mas valida também antes de propor e na aprovação: `409 BUSINESS_RULE_VIOLATION` com motivos `INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS`, `INVOICE_CANCELLATION_HAS_TAX_PAYMENTS` ou `INVOICE_CANCELLATION_REQUIRES_REVIEW`, além de conflito concorrente padronizado. Mantém dívida e histórico, sem estorno automático. Hash ativo FCEEA62E.../OpenAPI vivo conferidos com export 976E9070...; F05-32 e novos recebimentos continuam bloqueados. [Contrato, diagnóstico, testes e publicação](FRONTEND-INVOICE-UPLOAD-AND-CANCELLATION-SAFETY.md).

Anexos aceitos: `PDF`, `XML`, até 10 MiB. A API valida MIME/extensão e conteúdo antes de gravar: PDF legível, com páginas e não cifrado; XML bem formado sem DOCTYPE, entidades externas ou XInclude, com limites estruturais. Arquivo inválido retorna `400 application/problem+json` com `correlationId`, sem anexo/auditoria de sucesso. Upload exige alçada direta de criação em INVOICES (`403` sem ela; `404` para NF ausente/fora do tenant), sem mudança do HTTP `200` de sucesso. Validação estrutural não é antimalware nem autorização/validação fiscal. A NF pertence a um único contrato e cada item consome somente a capacidade da `BillingAllocation` informada. Registrar NF não registra recebimento.

Os GETs de lista/detalhe da NF exigem `INVOICES.READ`, retornam somente o tenant da sessão e incluem histórico cancelado. Filtros são literais e não exigem pesquisa por UUID; a ordem padrão é `issuedOn desc, id asc`, com campos permitidos `number`, `series`, `issuedOn`, `status`, `createdAt`. O detalhe mantém os IDs de alocação/parcela/composição exatos por item e não expõe chave fiscal, anexos, impostos ou dados de baixa. Erros `400/401/403` e `404` no detalhe usam Problem Details. Parâmetros, nulabilidade, decimais e limites efetivos no [guia F05-22](FRONTEND-INVOICE-READ.md); publicação local não integra este recorte.

A lista contextual de anexos exige `INVOICES.READ`, tamanho de página `1..100` (padrão `20`), ordem padrão `createdAt desc,id asc` e sort permitido `createdAt`, `attachmentType`, `originalFilename`, `contentSize`. Retorna os metadados do DTO atual, nunca storage ou conteúdo fiscal. Download exige **READ+EXPORT**, preserva arquivo existente de até 10 MiB, confere hash/tamanho e devolve `application/pdf`/`application/xml`, `Content-Disposition: attachment` seguro e `Cache-Control: no-store`; registra somente auditoria EXPORT segura, sem efeito financeiro. NFs canceladas continuam legíveis. Cadeia inválida/fora do tenant retorna `404`; conteúdo indisponível/corrompido retorna `503`, também Problem Details. [Contrato completo e provas F05-23](FRONTEND-INVOICE-ATTACHMENTS.md). Upload mantém suas regras; consulta de tributos é o recorte F05-24 abaixo.

`invoice.totalAmount` e `invoice.items[].allocatedAmount` são strings decimais canônicas. A mesma alocação pode ser documentada parcialmente em várias NFs até seu limite; a NF não consome novamente o saldo contratual. A situação ativa e o saldo da alocação são verificados sob lock transacional, também na aprovação.

### 2.14 Impostos da NF — JWT de empresa

Complemento F05-28: `POST /api/v1/company/invoices/{invoiceId}/tax-obligations/preview` → `InvoiceTaxCalculationResponse` (`200`, sem efeitos); `POST .../calculated` → `CalculatedTaxObligationCreationResponse` (`201` direto/`200` replay/`202` somente proposta); `GET .../{taxObligationId}/calculation` → `InvoiceTaxCalculationResponse` (`200`, histórico por INVOICES.READ). Prévia/criação exigem alçada INVOICES.CREATE/request, inputs explícitos por valor ou base/alíquota e confirmação oficial do valor, sem motor fiscal. [Requests, precisão, idempotência, status e provas](FRONTEND-SERVICE-BILLING-AND-TAX.md). Todos documentam `400/401/403/404/409` Problem Details; legado abaixo não muda.

Base: `/api/v1/company/invoices/{invoiceId}/tax-obligations`

Leitura: `GET /` retorna `PageResponseInvoiceTaxObligationResponse` com `INVOICES.READ`, somente no tenant e na cadeia NF/contrato/tributo/recebível validados. Aceita `page`/`size` (0/20, tamanho 1..100), `status` (`REGISTERED`/`CANCELLED`) e `treatment` opcionais; inclui todo histórico por padrão, inclusive NF cancelada. Sort controlado `createdAt`, `name`, `treatment`, `status`, com direção, padrão `createdAt desc,id asc`. Resposta `200 application/json`/`no-store`; NF existente sem tributos retorna página vazia, fora do tenant/inexistente retorna `404`; `400/401/403/404` usam Problem Details/correlationId.

O item `InvoiceTaxObligationResponse` contém `id`, `invoiceId`, `contractId`, `name`, `description` opcional, `dueOn` opcional, `amount` string decimal, `treatment`, `status`, `currency`, `createdAt`, `updatedAt`. Não devolve IDs/detalhes de recebível/Payable, não recalcula nem reaplica retenções, não cria obrigação/saldo/baixa e não muda auditoria financeira; `202` continua só proposta. POST existente permanece com seu próprio DTO de criação. [Contrato completo, nulabilidade e provas F05-24](FRONTEND-INVOICE-TAXES.md).

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | `status`, `treatment`, `page`, `size`, `sort` | `PageResponseInvoiceTaxObligationResponse` | `200` |
| POST | `/` | `CreateTaxObligationRequest` | `TaxObligationCreationResponse` | `201` ou `202` |

Tratamentos:

- `PAYABLE_BY_COMPANY`: cria `Payable` de escopo obra e devolve `payableId`.
- `WITHHELD_BY_CUSTOMER`: exige `receivableId` compatível e reduz o valor líquido a receber.
- `INFORMATIONAL`: apenas registra informação; não cria conta a pagar nem baixa.

`taxObligation.amount` é string decimal canônica, não número JSON.

### 2.15 Contas a receber e recebimentos — JWT de empresa

Base: `/api/v1/company/receivables`

F05-32 implementada/validada e **publicada somente em8081 em29/10/2026, com V90 e nova autorização específica**: `GET /{receivableId}/invoice-settlement-options?page=&size=` retorna `InvoiceSettlementOptionsResponse` com página interna de origens exatas e limites oficiais; `POST /{receivableId}/invoice-settlements` aceita `CreateInvoiceSettlementRequest` com operationId e valores manuais por NF/InvoiceItem/alocação/composição. `201` novo, `200` replay, `202` somente `PendingInvoiceSettlementResponse`. Exige INVOICES.READ e leitura/alçada de RECEIVABLES correspondente. Flag e POST genéricos antigos continuam bloqueados. Estorno/histórico usam rotas já existentes; posição de NF evolui somente para vínculos comprováveis. Export executável27037B87... disponível para tipos,147paths/255schemas; 89 testes/11 suítes e Checkstyle aprovados. [DTOs, paginação, limites compartilhados e idempotência](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md). [Ativação local verificada](F05-32-LOCAL-ACTIVATION-2026-10-29.md): JAR0E25A12A..., V90 única aplicada com checksums, OpenAPI200 idêntico nos componentes/caminhos compartilhados ao export,401 anônimos, seeds/Flyway automático off e ledger vazio. Frontend pode habilitar apenas a configuração local autorizada; nenhum recebimento real executado.

Histórico F05-37, anterior ao complemento de retenção proporcional descrito ao final deste guia: aditivo de leitura validado e publicado somente em8081 em29/10, após nova autorização específica e sem migrations: o mesmo GET de opções acrescenta totalSettlementProposal com total/linhas exatas somente quando a distribuição da cobrança pelas origens documentadas for única. Independente da página; sem reserva, proporção/FIFO ou cálculo oficial no frontend; parcial permanece manual. Indisponível retorna motivo/null/linhas vazias. Export atual7BF8E221.../147paths/257schemas; API8081 ativa no JAR B971C83C..., OpenAPI vivo200/componentes e caminhos compartilhados idênticos ao export7BF8E221..., schema90 e seeds/Flywayfalse preservados. [Publicação verificada](F05-37-LOCAL-ACTIVATION-2026-10-29.md). [Contrato, exemplos, motivos e22 testes aprovados](FRONTEND-TOTAL-INVOICE-SETTLEMENT-PROPOSAL.md).

Complemento F05-33: `financialView` filtra a posição oficial antes de count/paginação; `financialPosition` separa dinheiro, crédito, retenção e saldo. UI operacional envia `OUTSTANDING`, enquanto omissão mantém `ALL` histórico. [Contrato aditivo e estado de validação/publicação](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md). As descrições históricas abaixo sobre ausência de saldo na lista são substituídas por esse complemento, sem mudar o status persistido.

| Método | Caminho | Entrada | Resposta | Status |
|---|---|---|---|---|
| GET | `/` | filtros comerciais, contexto opcional, status registrado, financialView=OUTSTANDING/RECEIVED/ALL, moeda, vencimento, page/size/sort | `PageResponseReceivableSummaryResponse` com financialPosition oficial | `200`; `400/401/403` Problem Details |
| GET | `/{receivableId}` | path UUID | `ReceivableDetailsResponse` | `200`; `400/401/403/404` Problem Details |
| GET | `/{receivableId}/settlements` | path UUID, entryType/status/paymentMethod, receivedFrom/receivedTo, page/size/sort | `PageResponseReceivableSettlementHistoryResponse` | `200`; `400/401/403/404` Problem Details |
| POST | `/{receivableId}/settlements` | `CreateReceivableSettlementRequest` | `ReceivableSettlementApprovalResponse` | `201` ou `202` |
| POST | `/{receivableId}/settlements/{settlementId}/reversal` | `CancelFinancialRecordRequest` | `FinancialCancellationResponse` | `201` ou `202` |
| GET | `/{receivableId}/balance` | path UUID | `ReceivableBalanceResponse` | `200` |
| POST | `/{receivableId}/credits` | `CreateReceivableCreditRequest` | `ReceivableCreditResponse` | `201` ou `202`; crédito justificado sem baixa fictícia |
| POST | `/{receivableId}/credits/{creditId}/reversal` | `CancelFinancialRecordRequest` | `ReceivableCreditResponse` | `201` ou `202`; estorno imutável |
| GET | `/{receivableId}/renegotiations` | path UUID | `ReceivableRenegotiationResponse` | `200`; títulos descendentes e históricos |
| POST | `/{receivableId}/renegotiations` | `RenegotiateReceivableRequest` | `ReceivableRenegotiationResponse` | `200` ou `202`; somente saldo ainda devido |
| POST | `/{receivableId}/renegotiations/reversal` | `CancelFinancialRecordRequest` | `ReceivableRenegotiationResponse` | `200` ou `202`; reabre original após reversão de efeitos descendentes |

F05-25 / FRONT-F08-11: lista/detalhe exigem somente `RECEIVABLES.READ`, com tenant exclusivo da sessão; não exigem `BILLING.READ`. Projeção mínima traz referências legíveis da parcela/contrato/obra/cliente, `originalAmount` histórico em string decimal e `currency`, preservando cancelados/renegociados e vínculo ao título anterior. `status` é **situação registrada**, não atraso calculado; consultar `/{receivableId}/balance` para saldo/situação financeira atuais, sem cálculo no frontend. Filtros literais `search`, `reference`, `contractSearch`, `workSearch`, `customerName`, `billingReference`, contextos UUID opcionais, `status`, `currency`, `dueFrom`, `dueTo`; página padrão 0/20, tamanho 1..100; sort controlado, padrão `dueOn asc,id asc`. Sem detalhes tributários/de baixa ou acesso implícito aos módulos relacionados. [Contrato completo, nulabilidade, validação e export](FRONTEND-RECEIVABLE-READ.md); sem publicação/reinício da instância. Histórico de baixas FRONT-F08-15 é o recorte próprio F05-26 abaixo.

F05-26 / FRONT-F08-15: histórico contextual exige somente `RECEIVABLES.READ`, preservando títulos cancelados/renegociados e lançamentos cancelados. `ReceivableSettlementHistoryResponse` traz ID opaco de seleção, título/referência, contrato, rótulo controlado, data, valor assinado decimal, moeda, método, situação persistida, instantes UTC e `reversalOfSettlementId`/`reversedBySettlementId` exatos. Não inclui observação livre/dados bancários/fiscais ou atribuições e não altera saldo/auditoria. Recebimento original permanece no histórico após estorno; não inferir correspondência por valor/data/serviço e não somar a página para obter saldo. Filtros são enums/datas inclusivas; sort permitido `receivedOn`, `createdAt`, `entryType`, `paymentMethod`, `status`, padrão `createdAt desc,id asc`; página 0/20, size 1..100. [Contrato, nulabilidade, export e provas](FRONTEND-RECEIVABLE-SETTLEMENT-HISTORY.md); comandos de estorno e publicação separados.

Contenção F05-29/F05-31 validada e ativa em localhost:8081 desde 27/10/2026: novas baixas legadas estão temporariamente bloqueadas, inclusive com NF, em chamada direta/solicitação/aprovação, com `409 BUSINESS_RULE_VIOLATION` e `reason=SETTLEMENT_INVOICE_LINK_REQUIRED`. Não criam proposta/reserva/lançamento. O saldo existente acrescenta `maxReceivedOn` (dia UTC oficial), `settlementCreationAvailable=false` e `settlementCreationBlockReason` (string/null sem enum restritivo). Datas futuras são `400` com violação `receivedOn`, não agendamento. Consultas e estornos autorizados permanecem disponíveis; histórico não é reescrito. Publicação sem migrations, schema real V89 preservado. [Contrato e limitações](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md).

O modelo suporta recebimento parcial, mas novas baixas estão indisponíveis durante a contenção. Estorno cria lançamento reverso; não apaga a baixa original.

`settlement.amount` e os campos monetários de `ReceivableBalanceResponse` (`originalAmount`, `withheldAmount`, `creditedAmount`, `netReceivableAmount`, `confirmedSettlementAmount`, `outstandingAmount`) são strings decimais canônicas com duas casas. Criação de parcela, NF e imposto mantém `201` direto ou `202` pendente. Os antigos schemas `201`/`202` da baixa permanecem por compatibilidade estrutural, mas não significam que o POST esteja disponível durante a contenção. Cancelamentos de parcela/NF usam `200`/`202`, e estorno de baixa usa `201`/`202`. `400`, `403`, `404` e `409` usam Problem Details. O `202` não tem efeito financeiro oficial.

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
- `AccessUserResponse`: `id`, `email`, `displayName` (opcional), `status`, `roleIds`, `companyOwner`, `createdAt`, `updatedAt`. O nome vem apenas do nome completo do funcionário associado pelo mesmo `userId` na empresa autenticada. Quando faltar essa associação, o campo é omitido e o e-mail continua disponível para identificar a conta.
- `RoleResponse`: `id`, `name`, `description`, `active`, `systemProtected`, `permissionIds`, `createdAt`, `updatedAt`.
- `PermissionResponse`: `id`, `module`, `action`, `code`, `active`.
- `ChangeAuthorizationResponse`: `module`, `operation`, `mode`.

### Hierarquia operacional

- `FinalCustomerResponse`: `id`, `customerType`, `name`, `document`, `active`.
- `WorkResponse`: `id`, `finalCustomerId`, `name`, `reference`, `executionLocationType`, `executionAddress`, `status`, datas.
- `ContractResponse`: `id`, `workId`, `reference`, `name`, `status`, datas.
- `EmployeeResponse` operacional (detalhe/criação/edição): perfil, `registrationNumber` gerada como `ABC-001` quando omitida (três primeiras letras da empresa assinante mais sequência), `documentType`, `documentNumber`, `currentlyEmployed`, `gender`, `ethnicity`, `compensationFrequency` (`MONTHLY`, `DAILY`, `HOURLY`) e `compensationAmount`. O documento é completo/normalizado no perfil autenticado da própria empresa; não existe `documentNumberMasked`. A listagem usa `EmployeeListItemResponse` separado, sem documento nem salário, e deve identificar pela matrícula/nome. Não declarar tipos manualmente; gerar do OpenAPI atualizado.

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
- `ReceivableSummaryResponse`: identificação do título, referências mínimas de parcela/contrato/obra/cliente, grupo/linhagem de renegociação, vencimento próprio, `originalAmount` histórico decimal, `currency`, `status` registrado e timestamps. [Campos e nulabilidade](FRONTEND-RECEIVABLE-READ.md).
- `ReceivableDetailsResponse`: `receivable` (o resumo) e `description` opcional. Não inclui baixas nem saldo.
- `ReceivableSettlementHistoryResponse`: lançamento histórico assinado, método/data/situação, rótulo e instantes de registro, moeda e vínculos exatos original/estorno; sem observação livre. Lista paginada, não saldo.
- `ReceivableBalanceResponse`: `originalAmount`, `withheldAmount`, `creditedAmount`, `netReceivableAmount`, `confirmedSettlementAmount`, `outstandingAmount`, `status` atual e `currency`; autoridade financeira existente.
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
7. consultar/editar todos os dados cadastrais da própria empresa;
8. edição/cancelamento geral de contas a pagar e projeções de lucro/caixa. [Guia de títulos](FRONTEND-PAYABLES.md) e [guia de baixas e estornos](FRONTEND-PAYABLE-SETTLEMENTS.md).
9. relatórios gerais, exportação DOCX e envio de relatório por e-mail. As exportações PDF/XLSX de uma versão de medição já existem e não integram esta lacuna;
10. consulta e PDF/XLSX/prévia acumulados da medição, com aceitos anteriores e avanço atual no mesmo corpo e totais separados;
11. identidade de serviço contínuo entre avanços e escolha persistida de destino contratual antes do aceite;
12. coordenação de composição incremental autorizada no aceite/vínculo posterior, com pendências consultáveis e idempotência também de solicitações;
13. extensão do resumo com saldo total `remainingToReceive`, disponibilidade e `asOf`, tratamento completo de correções/ajustes/estornos e origem obrigatória para faturamento de composições. O resumo atual com cinco campos não conclui essas lacunas.

Essas lacunas devem virar backlog de API antes de fechar navegação, telas de listagem e carregamento inicial do frontend. O frontend não deve acessar banco, storage ou gateway diretamente para contorná-las.

A antiga lacuna de listagem/detalhe básico de recebíveis está atendida no código/export pela F05-25 (seção 2.15); histórico de baixas continua separado. Isso não confirma publicação das rotas na instância local.

A antiga lacuna de histórico contextual de recebimentos/estornos está atendida no código/export pela F05-26: 32 testes/7 suítes, Checkstyle e OpenAPI 3.1 validados (137 paths/234 schemas; hash no guia próprio). Sua publicação na instância e integração dos comandos do frontend são etapas separadas.

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


### Correção FRONT-F06-14 — desconto contratual ativo

Publicado em8081 em29/10/2026 às18:44:52SãoPaulo,JARAF20D2D2: GET `/api/v1/company/contracts/{contractId}` inclui `hasActiveContractDiscount`, booleanoficial no detalhe e null(nãoavaliado) emlistagem/mutações. MesmaCONTRACTS.READ/tenant. Não inferir desconto pelo agregadofinanceiro. [Contrato e integração](FRONTEND-CONTRACT-DISCOUNT-STATE.md).


### Situação financeira no detalhe do faturamento —29/10/2026

Publicado em8081 em29/10/2026 às19:22:27SãoPaulo,JAR4A2D4980: allocations[].receivableFinancialStatusAvailable e receivableFinancialStatus(nullable) noGETcontroleporcontrato. ExigeRECEIVABLES.READ adicional;reutiliza posiçãooficial dotítulooriginal,sem sobrescreverinstallmentStatus,sem somarsucessores/inferir porNF. [Contrato](FRONTEND-BILLING-FINANCIAL-STATUS.md).


### Rótulo recebido com imposto retido

Publicado em8081 em29/10/2026 às20:01:42SãoPaulo,JARE09835F5: allocations[].receivableReceivedWithWithholding(requiredboolean|null) noGETbilling-controlporcontrato,mesmosREAD/tenant/indisponibilidade.Indicaquitaçãooficialcomcash+retençãosemcrédito,sem mudarposiçãofinanceira ou dinheiro. [Guia](FRONTEND-BILLING-WITHHOLDING-LABEL.md).

#### Retenção proporcional por item de NF — complemento publicado localmente

GET `receivables/{receivableId}/invoice-settlement-options` acrescenta `withheldItemAmount`, `netInvoicedItemAmount` e `netAvailableItemAmount` em cada opção: required nullable string decimal, origem exata e escopo de título comprovado. A proposta TOTAL usa líquidos oficiais; parciais manuais e202 sem efeitos. Não somar tetos, ratear no frontend ou converter imposto em dinheiro recebido. Autorizado pelo usuário em29/10; sem migration, reescrita histórica ou operação real. [Contrato e motivos de conciliação](FRONTEND-INVOICE-ITEM-WITHHOLDING.md). 50testes/4suítes e Checkstyle aprovados; export49F89DB0/148paths/257schemas. Conferido e publicado somente em8081 às23:42:22SãoPaulo, JARE97C650C/schema91 sem alteração. [Provas](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md).

#### PUT de serviço preserva desconto — correção publicada localmente,30/10/2026

`PUT /api/v1/company/contracts/{contractId}/services/{serviceId}` mantém200/202 e não apaga desconto por omissão/null dos dois campos. Aprovação preserva o desconto corrente no banco. Eco explícito igual é aceito; par diferente recebe409ProblemDetails com reason `SERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION`, pois troca/reversão exige rota específica DISCOUNTS. Não enviar snapshot de desconto pelo formulário operacional nem compensar perdas no frontend. Criação inalterada; sem migration ou reparo real. [Guia](FRONTEND-SERVICE-UPDATE-DISCOUNT-PRESERVATION.md).

Correção de preservação publicada/verificada somente em8081 às00:47:58SãoPaulo, JAR5E4601A1/export4BFEA6EB,16testes/4suítes eCheckstyle aprovados, sem migration ou reparo real. [Provas e retorno](F03-16-SERVICE-DISCOUNT-PRESERVATION-LOCAL-ACTIVATION-2026-10-30.md).


## Contato de fornecedores e instruções para pagamento — F06-20/21

Contrato detalhado e dependências: [FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md](FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md). Código e contrato validados, export atualizado (154 paths/268 schemas), ainda não publicados na instância local; [provas e plano de ativação](F06-20-21-VALIDATION-2026-11-06.md). Contatos opcionais (`contactName`, `contactPhone`, `contactEmail`) nas rotas existentes de fornecedor; alçada/versão/DIRECT/202 preservados.

| Método | Rota | Uso |
|---|---|---|
| GET | `/api/v1/company/payables/{payableId}/payment-instructions` | Metadados; PAYABLES.READ, nunca dados para copiar/pagar. |
| PUT | mesma base | Substituição integral com versão própria e `details`; READ+VIEW_PAYMENT_DETAILS e UPDATE; 200/202. |
| POST | base + `/reveal` | Dados completos por ação explícita, auditada; READ+VIEW_PAYMENT_DETAILS; no-store. |
| POST | base + `/boleto` | PDF multipart privado após título/BOLETO oficiais; versão própria; 200/202. |
| GET | base + `/boleto` | PDF autenticado, sem URL pública; READ+VIEW_PAYMENT_DETAILS; no-store. |

Reveal/download aceitam `changeRequestId` opcional para proposta pendente do mesmo título/tenant, restrita ao solicitante ou aprovador com permissões sensíveis. V92 e chave independente são pré-requisitos de publicação, ainda sem aplicação no banco persistente. Instrução/documento não registra baixa, não quita título, não emite boleto/Pix nem executa pagamento. Não reutilizar RH nem simular atomicidade com criação do título/lote.


## FRONT-F08-34 — prévia XML e cobertura documental por serviço

[Contrato efetivo F05-39/40/41](FRONTEND-INVOICE-XML-AND-COVERAGE.md); [provas/export/pacote](F05-39-41-VALIDATION-2026-11-06.md). Código testado e OpenAPI atualizado, **sem publicação na API local**.

| Método | Rota | Contrato |
|---|---|---|
| POST | `/api/v1/company/invoices/xml/preview` | Multipart objeto com file; contractId query e billingInstallmentId query opcional. INVOICES.READ+alçada de criação; contexto de cobrança exige BILLING.READ.200 stateless/no-store, sem criar lançamento, anexo, tributo ou202. |
| GET | `/api/v1/company/billing-installments/{billingInstallmentId}/invoice-coverage` | INVOICES.READ+BILLING.READ; grupos completos com origens, estados e totais oficiais documentados/faltantes em decimal textual, sem reserva. |
| POST existente | `/api/v1/company/invoices` | Guarda comum direta/aprovação para serviços completos da cobrança, origens exatas e conflitos409. Campo opcional documentTotalAmount textual, obrigatório no cliente assistido após revisão para comparação oficial.201/202; nenhuma alteração oficial em202. |

O registro documental aceita serviços completos por cobrança. Recebimentos parciais e registros legados permanecem disponíveis. DPS, retenções, líquido, percentuais informativos e substituição preservam proveniência para conferência. F05-39 a F05-41 estão publicados em localhost:8081. A confirmação fiscal abaixo depende da publicação separada da V94.

| Método | Rota | Contrato da evidência fiscal F05-42 |
| --- | --- | --- |
| GET | `/api/v1/company/invoices/{invoiceId}/xml-fiscal-snapshot` | `INVOICES.READ`. Retorna a evidência registrada ou `404`. |
| PUT | `/api/v1/company/invoices/{invoiceId}/xml-fiscal-snapshot` | `INVOICES.READ` e alçada direta de criação. Enviar `attachmentId` de XML privado já anexado à NF oficial. Primeira confirmação `201`, reenvio da mesma origem `200`, divergência `409`. |

Anexação e tributos continuam operações separadas. [Detalhes da evidência e dos campos](FRONTEND-INVOICE-XML-FISCAL-SNAPSHOT.md).

| Método | Rota | Contrato do faturamento por obra F05-43 |
| --- | --- | --- |
| GET | `/api/v1/company/billing-control/works/{workId}` | `WORKS.READ`, `BILLING.READ` e `INVOICES.READ`. Totais oficiais de toda a obra por moeda e lista paginada de contratos com link para detalhe. |
| GET | `/api/v1/company/billing-control/works/{workId}/report/pdf` | Acrescenta `BILLING.EXPORT` e `INVOICES.EXPORT`. PDF privado inline com título, referência, tabela de contratos e totais por moeda. |

[Guia do controle por obra](FRONTEND-WORK-BILLING-CONTROL.md).
