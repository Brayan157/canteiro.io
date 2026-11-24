# F05-21 — Faturamento líquido positivo

Data: 16/10/2026. Decisão proporcional autorizada pelo usuário. Implementação, testes e export concluídos; não publicada na instância local.

## Regra oficial

O usuário escolhe serviço e, quando aplicável, a composição exata da medição e informa somente o líquido positivo a cobrar. Não pedir desconto novamente nem enviar `CONTRACT_DISCOUNT` no novo fluxo. O frontend não calcula rateio, capacidade, bruto consumido ou saldo.

Os preços negociados, itens aceitos e valores documentais não mudam. O desconto contratual é distribuído pelo backend apenas sobre a capacidade não comprometida de cada base/composição. Cada nova alocação preserva o líquido e congela a base bruta consumida e o desconto aplicado. Novos avanços redistribuem somente a parcela ainda livre. Linhas negativas legadas, títulos, NFs, baixas e estornos anteriores não são regravados.

“Bruto” neste contrato é a capacidade do serviço/composição **antes do desconto contratual**; descontos próprios do serviço ou de cabeçalho da medição já estão reconhecidos nessa base. Não reaplicá-los. Não se trata de alterar o preço unitário histórico.

Exemplo: dois serviços com capacidade livre `1000.00` cada e desconto livre `200.00` recebem capacidade líquida `900.00` cada. Uma cobrança de `900.00` consome a base `1000.00` e congela o desconto `100.00` daquela origem. A outra permanece com `900.00` líquidos. O backend fecha os centavos deterministicamente.

## 1. Consultar capacidade

`GET /api/v1/company/billing-control/contracts/{contractId}/net-capacity`

Autenticado; exige `BILLING.READ` e `MEASUREMENTS.READ`. Tenant exclusivamente da sessão. Parâmetros opcionais: `contractServiceId`, `measurementContractCompositionId`, `page` (padrão 0), `size` (padrão 20, entre 1 e 100). Ordenação fixa por base/composição e IDs imutáveis; filtros/paginação não mudam a distribuição global. Não há reserva ou mutação no GET.

Resposta `NetBillingCapacityResponse`:

- `contractId`, `currency`, `capacityVersion`;
- `contractNetAmount`, `activeAllocatedAmount`, `remainingNetAmount`, `remainingDiscountAmount`: strings decimais de duas casas;
- `positions`: `PageResponse<NetBillingCapacityPositionResponse>` (`items`, paginação padrão).

Cada posição identifica `contractServiceId`, `serviceName`, `measurementContractCompositionId`, `measurementId`, `measurementVersionId`, `continuousServiceId` e retorna `grossAvailableAmount`, `netAvailableAmount`, `uncommittedDiscountAmount`, `eligible` e `reason`. IDs de medição são nulos na base negociada. Selecione somente posições `eligible=true`; a capacidade global pode conter origens bloqueadas, portanto não é autorização para cobrar de qualquer origem.

Motivos: `AVAILABLE`, `NO_NET_CAPACITY`, `CONTRACT_CANCELLED`, `RECONCILIATION_REQUIRED`, `LEGACY_ORIGIN_REQUIRES_RECONCILIATION`. Contratos históricos inconsistentes exigem reconciliação e retornam `409`, nunca zeros financeiros inventados.

## 2. Criar parcela líquida

`POST /api/v1/company/billing-installments/net`

Exige alçada efetiva de criação em `BILLING` (direta, ou solicitação+aprovação); quem só solicita recebe `202`.

```json
{
  "operationId": "uuid-novo-para-esta-operacao",
  "contractId": "uuid-do-contrato",
  "capacityVersion": "sha256-retornado-pela-consulta",
  "reference": "Parcela 01",
  "description": "Serviços executados",
  "issuedOn": "2026-10-16",
  "dueOn": "2026-11-16",
  "allocations": [
    {
      "contractServiceId": "uuid-do-servico",
      "measurementContractCompositionId": "uuid-exato-da-composicao",
      "netAmount": "90.00"
    }
  ]
}
```

`financialGroupId` e `justification` são opcionais. `issuedOn` também pode ser omitido. Referência aceita até 100 caracteres; descrição/justificativa até 1.000. `dueOn`, `operationId`, `contractId`, `capacityVersion` e pelo menos uma alocação são obrigatórios. Uma mesma base/composição só pode aparecer uma vez; máximo de 100 linhas. `netAmount` é string canônica positiva com exatamente duas casas, não número JSON, notação científica, desconto ou valor negativo. O ID de composição só pode ser omitido para a base negociada sem origem de medição. A seleção humana pode usar o seletor `/measurement-origins` existente; correlacionar com a capacidade exclusivamente pelo ID exato da composição, nunca pelo título.

- `201`: aplicação direta; `BillingInstallmentApprovalResponse` contém `billingInstallment`, `receivable`, `mode`, com `changeRequestId=null`.
- `200`: replay idempotente da operação já aplicada, sem duplicar parcela/título/auditoria. A situação retornada é a atual, inclusive após cancelamento.
- `202`: `changeRequestId` e `mode=REQUEST_APPROVAL`; `billingInstallment` e `receivable` nulos. Nenhuma capacidade, valor, reserva ou saldo oficial muda.
- `400`: formato, paginação ou alocações inválidas; `401/403`: autenticação/alçada; `404`: contrato/origem fora da cadeia/tenant; `409`: capacidade desatualizada, excesso, origem indisponível ou conflito da operação. Erros são `application/problem+json`, com `correlationId`.

Reenviar o mesmo `operationId` com a mesma proposta repete o resultado/pedido pendente. O mesmo ID com outra proposta produz `409`. Após rejeição, use novo ID e capacidade atual. Aprovar revalida a capacidade sob lock do contrato; se ela mudou, a aprovação falha com `409` e a proposta permanece pendente. Não criar outra cobrança automaticamente após timeout: primeiro repetir o mesmo corpo e ID.

Em `billingInstallment.allocations[]`, `allocatedAmount` é o líquido usado por NF/título. Os campos `netBillingGrossAmount` e `netBillingDiscountAmount` mostram a base/desconto congelados; no legado são nulos. O detalhe `GET /billing-installments/{installmentId}` também os expõe. Cancelar parcela pelo fluxo existente libera a base e o desconto, sem apagar os metadados históricos; recebimentos/NFs precisam ser tratados pelas validações existentes.

## 3. Saldos e compatibilidade

- Controle contratual: `services[].activeAllocatedAmount` continua sendo o montante efetivamente alocado; `activeCapacityAmount` informa a base consumida; `availableAmount` é a capacidade antes do desconto contratual ainda livre; `netAvailableToBillAmount` é a capacidade líquida oficial. Não subtrair localmente esses campos nem repartir pelo título.
- Seletor legado `/measurement-origins`: `acceptedAmount` continua documental; `availableToBillAmount` é capacidade da origem antes do desconto contratual. Os novos campos `activeCapacityAmount` e `netAvailableToBillAmount` distinguem consumo bruto de disponibilidade líquida. Para criar no novo fluxo, obtenha também `capacityVersion` pela nova rota.
- Resumo de medição: `acceptedAmount` continua aceito documental. `acceptedAvailableToBill`, `remainingToReceive` e `financialAdjustmentAmount` reconhecem o desconto contratual oficial, incluindo sua parcela livre. Baixas parciais/estornos continuam rastreando a alocação e composição exatas. Uma baixa não libera capacidade de faturamento.
- `POST /billing-installments` legado permanece compatível; preserva linhas SERVICE e CONTRACT_DISCOUNT anteriores. Não utilizá-lo para simular o novo fluxo apenas ocultando desconto na interface.

## Entrega e publicação

Migration aditiva `V85__preserve_net_billing_capacity_basis.sql`; não modifica V1–V84 nem registros financeiros históricos. Testada exclusivamente no PostgreSQL isolado do Testcontainers. Registros legados permanecem sem metadados de base; triggers impedem exclusão física e alteração da base/origem de novas alocações. Nenhuma migration foi aplicada pelo agente ao banco real.

Snapshot entregue em F05-21: contrato gerado diretamente de `GET /v3/api-docs` da aplicação de integração, sem edição manual: `postman/canteiro-io.openapi.json`, **OpenAPI 3.1.0, 127 paths, 205 schemas**, com gateway desabilitado. O arquivo exportado evolui; o contrato mais recente está registrado em [F06-01](FRONTEND-PAYABLE-CATALOGS.md). O contrato geral também foi validado com gateway habilitado, em artefato de teste separado. SHA-256 do snapshot F05-21:

```text
3E4EECCC9A3E22D08FD6C8145FA724544F5EA1BC28E088CD161EBCAF5C103AAF
```

Provas: **135 testes em 30 suítes**, zero falhas/erros/ignorados; **12 execuções focadas adicionais em três suítes** revalidaram a versão final após reforços de limites, serialização e idempotência; Checkstyle main/test sem erros. Cobertura inclui legado, duas origens no mesmo serviço, novos avanços sem reprecificar compromissos, centavos/parciais, desconto FIXED/PERCENTAGE e 100%, NF pelo líquido, recebimento/estorno por origem, cancelamento, lastro de preço/desconto, tenant/permissões, decimal JSON estrito, limite de descrição, replay e conflito concorrente, aprovação/rejeição sem reserva e PDF/XLSX com resumo financeiro líquido.

Evidências locais ignoradas pelo Git: `tmp/validation-net-billing/regression` e `tmp/validation-net-billing/final`. O snapshot intermediário com a expectativa antiga de capacidade bruta foi preservado separadamente; a regressão final confirma base `100.00` intacta e disponibilidade líquida `80.00` após cancelamento quando o desconto vigente é `20.00` por origem.

Sem deploy, reinício solicitado da API, push, mensagem a outro chat ou alteração do frontend neste recorte. A instância antiga `localhost:8081` ainda precisa de publicação autorizada para servir essas rotas e aplicar V85. Para FRONT-F08-18, gerar tipos deste export e integrar somente o contrato descrito acima; não acrescentar cálculo financeiro local.
