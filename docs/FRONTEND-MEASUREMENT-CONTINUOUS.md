# Medição contínua — integração implementada

Data: 14/10/2026. Base autenticada: `/api/v1/company`. Tipos devem ser gerados do OpenAPI da aplicação, nunca declarados manualmente a partir deste guia.

Atualização de moeda e layout **publicada em localhost:8081 com V84 aplicada em 14/10/2026**: [BRL/USD/EUR e relatórios oficiais](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md). `measurement.currency` e `financialSummary.currency` identificam a unidade imutável; não somar moedas distintas. Os relatórios passam a dez colunas visíveis, sem enums técnicos, com cifra em todo dinheiro e metadados de origem preservados no XLSX por versão. Em 15/10/2026, o frontend confirmou tipos sincronizados e moedas/downloads autenticados, incluindo filename oficial e preservação dos cinco aceitos após recriação: [F04-41 concluída](F04-41-LOCAL-PUBLICATION.md). Logo real aguarda o usuário, sem impedir o fallback seguro.

## Grade acumulada

Use `GET /measurements/{measurementId}/accumulated`. A resposta `MeasurementAccumulatedResponse` contém:

- `measurement`, `currentVersion` e `asOf` UTC;
- `services[]`, cada um com `service` (identidade/destino persistidos), `items`, `acceptedTotals`, `pendingTotals` e `compositions`;
- `acceptedTotals`, `pendingTotals`, `accumulatedTotals` e `financialSummary` oficiais.

Cada linha de `items[]` contém `item`, `continuousServiceId`, `rootItemId`, `measurementVersionId`, `versionNumber`, `readReference`, `status`, `presentationStatus` e `editable`. Use `readReference` para leitura, por exemplo `1.3` e `2.1`; não renumere itens de versões aceitas. Somente a `DRAFT` atual é editável.

Uma revisão incremental vazia continua exibindo os aceitos anteriores no corpo principal. Ela não copia esses itens para o rascunho. Preços anteriores ficam congelados; o próximo avanço pode usar outro preço. Correções formais substituem a posição efetiva da raiz e não somam snapshots históricos. Enquanto uma correção aguarda aceite, o original aparece como aceito e a proposta aparece separadamente.

A V83 retira operacionalmente apenas sobras comprovadas do backfill da V78, por origem transacional e ausência de vínculos. Não altera a V78, não apaga identidades, não regrava itens/reconhecimentos/composições e não agrupa serviços por títulos iguais. Serviços vazios criados intencionalmente, com destino, referência ou origem ambígua permanecem. A mesma filtragem vale para GET `/services`, GET `/accumulated` e PDF/XLSX acumulados.

Organização da tela e dos relatórios: título da medição → serviço contínuo → atividades → subtotal do serviço → próximo serviço → totais finais. Use `service.id`/`continuousServiceId` como chave. Para subtotal aceito, use `acceptedTotals.netAmount`; a variação ainda não aceita fica em `pendingTotals.netAmount`, separadamente. Use os totais gerais retornados, nunca some serviços no cliente. O PDF/XLSX traz os mesmos subtotais oficiais, inclusive em revisões corretivas. Não crie outro serviço ao abrir uma revisão: acrescente as atividades com o ID do serviço já retornado.

`pendingTotals` representa a **variação** proposta em revisão, podendo ser negativa em redução/anulação. Não é faturável. O desconto de cabeçalho é alocado oficialmente entre os serviços; não ratear no frontend. `accumulatedTotals` é uma projeção documental do aceito mais a variação pendente, não um limite comercial.

## Serviço contínuo e destino

| Método | Rota | Resposta |
|---|---|---|
| GET | `/measurements/{measurementId}/services` | Lista `MeasurementContinuousServiceResponse[]` |
| POST | `/measurements/{measurementId}/services` | `201` direto ou `202` com solicitação |
| PUT | `/measurements/{measurementId}/services/{continuousServiceId}/destination` | `200` direto ou `202` |
| PUT | `/measurements/{measurementId}/versions/{versionId}/items/{itemId}/service` | `200` direto ou `202` |
| GET | `/measurements/{measurementId}/contract-compositions` | Estados por origem aceita |
| POST | `/measurements/{measurementId}/contract-compositions/apply` | `200` com estados de todas as origens; algumas podem estar aguardando aprovação |

Crie um serviço contínuo e reutilize seu ID nos próximos avanços:

```json
{
  "serviceTitle": "Linha de vinhaça",
  "destinationAction": "CREATE_NEW_SERVICE"
}
```

Para complementar um serviço existente, use `destinationAction: "ADD_TO_EXISTING_SERVICE"` e `contractServiceId`. O contrato precisa estar vinculado e o serviço deve estar ativo, na mesma cadeia empresa/obra/contrato. O seletor existente é `GET /contracts/{contractId}/services` e exige `SERVICES.READ`.

O `PUT .../destination` exige `expectedVersion` igual ao `lockVersion` oficial do serviço contínuo. Título não é chave financeira. O destino vive no serviço contínuo e permanece após recarga, aceite e revisão. Quando o backend materializa um destino novo, ele devolve o `contractServiceId` criado. Não tente trocar esse destino depois do aceite por uma edição comum.

`continuousServiceId` é opcional no `CreateMeasurementItemRequest`, no item do `POST .../items/insert` e em cada linha de `PUT .../items/reorder`. O novo frontend deve enviá-lo para manter a identidade entre meses. Para uma linha já salva, o `PUT .../items/{itemId}/service` recebe:

```json
{ "continuousServiceId": "uuid-do-servico-continuo" }
```

Compatibilidade: itens antigos sem vínculo podem aparecer em agrupamento com ID nulo. Ao aceitar uma versão legada, a API cria identidades locais daquela versão. Ela **não deduz continuidade entre meses ou destino contratual por nomes iguais**. Prepare explicitamente o vínculo quando necessário.

Quando há conversão individual pelo fluxo antigo, o resumo contínuo informa `LEGACY_ORIGIN_REQUIRES_RECONCILIATION`, mantendo o aceito documental e devolvendo financeiro protegido/sem atribuição segura como `null`. Não inventa zero recebido nem migra recebimentos automaticamente. Esses registros precisam de reconciliação financeira explícita antes de usar o novo fluxo de composição; histórico e relatórios documentais continuam disponíveis.

O mesmo estado é usado se uma atribuição histórica imutável ultrapassar a capacidade de sua origem ou divergir do título. A consulta não regrava esse histórico nem devolve saldo inventado; uma nova baixa incompatível é bloqueada com `409` até reconciliação.

## Aceite externo não é aprovação de composição

As rotas de workflow/link permanecem. Ao registrar um aceite ou vincular posteriormente um contrato, o backend coordena somente os incrementos aceitos com destinos oficiais. O frontend não precisa criar `ContractService` e atualizar seu valor por uma sequência própria.

- `FIXED`: a composição é um acréscimo auditável (`CONTRACT_ADDITION`), sem regravar a base negociada.
- `MEASUREMENT_BASED`: a composição forma progressivamente o valor (`MEASUREMENT_PRICING`).
- Repetição não duplica composição, serviço nem solicitação pendente por grupo de origem.

`MeasurementCompositionState` contém `continuousServiceId`, `measurementVersionId`, `measurementServiceId`, `measurementContractCompositionId`, `state` e `changeRequestId`. Estados: `APPLIED`, `REVERSED`, `AWAITING_APPROVAL`, `NO_CONTRACT`, `NO_DESTINATION`, `AUTHORIZATION_REQUIRED`.

Permissões `MEASUREMENTS` não concedem `SERVICES`. Pode haver aceite oficial e composição comercial pendente. `202` de uma operação contém somente a solicitação e modo; não substitua dados oficiais por uma entidade fictícia. `POST .../contract-compositions/apply` devolve estados por origem, inclusive pedidos pendentes, em `200`; não interprete esse status como aprovação de todas as composições.

Origem aceita ainda não composta não está disponível para faturar. Conversões individuais legadas são preservadas, mas não podem ser combinadas com composição contínua da mesma origem; a API exige reconciliação explícita em vez de duplicar valor.

## Financeiro oficial

A revisão [F05-14](F05-14-FINANCIAL-INVARIANTS-REVIEW.md) valida a cadeia completa pelos GETs oficiais, nos modos `FIXED` e `MEASUREMENT_BASED`. Ao receber `APPLIED`, recarregue `GET /contracts/{contractId}/services` (base, composição e posição efetiva) e `GET /billing-control/contracts/{contractId}` (subtotal, desconto e líquido). Não some valores de medição localmente. `200` do coordenador pode conter origens `AWAITING_APPROVAL`; somente a aprovação da própria composição atualiza a posição comercial. As respostas de alocações, NFs, impostos, títulos/saldos e recebimentos agora também usam dinheiro em string decimal canônica.

`GET /measurements/{measurementId}/financial-summary` exige `MEASUREMENTS.READ`, `BILLING.READ` e `RECEIVABLES.READ`. A consulta acumulada exige somente `MEASUREMENTS.READ` e omite valores financeiros protegidos quando necessário.

```json
{
  "acceptedAmount": "10000.00",
  "billedAmount": "7000.00",
  "receivedAmount": "4000.00",
  "acceptedAvailableToBill": "3000.00",
  "billedOutstandingAmount": "3000.00",
  "remainingToReceive": "6000.00",
  "financialAdjustmentAmount": "0.00",
  "acceptedUncomposedAmount": "0.00",
  "financialAvailability": "AVAILABLE",
  "asOf": "2026-10-13T15:00:00Z"
}
```

`remainingToReceive` inclui aceito não faturado e faturado ainda devido, considerando descontos contratuais atribuídos, retenções, créditos e estornos. Não usar apenas `billedOutstandingAmount` no rótulo “Falta receber”. Não somar/subtrair campos no cliente.

`services[]` é consolidado por `continuousServiceId` e contém `compositionIds`. Os campos legados singulares de versão/grupo/composição ficam nulos quando houver várias origens. Toda quantidade monetária da projeção é string decimal canônica. Valores indisponíveis são `null`, nunca zeros fictícios; o OpenAPI 3.1 declara `string | null`. `financialAvailability` distingue `AVAILABLE`, `PARTIALLY_COMPOSED`, `RECONCILIATION_REQUIRED`, `LEGACY_ORIGIN_REQUIRES_RECONCILIATION`, `NO_CONTRACT` e `NOT_AUTHORIZED`. Uma redução aceita ainda aguardando reconciliação comercial bloqueia a disponibilidade para novo faturamento daquela identidade; não usa o limite obsoleto da composição anterior.

Nas alocações de faturamento de origem em medição, envie `measurementContractCompositionId` junto de `contractServiceId`. O backend valida o limite específico e o limite do contrato na mesma transação. NF referencia a alocação e não consome o saldo novamente. Valores sem origem podem usar a base negociada do serviço; não omita a origem para faturar uma composição.

Uma baixa parcial de título com várias origens é atribuída oficialmente e persistida por alocação. O rateio fecha os centavos, respeita capacidades e tem resíduo determinístico por UUID. Estorno usa as parcelas exatas da baixa original. Duas medições do mesmo serviço não recebem integralmente a mesma baixa.

## Créditos e renegociação de títulos

| Método | Rota | Comportamento |
|---|---|---|
| POST | `/receivables/{receivableId}/credits` | `201` crédito imutável ou `202`; `{ amount: "50.00", reason: "..." }` |
| POST | `/receivables/{receivableId}/credits/{creditId}/reversal` | `201` estorno do crédito ou `202`; exige motivo |
| GET | `/receivables/{receivableId}/renegotiations` | `200`, títulos descendentes e históricos |
| POST | `/receivables/{receivableId}/renegotiations` | `200` títulos oficiais ou `202`; reprograma somente saldo não pago |
| POST | `/receivables/{receivableId}/renegotiations/reversal` | `200` cancela títulos descendentes logicamente e reabre o original, ou `202` |

Renegociação recebe `reason` e `installments: [{ dueOn: "2027-01-01", amount: "100.00" }]`. A soma precisa ser exatamente o saldo atual; vencimentos não podem ser passados. Não há juros, nova receita, nova alocação ou baixa fictícia. Cada descendente preserva `billingInstallmentId` e `financialGroupId`. Consulte o GET para recuperar seus IDs e use as rotas normais de recebimento/estorno nesses títulos.

Crédito reduz o devido, não representa dinheiro recebido e não libera capacidade de faturamento. Antes de reverter renegociação, reverta recebimentos/ajustes e renegociações descendentes. Antes de cancelar faturamento ou reverter baixa original renegociada, reabra a renegociação. Redução de medição com faturamento ativo continua bloqueada até estornar/cancelar esse comprometimento; um crédito isolado não apaga a alocação canônica.

## Relatórios e importação

| GET | Conteúdo |
|---|---|
| `/measurements/{measurementId}/accumulated/pdf` | PDF completo, attachment |
| `/measurements/{measurementId}/accumulated/spreadsheet` | XLSX completo, attachment |
| `/measurements/{measurementId}/accumulated/spreadsheet/preview` | Mesmo conteúdo acumulado em PDF inline |

Downloads exigem `MEASUREMENTS.EXPORT`, preservam tenant e auditoria e usam `Cache-Control: no-store`. Marca privada, título centralizado, serviços, itens aceitos e avanço atual estão no mesmo relatório. Totais aceitos e variação pendente ficam separados; o resumo simples mostra aceito/recebido/falta receber ou indisponibilidade financeira. Filename vem do título sanitizado. Não há URL pública.

O XLSX acumulado é **relatório, não planilha de sincronização**. O importador rejeita essa folha explicitamente. Continue usando o XLSX de `versions/{versionId}/spreadsheet` para importar somente a `DRAFT` atual. Omitir item aceito na planilha do novo avanço não o inativa. Importação, inserção e reorganização não alteram números, preço ou origem de itens aceitos.

Histórico, detalhe, serviços e PDF/XLSX por versão continuam disponíveis e preservam o contrato histórico. Erros usam Problem Details/correlationId: `400` validação, `403` alçada/permissão, `404` inexistente ou fora do tenant, `409` estado/concorrência/conflito de origem.
