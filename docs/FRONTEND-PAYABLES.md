# Contas a pagar — F06-02 / FRONT-F09-02

Estado: **implementado e validado em 22/10/2026**, ainda sem publicação/reinício da API local. Esta entrega não autoriza deploy, reinício, push ou dados reais.

## Rotas

| Método | Rota | Autorização / resposta |
|---|---|---|
| POST | `/api/v1/company/payables` | Alçada `PAYABLES.CREATE`; `201` aplicado, `200` replay oficial, `202` solicitação pendente. |
| GET | `/api/v1/company/payables` | `PAYABLES.READ`; `200 PageResponsePayableResponse`. |
| GET | `/api/v1/company/payables/{payableId}` | `PAYABLES.READ`; `200 PayableResponse`, incluindo histórico cancelado/tributário. |

Autenticação Bearer e tenant exclusivamente da sessão. Não há `companyId` no request. Permissões de fornecedor/categoria não concedem criação de título. A seleção de categorias usa o catálogo com `COSTS_EXPENSES.READ`, mas ler um título autorizado já fornece os rótulos mínimos de seus vínculos, sem documento ou detalhes adicionais.

## Criação oficial

Um POST cria **1 a 100 títulos individuais**, em uma transação e em um único grupo financeiro gerado pelo servidor. Para um único título, enviar uma lista de um elemento. Não existe divisão automática de valores, geração de vencimentos, juros, conversão ou rateio. O cliente informa cada valor/vencimento explícito; o backend valida e persiste valores exatos. Não enviar total calculado, saldo, status, versão, ID do título/grupo ou origem fiscal.

```json
{
  "operationId": "8826814b-2db2-4c66-b582-d6c585cb295d",
  "financialScope": "WORK",
  "workId": "cfe7fb0f-ec58-4e1b-a11b-7d641008cc71",
  "supplierId": "d40fe5a7-6568-4208-a236-bb8de1fa5ba3",
  "financialCategoryId": "33621b7f-6702-434c-824d-22890850283b",
  "currency": "BRL",
  "titles": [
    {
      "reference": "EPI — parcela 1/2",
      "description": "Equipamentos de proteção da obra",
      "issuedOn": "2026-10-22",
      "dueOn": "2026-11-01",
      "originalAmount": "150.00"
    },
    {
      "reference": "EPI — parcela 2/2",
      "dueOn": "2026-12-01",
      "originalAmount": "150.00"
    }
  ],
  "justification": "Compra para a obra"
}
```

`operationId` UUID é obrigatório. Guardar o mesmo ID para repetir exatamente a mesma operação após perda da resposta; dados diferentes com o mesmo ID de uma criação aplicada/pendente geram `409`. Repetições concorrentes não duplicam títulos nem solicitações. Uma proposta rejeitada pode ser refeita com um novo `operationId`. Cada novo POST legítimo usa novo ID, não o ID de um grupo existente.

`financialScope` é obrigatório:

- `WORK`: `workId` obrigatório; `contractId` opcional da **mesma obra e empresa**. EPI sem contrato é permitido.
- `COMPANY`: `workId` e `contractId` ausentes/nulos. Não aparece em consulta por obra nem pode compor resultado de obra.
- A classificação da categoria não escolhe, substitui ou infere escopo. Não há rateio administrativo entre obras.

`supplierId` e `financialCategoryId` são opcionais. Se informados, devem existir no tenant e estar ativos; outro tenant/inexistente → `404`; inativo → `409`. A aprovação verifica novamente a ativação e a cadeia, sem usar o estado antigo da tela. Categoria não é obrigatória para imposto legado.

`currency`: `BRL`, `USD` ou `EUR`. Com contrato, omissão herda sua moeda; valor diferente → `409`. Sem contrato, opcional com padrão `BRL`, persistido explicitamente. Todos os títulos do lote compartilham a moeda, que é imutável. Não há soma global entre moedas nem conversão. Gasto avulso não herda moeda de outros contratos da obra.

Cada título exige `reference` não vazia (até 100), `dueOn` e `originalAmount`. Descrição opcional até 1.000; `issuedOn` opcional, não inferida. Vencimento passado é permitido. `originalAmount` é **string decimal positiva de duas casas**, até 17 dígitos inteiros. Ex.: `"150.00"`. JSON numérico, vírgula, expoente, símbolo, zero, negativo ou casas extras → `400`, sem arredondamento. `justification` opcional até 1.000.

## Respostas e aprovação

`201` / replay `200`:

```json
{
  "financialGroupId": "uuid-do-grupo-oficial",
  "payables": [
    {
      "id": "uuid-do-titulo",
      "financialGroupId": "uuid-do-grupo-oficial",
      "financialScope": "WORK",
      "workId": "uuid-da-obra",
      "contractId": null,
      "supplierId": "uuid-do-fornecedor",
      "financialCategoryId": "uuid-da-categoria",
      "taxObligationId": null,
      "reference": "EPI — parcela 1/2",
      "description": "Equipamentos de proteção da obra",
      "issuedOn": "2026-10-22",
      "dueOn": "2026-11-01",
      "currency": "BRL",
      "originalAmount": "150.00",
      "status": "OPEN",
      "overdue": false,
      "version": 0,
      "createdAt": "2026-10-22T15:00:00Z",
      "updatedAt": "2026-10-22T15:00:00Z",
      "work": { "id": "uuid-da-obra", "displayName": "Construção da unidade", "reference": "OB-1" },
      "contract": null,
      "supplier": { "id": "uuid-do-fornecedor", "displayName": "Fornecedor de EPI", "reference": null },
      "financialCategory": { "id": "uuid-da-categoria", "displayName": "Equipamentos de proteção", "reference": "EPI" }
    }
  ],
  "changeRequestId": null,
  "mode": "DIRECT"
}
```

`202`, inclusive replay da mesma proposta pendente:

```json
{
  "financialGroupId": null,
  "payables": null,
  "changeRequestId": "uuid-da-solicitacao",
  "mode": "REQUEST_APPROVAL"
}
```

Não criar linha fictícia ou reduzir saldo no frontend em `202`. Não existe título/grupo oficial até aprovação; listagem e detalhe só mostram oficiais. Alçada direta efetiva é `CREATE_DIRECT` ou `REQUEST_CREATE` + `APPROVE` no módulo PAYABLES. Solicitação isolada aguarda aprovação; solicitante não aprova a própria proposta.

Aprovação usa a rota existente `POST /api/v1/company/change-requests/{id}/approve` e retorna `appliedEntity` com `type=PayableFinancialGroup`, ID do grupo e nome legível. Consultar `/payables?financialGroupId=...` para obter os títulos reais (exige READ). A aplicação é atômica; falha de referência/concorrência deixa a solicitação pendente e nenhum título parcial. Um novo POST igual após aprovação retorna `200` com o mesmo grupo/títulos, não duplica.

## Leitura, filtros e apresentação

Paginação padrão: `page=0`, `size=20`; `page >=0`, `size 1..100`, deslocamento limitado. Filtros opcionais exatos: `financialScope`, `workId`, `contractId`, `supplierId`, `financialCategoryId`, `financialGroupId`, `currency`, `status`. `reference` é trecho literal sem distinguir maiúsculas; `%`/`_` não são curingas. `dueFrom`/`dueTo` são datas inclusivas, com intervalo válido.

Use `view=OUTSTANDING` para a lista operacional (`OPEN`, `OVERDUE`, `PARTIALLY_PAID`), incluindo títulos `OPEN` com atraso derivado e legados com saldo indisponível. Use `view=HISTORY` para `PAID`, `CANCELLED`, `RENEGOTIATED` e `DRAFT`. O servidor cruza `view` com `status` e os demais filtros antes da paginação. Omita `view` para consultar todos os estados, inclusive ao abrir um grupo por `financialGroupId`. Baixas e estornos aplicados passam a refletir o estado oficial na próxima consulta. Propostas pendentes preservam a classificação vigente.

O filtro foi verificado no `/v3/api-docs` de `localhost:8081` em 13/11/2026. A publicação usou o schema local V96 e preservou o histórico Flyway.

Ordenações permitidas: `dueOn`, `issuedOn`, `reference`, `originalAmount`, `createdAt`, `updatedAt`, seguidas de `asc`/`desc`; repetir `sort` para vários campos. Padrão `dueOn,asc`, desempate por ID ascendente. Não são campos livres. `status` reflete o estado persistido; `overdue` é uma indicação oficial derivada do vencimento na data UTC para estados abertos. GET não persiste atraso nem altera auditoria/status.

Estados do domínio: `DRAFT`, `OPEN`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `RENEGOTIATED`, `CANCELLED`. Este POST cria somente `OPEN`. Use o saldo oficial e as operações dedicadas para registrar pagamentos já efetuados. [Baixas, saldo e estornos](FRONTEND-PAYABLE-SETTLEMENTS.md).

As projeções `work`, `contract`, `supplier`, `financialCategory` são rótulos mínimos no próprio recurso autorizado, com nomes sanitizados e sem documento, telefone, endereço ou banco. A referência do contrato prioriza `contractIdentifier`, senão `reference`; não exige chamadas de resolução de UUID no frontend. Rótulos de leitura refletem o cadastro atual; o evento de auditoria preserva sua identificação histórica segura.

## Segurança, persistência e limites desta entrega

- `400/401/403/404/409` usam `application/problem+json` com `correlationId`; erros de Bean Validation incluem `violations`. Nunca divulgar existência de IDs de outro tenant.
- Fornecedor/categoria/grupo/obra/contrato têm FKs de tenant. Contrato precisa pertencer à obra e ter a mesma moeda também no banco.
- Títulos usam `@Version`; criação usa chave de operação com lock transacional e unicidade por empresa. Não há PUT/DELETE/baixa operacional geral nesta entrega.
- Auditoria CREATE por título aplicado com ator/origem/grupo; não inclui documentos, dados bancários, valores ou descrição livre. `202` registra apenas a solicitação, não auditoria de título inexistente.
- Não há exclusão física: triggers preservam Payable e grupo; origem fiscal/escopo/moeda/vínculos permanecem imutáveis. Cancelamento tributário existente continua funcionando com versão.
- V87 é aditiva sobre V86. Cada título legado recebe grupo próprio, versão inicial e moeda do contrato (BRL somente quando sem contrato), preservando valor, datas, situação, IDs e origem. Não editar migrations anteriores.
- Não inclui pagamentos/estornos, câmbio, relatórios, lucro/fluxo de caixa, atualização/cancelamento geral, associação posterior de títulos a grupo, ou frontend.

## Artefatos e provas

Contrato executável/export: `postman/canteiro-io.openapi.json`, **OpenAPI 3.1.0, 133 paths, 221 schemas**. O export vem de `/v3/api-docs` da aplicação em teste, não de edição manual de JSON. Não implica publicação em `localhost:8081`. O teste contratual geral também validou a configuração com gateway habilitado, em artefato separado.

SHA-256:

```text
300F6FB254041923801EBBD944B7F4D0AC163E125F1F33B8146EC59E87B8A26D
```

Validação final: **69 testes em 18 suítes, zero falhas/erros/ignorados**, Checkstyle main/test sem erros. Inclui domínio, API/catálogos, upgrade V86→V87 com legado preservado, migração de banco vazio, OpenAPI, governança/auditoria, persistência de faturamento e regressão de imposto/cancelamento tributário. Há provas de EPI sem contrato, COMPANY sem obra, herança/compatibilidade de moeda, vínculos/isolamento, filtros/ordenação, rejeição de JSON numérico, aprovação sem efeito antecipado, referência desativada antes da aprovação, idempotência concorrente direta/pendente, versão otimista e bloqueio de exclusão física. A preparação de dois cenários foi corrigida para reutilizar o cliente final existente, sem alterar sua unicidade por tenant/documento.

Evidências locais ignoradas pelo Git: `tmp/validation-payables/final` (JUnit e Checkstyle). Não é alegação de execução integral do `check`/cobertura do projeto. Todos os bancos utilizados nesta validação são PostgreSQL/Testcontainers isolados; nenhuma migration foi aplicada pelo agente ao banco real. A aplicação da V87 e do código novo na API em uso depende de publicação autorizada; não foi verificado o estado atual do banco real nesta entrega. Nenhum arquivo do repositório frontend foi alterado.


Atualização F06-20/21 (06/11/2026): [contatos e instruções privadas de pagamento](FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md), com [export/pacote/testes atuais e ativação pendente](F06-20-21-VALIDATION-2026-11-06.md). Os hashes acima são snapshots das entregas anteriores; o arquivo OpenAPI atual inclui o contrato novo. Não confundir instrução de pagamento com baixa ou com publicação na API local.
