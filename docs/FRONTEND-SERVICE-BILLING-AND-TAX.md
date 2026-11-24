# Cobrança por serviço e conferência tributária — F05-27/F05-28

26/10/2026. Backend implementado e validado, com OpenAPI executável/exportado. Gere tipos do export definitivo abaixo. Não houve publicação/reinício da API ou operação financeira real.

## Decisões humanas aplicadas

- Cobrança parcial: **primeiro a base negociada inicial do serviço; depois as composições de medição por data oficial de aceite crescente**. Não há consumo proporcional.
- Empate de data de aceite: `measurementId`, `measurementVersionId`, `compositionId` em ordem lexicográfica crescente. A data é `externalAcceptanceOn`, não data de criação da composição. Origem sem data oficial não é presumida como mais antiga: fica fora da capacidade utilizável.
- Alíquota tributária positiva informada pelo usuário com até quatro casas; sem arredondamento de entrada, normalizada com quatro casas na resposta/registro. Dinheiro/base com duas casas, resultado oficial `HALF_UP` com duas casas, regra monetária já vigente. O sistema não escolhe base, incidência, regime, percentual, tratamento, isenção ou vencimento.

## 1. Disponível oficial por serviço

```text
GET /api/v1/company/billing-control/contracts/{contractId}/net-capacity/services?page=0&size=20
```

Permissões atuais preservadas: **BILLING.READ e MEASUREMENTS.READ**. Tenant vem da sessão. `page>=0`, `size=1..100`, padrão 0/20; offset limitado. Ordem fixa por ID do serviço em ordem lexicográfica, sem sort livre.

Resposta `ServiceNetBillingCapacityResponse`:

```json
{
  "contractId": "uuid",
  "currency": "BRL",
  "capacityVersion": "sha256-de-64-caracteres",
  "coverageComplete": true,
  "totalServices": 1,
  "services": {
    "items": [{
      "contractServiceId": "uuid",
      "serviceName": "Montagem",
      "originCount": 3,
      "usableOriginCount": 3,
      "coverageComplete": true,
      "availableNetAmount": "1000.00",
      "baseNetAvailableAmount": "600.00",
      "measurementNetAvailableAmount": "400.00",
      "blockedNetAmount": "0.00"
    }],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

São valores completos do serviço, calculados sobre **todas as origens canônicas antes da paginação dos serviços**. Não somar páginas/posições no frontend. `originCount` inclui a posição-base mesmo com zero; `usableOriginCount` conta posições elegíveis com líquido positivo. `coverageComplete=true` significa cobertura das posições canônicas existentes, não inclusão de aceite documental ainda sem composição ou de proposta pendente.

`availableNetAmount` é o líquido efetivamente utilizável; `blockedNetAmount` é separado. Base e medição são informativas e não precisam ser escolhidas no formulário. Sem capacidade, não simular saldo: apresentar indisponibilidade conforme valores/erro oficial. BRL/USD/EUR são herdadas do contrato; dinheiro é sempre string decimal. A versão cobre capacidades, compromissos e datas de aceite usadas na resolução. GET não reserva capacidade nem altera dados.

## 2. Criar cobrança por serviço

```text
POST /api/v1/company/billing-installments/net/by-service
```

`CreateServiceNetBillingRequest` usa o mesmo cabeçalho comercial do fluxo líquido anterior:

```json
{
  "operationId": "uuid-novo-da-intencao",
  "contractId": "uuid",
  "capacityVersion": "versao-oficial-consultada",
  "reference": "Parcela 01",
  "description": null,
  "issuedOn": "2026-10-26",
  "dueOn": "2026-11-25",
  "financialGroupId": null,
  "services": [
    {"contractServiceId": "uuid", "mode": "PARTIAL", "netAmount": "650.00"}
  ],
  "justification": null
}
```

Para “Cobrar todo o disponível”, enviar `{ "contractServiceId": "uuid", "mode": "ALL_AVAILABLE" }`, sem `netAmount`. `PARTIAL` exige valor positivo canônico com duas casas; número JSON é rejeitado. Lista de 1..100 serviços, cada ID exatamente uma vez. Não enviar composição, desconto, valor bruto ou rateio manual. Até 10.000 alocações exatas podem ser resolvidas na transação; excesso é conflito explícito, nunca corte silencioso.

O backend consome a base líquida livre primeiro e, depois, cada composição elegível pela ordem publicada, usando `min(restante, disponível)` em centavos. Exemplo sem desconto: base `600.00`, composição B aceita em 14/10 `100.00`, composição A aceita em 15/10 `300.00`; cobrar `650.00` gera base `600.00` + B `50.00`, sem consumir A. Repetir não reaplica o total.

Distribuição de **desconto contratual livre**, cálculo de base bruta consumida e atribuição posterior de baixas permanecem nas regras oficiais existentes; não confundir essas regras com a nova prioridade de consumo. Preços/aceites/compromissos antigos não são regravados. Alocações continuam preservando composições exatas até NF, recebível, baixa e estorno.

Alçada: **BILLING.CREATE efetiva**, ou solicitação/aprovação conforme catálogo. Resposta existente `BillingInstallmentApprovalResponse`: `201` direto, `200` replay da mesma intenção aplicada, `202` somente proposta, com entidades oficiais nulas. Mesmo `operationId`/corpo repete resultado; alteração de corpo ou versão obsoleta dá `409`. Guardar/reutilizar a intenção no retry; não gerar ID novo automaticamente para contornar conflito.

Em `202`, a resolução exata é guardada na proposta, mas não reserva saldo. Aprovação revalida versão e resolução sob lock contratual. Não amplia um “todo disponível” silenciosamente se novos avanços surgirem. Legado `/billing-installments/net` e seu request por origem permanecem compatíveis; omitir composição nele continua significando somente base.

## 3. Conferência oficial de tributo

```text
POST /api/v1/company/invoices/{invoiceId}/tax-obligations/preview
```

Requer alçada **INVOICES.CREATE** direta ou de solicitação, tenant e NF registrada. `CalculateInvoiceTaxRequest` aceita exatamente um modo:

```json
{"mode":"AMOUNT","amount":"25.00"}
```

ou

```json
{"mode":"PERCENTAGE","baseAmount":"1000.00","ratePercent":"2.1234"}
```

Campos monetários são strings positivas com duas casas; alíquota é string decimal positiva com até quatro casas. Não enviar `amount` junto com base/alíquota. Percentual usa `base × alíquota / 100` exato e arredonda somente o resultado final para centavos (`HALF_UP`). Não inferir base do total da NF. Não há limite fiscal de 100% presumido pelo sistema; há apenas precisão técnica e valor final suportado. Resultado que arredonde a zero continua inválido, preservando o domínio atual de tributo positivo.

Resposta `InvoiceTaxCalculationResponse`:

```json
{
  "invoiceId":"uuid",
  "taxObligationId":null,
  "mode":"PERCENTAGE",
  "baseAmount":"1000.00",
  "ratePercent":"2.1234",
  "amount":"21.23",
  "currency":"BRL",
  "rounding":"HALF_UP_2",
  "recorded":false
}
```

Preview não cria imposto, conta a pagar, retenção, recebimento, intenção idempotente ou evento de criação. Não retorna escolha de tratamento nem valida incidência fiscal. Mostre a resposta para conferência; não trate cálculo do navegador como oficial.

## 4. Registrar tributo conferido

```text
POST /api/v1/company/invoices/{invoiceId}/tax-obligations/calculated
```

`CreateCalculatedInvoiceTaxRequest`:

```json
{
  "operationId":"uuid-novo-da-intencao",
  "name":"Tributo informado",
  "description":null,
  "treatment":"INFORMATIONAL",
  "receivableId":null,
  "dueOn":null,
  "calculation":{"mode":"PERCENTAGE","baseAmount":"1000.00","ratePercent":"2.1234"},
  "confirmedAmount":"21.23",
  "justification":null
}
```

`confirmedAmount` deve ser exatamente o valor conferido; divergência é `400`, sem substituição silenciosa. Recalcular/revalidar é responsabilidade do backend, também na aprovação. A prévia é conferência aritmética sem reserva de saldo: título/NF podem mudar antes da gravação e as regras financeiras podem produzir `409`.

Tratamentos preservados:

- `PAYABLE_BY_COMPANY`: exige vencimento, proíbe recebível e cria uma conta a pagar de escopo WORK pela regra existente.
- `WITHHELD_BY_CUSTOMER`: exige recebível da mesma cadeia; valida capacidade ainda devida e não cria Payable nem baixa fictícia.
- `INFORMATIONAL`: não reduz saldo nem cria obrigação/pagamento, e não recebe vínculo a recebível.

Resposta `CalculatedTaxObligationCreationResponse`: `taxObligation`, `calculation`, `payableId`, `changeRequestId`, `mode`. Campos ausentes são omitidos, não preenchidos com objetos fictícios ou null. `201` direto, `200` replay; `202` contém somente `changeRequestId` e `mode=REQUEST_APPROVAL`, sem `taxObligation/calculation/payableId`. Operação idempotente por empresa/UUID, com conteúdo imutável; mudar conteúdo dá `409`. Proposta decidida sem aplicação exige nova intenção; repetição concorrente não duplica imposto/Payable/retenção. Nomes e valores iguais não são usados como chave presumida de tributo.

O POST anterior de tributo por `amount` permanece igual. A nova V89 é aditiva: mantém modo/base/alíquota/valor e vínculos da intenção/aprovação em `invoice_tax_operation`, com FKs de tenant/cadeia e proteção contra alteração/exclusão, exceto anexar uma única vez o resultado da aprovação. Não reescreve os tributos existentes.

## 5. Ler a origem aritmética registrada

```text
GET /api/v1/company/invoices/{invoiceId}/tax-obligations/{taxObligationId}/calculation
```

Exige **INVOICES.READ**, não concedida automaticamente pela criação. Retorna `InvoiceTaxCalculationResponse` com ID do tributo e `recorded=true` quando existe intenção nova registrada, inclusive após cancelamento da NF/tributo. Para legado por valor: `mode=AMOUNT`, valor histórico, base/alíquota nulas, `recorded=false` (não inventa origem percentual). Sem saldo/IDs de Payable ou recebível. Lista histórica de tributos existente continua com seu contrato mínimo; abrir esta rota quando precisar dos dados de conferência/origem.

## Erros, geração de tipos e publicação

Todos os novos endpoints documentam `400/401/403/404/409` com Problem Details e `correlationId`, sem conteúdo fiscal/bancário bruto. Respostas privadas mantêm `no-store`. Tenant vem exclusivamente da sessão. Nenhuma rota emite NF, escolhe regra fiscal, paga conta ou confirma recebimento bancário.

Os tipos devem ser gerados do OpenAPI executável final, não destes exemplos com placeholders. Sem deploy, início/reinício da API/Docker, infraestrutura, push ou operação financeira real. Disponibilidade na instância do frontend depende de publicação posterior autorizada; não foi validado E2E contra a instância antiga.

## Provas e OpenAPI definitivo

**38 testes distintos em 8 suítes aprovados**, mais **21 reexecuções dirigidas** (3 da cadeia/contrato e 18 do contrato tributário final), totalizando 59 execuções bem-sucedidas de verificação. Sem falhas, erros ou ignorados; Checkstyle main/test sem erros, BUILD SUCCESSFUL e `git diff --check` limpo. O teste de migration foi atualizado de V87 para V89, sem modificar migration aplicada.

Cobertura: base antes das medições e ordem pela data de aceite (não de criação), capacidade completa, todo/parcial, desconto/bruto preservados, replay/conflito, tenant/permissões, aprovação sem reserva e versão obsoleta, concorrência e cadeia nova cobrança → NF → baixa parcial → estorno com atribuições exatas positivas/negativas. Tributos: valor/percentual, base independente da NF, quatro casas/HALF_UP, divergência/inputs mistos/JSON number, três moedas e tratamentos, preview sem efeitos, replay concorrente, retenções concorrentes limitadas pelo saldo, aprovação/revalidação, histórico cancelado/legado, FKs e imutabilidade. Regressão do fluxo líquido e tributário anterior preservada.

Export: [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado de `GET /v3/api-docs` da aplicação com PostgreSQL/Testcontainers isolado e gateway desabilitado: **OpenAPI 3.1.0, 142 paths e 243 schemas**. Sem edição manual de JSON. Contrato com gateway habilitado também validado/exportado separadamente.

SHA-256 definitivo:

```text
C36DDE3C7D7FA097B51C6541FC2D2BB80ACEF7D6A59DCA87B45701D4DC25E027
```

Evidências locais ignoradas pelo Git em `tmp/validation-service-billing-calculated-tax/`: `full-suite-evidence/test-results` e `full-suite-evidence/checkstyle` (38 testes/8 suítes); `origin-chain-evidence/test-results` (3 verificações); `build/test-results/test` e `build/reports/checkstyle` (18 verificações finais/Checkstyle); `gateway-enabled.openapi.json` (configuração alternativa).

Execução completa reproduzível com JDK 21 e Docker somente para Testcontainers:

```powershell
$env:INVOICE_TAX_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-service-billing-calculated-tax/gateway-enabled.openapi.json"
.\gradlew.bat test --tests '*MeasurementContinuousApiIntegrationTest.serviceNetBilling*' --tests '*MeasurementContinuousApiIntegrationTest.netBilling*' --tests '*InvoiceTaxReadApiIntegrationTest' --tests '*TaxCalculationTest' --tests '*TaxObligationServiceTest' --tests '*TaxObligationTest' --tests '*NetBillingDistributionTest' --tests '*OpenApiContractIntegrationTest' --tests '*FlywayMigrationIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-service-billing-calculated-tax/build
```

A V89 foi aplicada apenas em bancos temporários. Não foi aplicada ao PostgreSQL real; publicação posterior precisa do processo normal autorizado de migration/deploy, preservando dados.
