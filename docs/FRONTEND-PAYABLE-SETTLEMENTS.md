# Baixas e estornos de contas a pagar

F06-07 e F06-09 · Integração FRONT-F09-04

O registro representa um pagamento já efetuado fora do sistema. Boleto, Pix e instruções de pagamento seguem como referências independentes. A baixa herda a moeda e o escopo do título individual, inclusive contas de obra sem contrato, contas gerais da empresa e títulos de imposto.

## Rotas

Base `/api/v1/company/payables/{payableId}`. O tenant vem da sessão.

| Método e caminho | Permissão efetiva | Resposta |
|---|---|---|
| `GET /balance` | `PAYABLES.READ` | `200 PayableBalanceResponse` |
| `GET /settlements?page=0&size=20` | `PAYABLES.READ` | `200 PageResponsePayableSettlementResponse`, tamanho 1–100, data efetiva, data de registro e ID em ordem decrescente |
| `POST /settlements` | `PAYABLES.READ` e alçada CREATE | `201` aplicado, `200` replay aplicado ou `202` pendente |
| `POST /settlements/{settlementId}/reversals` | `PAYABLES.READ` e alçada CANCEL | `201` aplicado, `200` replay aplicado ou `202` pendente |

CREATE direto exige `CREATE_DIRECT` ou `REQUEST_CREATE` com `APPROVE`. CANCEL direto exige `CANCEL_DIRECT` ou `REQUEST_CANCEL` com `APPROVE`. Quem possui apenas a permissão de solicitação recebe `202`. O fluxo comum de aprovação exige `PAYABLES.APPROVE` de outro usuário. A aprovação verifica o saldo e o estado atuais sob bloqueio do título. O `202` contém apenas `changeRequestId` e `authorizationMode`, sem baixar valor, reservar saldo ou inserir linha no histórico.

## Formulários e leitura

- Baixa recebe `operationId` UUID, `amount` decimal positivo em texto com duas casas, `paidOn` data de negócio, `paymentMethod` e `justification` opcional de até 1.000 caracteres. O meio efetivo aceita `PIX`, `BANK_TRANSFER`, `BOLETO`, `CARD`, `CASH`, `CHECK` e `OTHER`.
- Estorno recebe novo `operationId`, `reversedOn` e `justification` obrigatória de até 1.000 caracteres. A API usa o valor integral e o meio da baixa original. A data do estorno fica entre a data da baixa e o dia atual UTC. O histórico preserva a linha original e acrescenta uma linha negativa com `reversalOfId`. Cada pagamento pode ser estornado uma vez.
- `201` e `200` devolvem `settlement`, `balance` e `authorizationMode`. Reenvie o mesmo corpo e `operationId` para conferir um envio inconclusivo. O replay aplicado devolve a linha original e o saldo atual. Uma chave usada com conteúdo diferente recebe `409`.
- A lista e o detalhe de títulos incluem o mesmo `balance` com `originalAmount`, `paidAmount` líquido de estornos, `outstandingAmount`, `overdueAmount`, `status`, `currency`, `asOf` UTC e `available`. Cada valor oficial é decimal em texto. Quando o histórico não sustenta o status, `available=false` e as três quantias derivadas ficam nulas. Vencido é apenas o saldo em aberto de título com vencimento anterior ao dia UTC. Não deduzir saldo pelo status ou pela soma da página do histórico.

## Erros e ativação

`400` para dados inválidos, inclusive data futura ou formato monetário. `401` sem sessão, `403` sem permissão e `404` para título ou baixa fora da cadeia da empresa. Em `409 BUSINESS_RULE_VIOLATION`, `reason` pode ser `PAYABLE_SETTLEMENT_EXCEEDS_BALANCE`, `PAYABLE_SETTLEMENT_ALREADY_REVERSED`, `PAYABLE_SETTLEMENT_UNAVAILABLE`, `PAYABLE_SETTLEMENT_OPERATION_CONFLICT` ou `PAYABLE_SETTLEMENT_OPERATION_DECIDED`. Os erros usam Problem Details com `correlationId`.

A V95 original já consta como aplicada no PostgreSQL local, com checksum `-1626293171`. Ela cria o histórico imutável e a chave idempotente por empresa. A V96 adiciona a justificativa de estorno e adia a validação da chave estrangeira de aprovação até o fim da transação. Ela preserva os lançamentos anteriores, inclusive estornos sem justificativa histórica. A V96 já constava como aplicada, com checksum `-374699626`, na conferência de 13/11/2026.

## Verificação

Em 12/11/2026, 83 testes passaram em PostgreSQL descartável, com Checkstyle de código principal e testes. A cobertura inclui saldo parcial/total e vencido, isolamento, estorno, reabertura, histórico, idempotência, concorrência, propostas sem efeito, revalidação na aprovação, compatibilidade tributária, Flyway e a resposta de `/v3/api-docs`. O [OpenAPI exportado](../postman/canteiro-io.openapi.json) foi gerado pelo teste contratual. Na conferência posterior, o OpenAPI servido em `localhost:8081` ainda não expunha as rotas de saldo, baixa e estorno.

## Ativação local — 13/11/2026

- A validação da V96 em PostgreSQL descartável passou em 25 testes, incluindo atualização da V95 com preservação do histórico, contrato OpenAPI e fluxos de baixa e estorno. Checkstyle sem erros.
- A V96 já estava aplicada ao retomar a ativação. Antes da troca da API, foi feito um backup privado do estado encontrado, verificado com leitura integral pelo `pg_restore` e SHA-256. A troca não executou migrations, seeds nem lançamentos financeiros.
- A imagem `canteiroio-api:local-payable-settlements-20261113` está ativa em `localhost:8081`. O JAR tem SHA-256 `bfedb34dc57fa08a6c2d0bc7a78892b5ec7057f045cd0cb93fe879b94312754d`. O container anterior, `canteiro-api-before-payable-settlements-20261113`, foi preservado para retorno. O histórico Flyway permaneceu igual durante a troca.
- `GET /v3/api-docs` respondeu `200`. As quatro operações novas e seus schemas correspondem ao export. Todas responderam `401 application/problem+json` com `correlationId` sem sessão. Os fluxos autenticados foram validados no ambiente descartável.
