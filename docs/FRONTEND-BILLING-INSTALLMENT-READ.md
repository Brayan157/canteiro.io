# F05-20 — Listagem e detalhe de parcelas de faturamento

Solicitação: 15/10/2026. Validação final: 16/10/2026. Integração de `FRONT-F08-05`. Não inclui `FRONT-F08-06`, novas mutações, NF ou saldo/baixas de recebíveis.

## Correção de situação financeira — 05/11/2026

Implementação validada: 16 testes em quatro suítes, Checkstyle main/test e bootJar aprovados. OpenAPI executável atualizado e API local publicada/verificada em `localhost:8081` em 05/11/2026 às 12:08:09 (São Paulo). As duas rotas GET passam a acrescentar os mesmos campos já publicados nas alocações do controle de faturamento:

- `receivableFinancialStatusAvailable`: boolean obrigatório. Somente true com `BILLING.READ` + `RECEIVABLES.READ` e um título original com posição oficial.
- `receivableFinancialStatus`: obrigatório e anulável. `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `SETTLED_WITH_ADJUSTMENTS`, `CANCELLED` ou `RENEGOTIATED`. Sem permissão ou original, null; nunca inferir OPEN.
- `receivableReceivedWithWithholding`: boolean obrigatório e anulável. True somente para posição `SETTLED_WITH_ADJUSTMENTS`, saldo zero, dinheiro confirmado positivo líquido de estornos, retenção positiva e crédito zero. A tela pode exibir **Recebida — com retenção de imposto**, preservando o status oficial e sem chamar retenção de dinheiro. False para as demais posições disponíveis; null quando indisponível.

A situação principal da cobrança deve usar a projeção financeira autorizada, com fallback seguro para enum futuro/indisponibilidade. `status` continua sendo cadastral/histórico e pode permanecer `OPEN` mesmo com recebimento completo; pode ser exibido secundariamente. Filtro e ordenação por `status` continuam cadastrais, sem novo filtro financeiro implícito. `netAmount` permanece o total histórico das alocações, não saldo a receber.

A fonte é exclusivamente o título ORIGINAL da parcela. Não agregar sucessores de renegociação, dinheiro de outra parcela ou inferir recebimento pela NF. Listagem carrega posições em uma query de lote limitada aos IDs da página e ao tenant, sem consulta por linha ou varredura de todos os títulos do contrato. Detalhe usa o mesmo cálculo de domínio/repositório. As consultas não regravam status, auditoria ou lançamentos;202 não interfere. O frontend não calcula saldo nem busca um recebível por linha e deve separar caches pela permissão efetiva.

OpenAPI executável em `postman/canteiro-io.openapi.json`, SHA256 **ADD231B994E6E40F446E14F740B19F28F30D0010C2E7D803C5B8062674AEBC42**, 151 paths/263 schemas. Alterados somente os dois GETs e `BillingInstallmentSummaryResponse`/`BillingInstallmentDetailsResponse`; nomes/enums existentes preservados. Pacote candidato **6477462FE7FC9B9F6E7D8A7BB7F9DA74616F4416765CF72BE48FB32D17006121**, com F03-19, bibliotecas, configuração e migrations idênticas à referência ativa. Runtime confirmado com o mesmo JAR; `/v3/api-docs` retorna 200 application/json, 150 paths/263 schemas, componentes e caminhos compartilhados idênticos ao export (única ausência: webhook desabilitado). Ambos GETs anônimos retornam 401 Problem Details/correlationId. Schema91 e ambiente/storage preservados, Flyway/seeds false e versão anterior compatível guardada. [Pacote, validação e ativação](F05-20-INSTALLMENT-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-11-05.md). Frontend confirmou a integração local F08-05 com tipos regenerados, testes focados,31 testes de política e sete E2E mais uma reexecução do caso afetado em quatro larguras, build/scanner/lint da aplicação aprovados. O lint global permanece pendente por dependências em tmp, sem invalidar a entrega funcional; detalhes nas provas. Os parágrafos históricos abaixo refletem suas respectivas datas; o fluxo manual por NF/item foi entregue posteriormente na [F05-32](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md).

## Distinção entre cancelar cobrança, cancelar NF e receber

Atualização operacional em 27/10/2026: a contenção F05-31 já está ativa na API local 8081 e bloqueia todas as novas baixas legadas, inclusive com NF, preservando consultas e estornos. Consulte o [contrato de disponibilidade, data UTC e recebido oficial](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md); o fluxo manual completo por NF/item abaixo continua pendente.

Esclarecimento de produto confirmado em 26/10/2026, sem alteração das rotas de leitura deste guia:

- **Cancelar cobrança indevida:** preservar a ação existente e seus bloqueios por NF registrada, recebimento vigente, renegociação e demais vínculos. Exige justificativa, autorização e auditoria; não exclui fisicamente. Em `202`, a cobrança/título e a capacidade oficial permanecem inalterados até aprovação. A leitura não concede permissão de cancelamento.
- **NF registrada sem pagamento:** registrar NF não registra baixa. Seu cancelamento local permitido não cancela automaticamente a cobrança/título nem apaga a dívida; outra NF pode ser registrada sobre a capacidade documental liberada, sem duplicar a cobrança ou consumir novamente o saldo faturável. O app não cancela NF no fisco.
- **Recebimento explícito:** não inferir quitação pela existência da NF. A nova exigência de cobertura documental e a atribuição manual por item ainda não foram implementadas; não criar elegibilidade ou limites financeiros no frontend. Histórico/conciliação, substituição de NF já recebida e a migration de rastreabilidade continuam aguardando decisões/autorização.
- **Estorno:** correção justificada de baixa registrada por engano não é devolução bancária. Não orientar estorno fictício de recebimento verdadeiro somente para substituir uma NF.

## Rotas reais

```http
GET /api/v1/company/billing-installments?contractId=<uuid>&page=0&size=20&sort=dueOn,asc
GET /api/v1/company/billing-installments/{installmentId}
Authorization: Bearer <accessToken>
```

As duas exigem `BILLING.READ` e usam exclusivamente a empresa da sessão. Não aceitam tenant livre. Sem autenticação: `401`; sem a permissão: `403`; parcela inexistente/fora do tenant no detalhe: `404`. Parâmetro inválido: `400`. Erros são `application/problem+json` com `correlationId`.

Não exigem `MEASUREMENTS.READ`: apenas o UUID da origem financeira já vinculada à alocação é retornado, sem título, itens, conteúdo ou resumo da medição. O seletor separado de origens (`measurement-origins`) continua exigindo `MEASUREMENTS.READ` para revelar seu conteúdo legível.

## Listagem

Retorna `PageResponse<BillingInstallmentSummaryResponse>`: `items`, `page`, `size`, `totalItems`, `totalPages`.

Cada linha contém `id`, `contractId`, `reference`, `issuedOn`, `dueOn`, `status`, `currency`, `netAmount`, `createdAt`, `updatedAt`. Referência e emissão podem ser nulas. Moeda é a do contrato, sem câmbio nem soma entre unidades diferentes.

Filtros opcionais:

| Parâmetro | Regra |
|---|---|
| `contractId` | UUID exato no tenant; não correspondente/estrangeiro resulta em página vazia |
| `reference` | Trecho literal, sem distinguir maiúsculas/minúsculas, até 100 caracteres; `%`, `_` e `!` não viram curingas |
| `status` | Situação persistida exata: `DRAFT`, `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `OVERDUE`, `RENEGOTIATED`, `CANCELLED` |
| `dueFrom`, `dueTo` | Vencimento inicial/final inclusivos, `YYYY-MM-DD`; intervalo invertido é inválido |
| `page`, `size` | Padrão `0/20`; `page >= 0`, `1 <= size <= 100`; offset excessivo é inválido |
| `sort` | `dueOn`, `issuedOn`, `reference`, `status`, `createdAt`, com `,asc` ou `,desc`; pode repetir para ordenar por vários campos |

Ordem padrão: `dueOn asc, id asc`. Todas as ordenações usam nulos por último e desempate por `id asc`. Campos/direções livres são rejeitados. Não ordenar ou somar dinheiro no frontend para reconstruir a resposta.

Sem filtro de situação, canceladas e demais registros históricos também aparecem. O GET retorna o status persistido: não deriva nem grava atraso/recebimento a partir da data ou de títulos. A situação e o saldo do recebível são outro contrato.

## Detalhe

Retorna `BillingInstallmentDetailsResponse` com os mesmos campos de cabeçalho, `description`, `allocations`, `receivableReferencesAvailable` e `receivables`.

```json
{
  "id": "126a1900-502b-4985-8360-e3ee8565c530",
  "contractId": "d31b499a-efc0-4685-9147-797bde22d17a",
  "reference": "PARC-01",
  "description": "Primeira parcela oficial",
  "issuedOn": "2026-10-11",
  "dueOn": "2026-11-01",
  "status": "OPEN",
  "currency": "BRL",
  "netAmount": "140.00",
  "allocations": [
    {
      "allocationId": "adf1c9bf-d354-48cd-8d57-859b6e314f83",
      "type": "SERVICE",
      "status": "ACTIVE",
      "contractServiceId": "8b04d5e7-54ad-4c6e-8fbd-a50b796e724a",
      "contractServiceName": "Tubulação contratual",
      "measurementContractCompositionId": "794b4b83-059f-4f7b-91ec-6c1f5829fb33",
      "allocatedAmount": "150.00"
    },
    {
      "allocationId": "13ad64e2-c1e0-4842-9834-14fe503f984a",
      "type": "CONTRACT_DISCOUNT",
      "status": "ACTIVE",
      "contractServiceId": null,
      "contractServiceName": null,
      "measurementContractCompositionId": null,
      "allocatedAmount": "-10.00"
    }
  ],
  "receivableReferencesAvailable": false,
  "receivables": [],
  "createdAt": "2026-10-15T15:00:00Z",
  "updatedAt": "2026-10-15T15:00:00Z"
}
```

IDs/valores do exemplo são ilustrativos. Alocações têm schema `BillingInstallmentAllocationResponse`, ordem estável por UUID e preservam origens distintas mesmo no mesmo `contractServiceId`. Sem origem de medição, `measurementContractCompositionId` é nulo. Nome é do serviço contratual, nunca inferido pelo título da medição. Desconto contratual é negativo e não possui serviço/origem.

`netAmount` é a soma canônica oficial das alocações **com seus sinais**, incluindo as preservadas no cancelamento. Não é capacidade ativa, saldo ainda devido, dinheiro recebido ou soma dos títulos renegociados. Cancelar a parcela mantém seu valor histórico e suas alocações, com os status oficiais; o comprometimento ativo é consultado no controle de faturamento. Dinheiro é sempre string decimal canônica de duas casas (`"140.00"`, `"-10.00"`), nunca `number`.

Com `RECEIVABLES.READ`, `receivableReferencesAvailable=true` e `receivables` contém somente `{ id, reference }` (`BillingReceivableReferenceResponse`) dos títulos oficiais vinculados, incluindo históricos/descendentes de renegociação. Sem essa permissão, o flag é `false` e a lista é vazia **por restrição de acesso**, não por inexistência de títulos. Não há saldo, valor, baixa, vencimento ou outros dados financeiros do recebível nessa projeção.

Criação em `202` permanece somente `ChangeRequest`: não aparece na lista nem produz ID fictício de parcela consultável. Os GETs não alteram auditoria, aprovação, status ou valores e usam snapshot transacional coerente. Não foram criadas migrations ou alteradas regras de criação/cancelamento.

## Export e validação

Concluído. O contrato foi exportado diretamente de `GET /v3/api-docs` da aplicação de testes para `postman/canteiro-io.openapi.json`, sem edição manual de JSON: **OpenAPI 3.1.0, 125 paths, 200 schemas**, com o gateway desabilitado como na configuração local. O teste contratual geral também validou separadamente o perfil com gateway habilitado.

SHA-256 do snapshot entregue em F05-20 (para o export mais recente, incluindo faturamento líquido e metadados de alocação, consulte [F05-21](FRONTEND-NET-BILLING.md)):

```text
1BBEF20C4D78A74D84CDDFC2EE33D153A272955E5BA0A73E020484AD608819F8
```

Schemas novos:

- `PageResponseBillingInstallmentSummaryResponse`;
- `BillingInstallmentSummaryResponse`;
- `BillingInstallmentDetailsResponse`;
- `BillingInstallmentAllocationResponse`;
- `BillingReceivableReferenceResponse`.

As respostas `200` declaram `application/json`, com `bearerAuth` e erros Problem Details. Campos opcionais que retornam nulo, como emissão, referência, serviço do desconto e origem ausente, declaram a união com `null` no OpenAPI 3.1. Não criar tipos manuais para contornar o contrato.

Provas: **11 testes distintos em quatro suítes** (13 execuções, incluindo duas repetições de contrato), zero falhas/erros/ignorados, e Checkstyle main/test sem erros. Cobertura de tenant/permissões, restrição de referências de recebíveis, valores oficiais/desconto negativo, duas origens no mesmo serviço, histórico cancelado, `202` ausente dos registros oficiais, filtros literais/datas, paginação/ordenação controladas e moeda USD preservada. Evidências XML locais em `tmp/validation-installment-read/business` e `tmp/validation-installment-read/contract`, ignoradas pelo Git.

Não houve deploy, reinício da instância `localhost:8081`, recriação dos containers da API ou mudança de infraestrutura/schema/dados reais neste recorte. PostgreSQL/Testcontainers foi usado somente para testes isolados. O export permite integrar/regenerar tipos; uso efetivo das rotas na instância antiga dependerá de publicação posterior separada.
