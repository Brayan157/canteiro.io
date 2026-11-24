# Contas a receber — listagem e detalhe básico (F05-25 / FRONT-F08-11)

Complemento F05-33 validado/publicado em 8081 em 27/10/2026: `financialView` e `financialPosition` de lista/detalhe estão descritos no [guia aditivo](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md). As restrições históricas abaixo sobre ausência de posição financeira são substituídas apenas por esse complemento; status persistido continua distinto.

Contrato implementado e validado em 26/10/2026, backend somente. OpenAPI executável exportado da aplicação de teste; não confundir com disponibilidade na API local. Nenhum deploy, reinício, push ou operação financeira real integra o recorte.

## Rotas e autorização

```text
GET /api/v1/company/receivables
GET /api/v1/company/receivables/{receivableId}
```

Ambas exigem Bearer e **RECEIVABLES.READ**; não exigem `BILLING.READ`. Tenant vem exclusivamente da sessão. Referências mínimas de contrato, obra, cliente e parcela são parte da projeção de recebíveis; isso não autoriza abrir detalhes/listas dos outros módulos. Cada rota relacionada continua exigindo sua própria permissão.

Complemento posterior F05-26: o histórico contextual de recebimentos/estornos possui [contrato próprio](FRONTEND-RECEIVABLE-SETTLEMENT-HISTORY.md). Não integra as provas/escopo original F05-25 abaixo nem altera a autoridade do endpoint de saldo.

Não há criação, baixa, renegociação, atualização de atraso, auditoria de alteração ou efeito financeiro nos GETs. A leitura não usa lock de mutação nem incrementa versões. Só existem títulos oficiais na resposta: uma solicitação `202` não vira registro de recebível. Não há histórico de baixas nesta entrega (FRONT-F08-15 permanece separado).

## Paginação, filtros e ordenação

Exemplo sem UUID digitado:

```text
GET /api/v1/company/receivables?search=Usina&dueFrom=2026-11-01&dueTo=2026-11-30&page=0&size=20&sort=dueOn,asc
```

| Parâmetro | Contrato |
|---|---|
| `search` | Trecho de referência do título/parcela, nome/referência/identificador do contrato, nome/referência da obra ou nome do cliente final; até 200 caracteres. |
| `reference` | Trecho da referência do título, até 100. |
| `contractSearch` | Trecho de nome/referência/identificador comercial do contrato, até 200. |
| `workSearch` | Trecho de nome/referência da obra, até 200. |
| `customerName` | Trecho do nome do cliente final, até 200; não pesquisa documento. |
| `billingReference` | Trecho da referência da parcela de faturamento de origem, até 100. |
| `contractId`, `workId`, `finalCustomerId` | Contextos UUID opcionais fornecidos pela navegação/seleção. Não são necessários para pesquisar. Contexto inexistente/estrangeiro retorna página vazia. |
| `status` | Situação **persistida**, exatamente `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `OVERDUE`, `RENEGOTIATED` ou `CANCELLED`. Omitido inclui todas. Não é filtro de atraso calculado. |
| `currency` | `BRL`, `USD` ou `EUR`, exato. Moeda imutável herdada do contrato, sem conversão. |
| `dueFrom`, `dueTo` | Limites inclusivos do vencimento próprio do título, `yyyy-MM-dd`. Intervalo invertido é inválido. |
| `page`, `size` | Página desde 0, padrão 0; tamanho 1..100, padrão 20. Offset maior que `Integer.MAX_VALUE` é inválido. |
| `sort` | Somente `dueOn`, `issuedOn`, `reference`, `status`, `createdAt`, `contractName`, `workName`, `finalCustomerName`; formato `campo,asc`/`campo,desc`, repetível. |

Filtros combinam-se por AND; `search` combina seus campos por OR. Texto é aparado, vazio equivale à ausência, comparação sem distinguir maiúsculas/minúsculas, **literal**, inclusive `%`, `_` e `!` (não são curingas). Não pesquisa descrição livre, documento/CPF/CNPJ, telefone, e-mail, dados tributários/bancários, Pix, itens/medições ou metadados de baixa.

Ordem padrão: `dueOn asc, id asc`; campos nulos ao final em qualquer direção, desempate sempre `id asc`. Count e página usam a mesma cadeia tenant/contrato/obra/cliente/parcela, em snapshot transacional de leitura. A consulta não junta alocações/NFs para não duplicar títulos por multiplicidade de origem.

## Respostas

Lista: `PageResponseReceivableSummaryResponse` com `items`, `page`, `size`, `totalItems`, `totalPages`. Cada item `ReceivableSummaryResponse`:

```json
{
  "id": "uuid-do-titulo",
  "reference": "REC-01",
  "contractId": "uuid-do-contrato",
  "contractName": "Contrato Usina",
  "contractReference": "CTR-REF",
  "contractIdentifier": "0001/2027",
  "workId": "uuid-da-obra",
  "workName": "Fábrica de açúcar",
  "workReference": "OB-REF",
  "finalCustomerId": "uuid-do-cliente",
  "finalCustomerName": "Cliente sintético",
  "billingInstallmentId": "uuid-da-parcela",
  "billingInstallmentReference": "PARC-01",
  "financialGroupId": "uuid-do-grupo",
  "renegotiationSourceReceivableId": null,
  "renegotiationSourceReference": null,
  "issuedOn": null,
  "dueOn": "2026-11-25",
  "status": "OPEN",
  "currency": "BRL",
  "originalAmount": "100.00",
  "createdAt": "2026-10-26T15:00:00Z",
  "updatedAt": "2026-10-26T15:00:00Z"
}
```

Identificadores ilustrativos devem ser UUIDs reais no uso. O detalhe `ReceivableDetailsResponse` retorna `{ "receivable": <mesmo resumo>, "description": null }`; descrição é a própria descrição comercial cadastrada no título, opcional, não conteúdo fiscal ou histórico de recebimento. Nomes/referências de contexto são identificações atuais, não snapshots fabricados de cadastros anteriores.

Campos opcionais/nulos: `reference`, `contractReference`, `contractIdentifier`, `workReference`, `billingInstallmentReference`, `renegotiationSourceReceivableId`, `renegotiationSourceReference`, `issuedOn`; `description` no detalhe. Demais 15 campos do resumo são obrigatórios. Datas de negócio são locais; timestamps de registro são UTC. Todo dinheiro é string decimal canônica com duas casas; não converter para ponto flutuante para cálculos.

`originalAmount` é **valor de face histórico do título**, não valor devido atual, líquido tributário ou saldo. `status` é a situação oficial **registrada**, sem simular atualização por data na consulta. Para situação financeira atual/atraso e saldo, consumir a rota já existente, também protegida por `RECEIVABLES.READ`:

```text
GET /api/v1/company/receivables/{receivableId}/balance
```

Essa rota permanece a autoridade para `outstandingAmount`, `confirmedSettlementAmount`, `withheldAmount`, `creditedAmount`, `netReceivableAmount`, `status` atual e `currency`. Exibir a coluna de `status` da lista como **Situação registrada**; não derivar atraso comparando datas no frontend. Não somar valores de títulos ou moedas para criar saldo local.

## Histórico e origem

Títulos cancelados/renegociados continuam legíveis e filtráveis. Descendentes mantêm sua própria data de vencimento, valor de face, moeda do contrato, grupo financeiro e parcela exatos. `renegotiationSourceReceivableId` aponta ao título anterior real e `renegotiationSourceReference` oferece identificação legível quando cadastrada; são nulos no original. A cadeia é resolvida dentro da mesma empresa/contrato/parcela, sem inferência por nome. Sem referência cadastrada, usar rótulo humano de ausência, não obrigar UUID como texto operacional.

**Nunca somar o título renegociado com seus descendentes como se fossem novas receitas/compromissos.** Lista é inventário histórico, não consolidado financeiro. As alocações/composições/origens financeiras permanecem inalteradas no backend; esta resposta não as expande nem concede permissão sobre medições, NFs, tributos ou faturamento. Continuar usando projeções oficiais para consolidação.

## HTTP e erros

- `200 application/json`, inclusive página vazia; `Cache-Control` contém `no-store` (atualmente `no-cache, no-store, max-age=0, must-revalidate`, pelo Spring Security).
- `400 application/problem+json`: paginação, sort, UUID/enum/data, comprimento ou intervalo inválidos.
- `401 application/problem+json`: sem autenticação válida.
- `403 application/problem+json`: sem permissão/contexto autorizado.
- `404 application/problem+json`: detalhe ausente, estrangeiro ou cadeia incoerente; sem revelar qual entidade falhou.

Problem Details inclui `correlationId`. Não há `201`/`202` nessas leituras. Não declarar manualmente tipos frontend; gerar pelo OpenAPI exportado após validação.

## Provas e publicação

**22 testes em 6 suítes, sem falhas, erros ou ignorados**, com Checkstyle main/test sem erros e `git diff --check` limpo. Inclui 9 testes de integração novos: acesso somente RECEIVABLES.READ sem liberar BILLING/CONTRACTS, `401/403/404`, duas empresas, filtro/contexto/count/paginação no tenant, filtros literais e não documentais, parâmetros inválidos, sort/desempate/nulos, moeda BRL/USD/EUR e decimal canônico, renegociação/reversão pelo fluxo oficial com pai/filhos históricos, proposta real `202` invisível, ausência de efeito nos snapshots de títulos/parcelas/alocações/baixas/tributos/auditoria/propostas, e compatibilidade da autoridade de saldo para recebimento parcial/atraso. Testes unitários cobrem filtros, autorização antes da consulta, limite do offset e tenant de leitura; regressão de saldo e criação de baixa preservada. O primeiro ensaio encontrou somente expectativa excessivamente restrita de Cache-Control no teste; a expectativa foi corrigida para exigir a diretiva `no-store`, sem enfraquecer o header da aplicação.

Não houve alteração de schema: reusa tabelas, índices e FKs compostas existentes, inclusive a linhagem V82. Não criar migration vazia para GET.

Export: [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), obtido de `GET /v3/api-docs` da aplicação em teste, com PostgreSQL temporário e gateway desabilitado: **OpenAPI 3.1.0, 137 paths e 232 schemas**. Acréscimos: `ReceivableSummaryResponse`, `PageResponseReceivableSummaryResponse`, `ReceivableDetailsResponse`; resumo com 15 campos obrigatórios/8 opcionais nulos, dinheiro `string/decimal`, segurança Bearer herdada globalmente, filtros e Problem Details documentados. O contrato com gateway habilitado foi validado/exportado separadamente em `tmp/validation-receivable-read/gateway-enabled.openapi.json`. Nenhum JSON de contrato foi editado manualmente.

SHA-256 do export entregue:

```text
6DB1002A2E8E251942E8CBC6A8AA1D01276B1ED53136328F4773C78ACF0B1BE7
```

Execução reproduzível com JDK 21 e Docker disponível somente para Testcontainers:

```powershell
$env:RECEIVABLE_READ_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-receivable-read/gateway-enabled.openapi.json"
.\gradlew.bat test --tests '*ReceivableReadApiIntegrationTest' --tests '*ReadReceivablesUseCaseTest' --tests '*ReceivableFilterTest' --tests '*ReceivableBalanceTest' --tests '*ReceivableSettlementServiceTest' --tests '*OpenApiContractIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-receivable-read/build
```

Evidências locais ignoradas pelo Git: `tmp/validation-receivable-read/build/test-results/test` e `tmp/validation-receivable-read/build/reports/checkstyle`.

Essa validação **não publica** as rotas na instância antiga em `localhost:8081` nem confirma E2E contra ela; publicação depende de autorização posterior separada. O export permite gerar os tipos oficiais e integrar o recorte. Sem alteração do frontend neste repositório, schema/dados reais, infraestrutura ou histórico de baixas.
