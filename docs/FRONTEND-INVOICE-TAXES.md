# F05-24 — Consulta histórica dos tributos de uma NF

Concluído e validado em 26/10/2026 para o recorte de tributos de FRONT-F08-10. Sem publicação/reinício da API local.

## Rota e autorização

```http
GET /api/v1/company/invoices/{invoiceId}/tax-obligations?page=0&size=20&sort=createdAt,desc
Authorization: Bearer <accessToken>
```

Exige permissão efetiva `INVOICES.READ` do catálogo existente. Criar NF/tributo não concede leitura. O tenant vem exclusivamente da sessão; não há campo de empresa livre. `invoiceId` é obtido da lista/detalhe de NFs e usado internamente na navegação, sem exigir UUID digitável.

A consulta valida a cadeia NF → contrato → tributo na mesma empresa e, quando houver retenção, o recebível vinculado do mesmo tenant/contrato. Validação histórica não exige título ativo: cancelamento/renegociação não apaga o tributo. O GET não divulga IDs, referências, detalhes ou saldos de recebíveis/contas a pagar, mesmo quando o leitor possuir permissões adicionais desses módulos. Não é uma consulta de títulos ou baixas.

NF existente sem tributos retorna página vazia. NF ausente ou de outra empresa retorna `404`. NFs canceladas permanecem consultáveis e os tributos cancelados são incluídos por padrão, preservando valores/datas históricos. `202` de criação continua somente proposta e não aparece como tributo oficial.

## Paginação, filtros e ordem

| Parâmetro opcional | Regra |
|---|---|
| `page` | Padrão `0`, não negativo |
| `size` | Padrão `20`, entre `1` e `100`; offset acima de `Integer.MAX_VALUE` é inválido |
| `status` | Exato: `REGISTERED` ou `CANCELLED`; omitido inclui ambos |
| `treatment` | Exato: `PAYABLE_BY_COMPANY`, `WITHHELD_BY_CUSTOMER` ou `INFORMATIONAL`; omitido inclui todos |
| `sort` | `createdAt`, `name`, `treatment`, `status`, no formato `campo,asc` ou `campo,desc`; repetível |

Filtros de situação/tratamento combinam com AND e são aplicados no banco junto de tenant/NF/contrato, antes da paginação/contagem. Não há pesquisa por chave fiscal, documento, ID de título ou texto técnico livre. Ordem padrão `createdAt desc, id asc`; todas as ordenações desempatarão por `id asc`. Os campos de ordenação permitidos são não nulos; `amount`, `dueOn` e campos livres não são ordenações permitidas.

## Resposta

`200 application/json`, com `Cache-Control: no-store` e `PageResponseInvoiceTaxObligationResponse`: `items`, `page`, `size`, `totalItems`, `totalPages`.

Cada item é `InvoiceTaxObligationResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "invoiceId": "00000000-0000-0000-0000-000000000002",
  "contractId": "00000000-0000-0000-0000-000000000003",
  "name": "Tributo informado",
  "description": null,
  "dueOn": "2026-11-25",
  "amount": "10.00",
  "treatment": "PAYABLE_BY_COMPANY",
  "status": "REGISTERED",
  "currency": "BRL",
  "createdAt": "2026-10-26T15:00:00Z",
  "updatedAt": "2026-10-26T15:00:00Z"
}
```

Exemplo ilustrativo. Obrigatórios/não nulos: `id`, `invoiceId`, `contractId`, `name`, `amount`, `treatment`, `status`, `currency`, `createdAt`, `updatedAt`. `description` e `dueOn` aceitam `null` no OpenAPI 3.1. A data segue o registro oficial; `PAYABLE_BY_COMPANY` já exige vencimento na criação existente, sem criar vencimento fictício para os outros tratamentos. Instantes são UTC, datas de negócio `YYYY-MM-DD`.

`amount` é sempre string decimal canônica positiva com duas casas, por exemplo `"10.00"`, sem símbolo ou separador de milhar, documentada `type: string`, `format: decimal`. O cancelamento mantém o valor histórico positivo com status `CANCELLED`; não transforma o valor em uma nova baixa negativa. `currency` vem do contrato imutável (`BRL`, `USD`, `EUR`), sem conversão ou soma entre moedas.

Os tratamentos oficiais mantêm sua semântica existente:

- `PAYABLE_BY_COMPANY`: obrigação tributária a pagar pela empresa, cujo título é produzido no fluxo de criação/aprovação já existente.
- `WITHHELD_BY_CUSTOMER`: retenção já registrada na origem do recebível; a consulta não a aplica novamente.
- `INFORMATIONAL`: registro informativo; consultar não cria título ou pagamento.

Situação do tributo não é situação de pagamento. Não recalcular saldo da NF/contrato no frontend somando/subtraindo tributos desta lista. O GET não calcula imposto, não cria Payable, não deduz valores outra vez, não altera títulos/retenções/recebimentos e não grava auditoria financeira. O POST e suas regras/respostas continuam inalterados, com `TaxObligationResponse` próprio da criação; não substituir esse contrato pelo DTO novo de leitura.

## Erros

`application/problem+json`, com `code` e `correlationId`:

| Status | Situação |
|---|---|
| `400` | UUID, enum, página, tamanho ou ordenação inválidos |
| `401` | Ausência de token ou token inválido |
| `403` | Sem `INVOICES.READ`, independentemente das permissões de criação ou de outros módulos |
| `404` | NF fora do tenant/inexistente ou cadeia histórica incompatível de NF/contrato/tributo/recebível |

Não expõe chave fiscal, storage ou dados de outro tenant nos erros. O GET não produz `201`/`202`, aprovação ou mutação financeira.

## Validação, export e limites da entrega

**26 testes em 6 suítes, sem falhas, erros ou ignorados**, com Checkstyle main/test sem erros. Cobertura de `401/403/404`, duas empresas, NF correta/irmã/ausente, constraints de cadeia contratual e recebível, defesa contra referências incompatíveis, três tratamentos, moeda BRL/USD/EUR e decimais, histórico cancelado pelo fluxo oficial, vazio/páginas/ordenação/filtros inválidos, propostas `202` invisíveis e ausência de efeitos nos snapshots de tributos/NF/itens/alocações/parcelas/títulos/baixas/auditoria/propostas. As consultas repetidas mantiveram a retenção em `10.00` e o saldo oficial de teste em `90.00`, sem criar outro Payable. Domínio/criação de tributo e leitura de NF também passaram na regressão.

Export oficial em [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado de `GET /v3/api-docs` da aplicação de teste com PostgreSQL isolado e gateway desabilitado: **OpenAPI 3.1.0, 135 paths, 229 schemas**. Schemas acrescentados: `InvoiceTaxObligationResponse` e `PageResponseInvoiceTaxObligationResponse`. Resposta `200 application/json`, Bearer, enums oficiais, 10 propriedades obrigatórias e 2 propriedades opcionais/nulas conferidas pelo teste contratual. Contrato com gateway habilitado validado/exportado separadamente em `tmp/validation-invoice-tax-read/gateway-enabled.openapi.json`.

SHA-256 do export entregue:

```text
86C564C403AF6C5BD95F6D1ED90D4BCC7C0C92277801E3819433D655B97C8BE3
```

Execução reproduzível com JDK 21 e Docker disponível para Testcontainers:

```powershell
$env:INVOICE_TAX_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-invoice-tax-read/gateway-enabled.openapi.json"
.\gradlew.bat test --tests '*InvoiceTaxReadApiIntegrationTest' --tests '*ReadInvoiceTaxesUseCaseTest' --tests '*TaxObligationServiceTest' --tests '*TaxObligationTest' --tests '*InvoiceReadApiIntegrationTest' --tests '*OpenApiContractIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-invoice-tax-read/build
```

Evidências locais em `tmp/validation-invoice-tax-read/build/test-results/test` e `tmp/validation-invoice-tax-read/build/reports/checkstyle`, ignoradas pelo Git. O export permite regenerar tipos sem declaração manual; esta entrega não valida E2E contra a instância antiga.

Não inclui listagem de recebíveis, histórico de baixas, pagamentos, emissão/consulta fiscal, motor tributário ou margem. Nenhuma migration/schema novo, alteração de banco real, operação financeira/documento real, início/reinício da API/Docker, deploy, infraestrutura ou push integra esta entrega. Para disponibilidade na instância `localhost:8081`, ainda será necessária publicação posterior autorizada.
