# Comparação de pagamentos entre períodos

F06-12 · FRONT-F09-08

| Consulta | Rota | Permissão |
|---|---|---|
| Empresa | `GET /api/v1/company/cash-flow/payments/comparison` | `PAYABLES.READ` |
| Obra | `GET /api/v1/company/cash-flow/works/{workId}/payments/comparison` | `PAYABLES.READ` e `WORKS.READ` |

Informe `from` e `to` em `YYYY-MM-DD`, com os dois dias inclusivos e término até hoje UTC. O intervalo anterior termina na véspera de `from` e tem a mesma quantidade de dias. Por exemplo, `from=2025-02-11&to=2025-02-20` compara com 1 a 10 de fevereiro. A empresa vem da sessão. A API retorna Problem Details com `400` para período inválido, `401` sem sessão, `403` sem permissão e `404` para obra inexistente no tenant.

O `200` retorna `workId` (nulo na empresa), `currentPeriod`, `previousPeriod`, `calculatedAt` e `totalsByCurrency`, separado em BRL, USD e EUR. Cada moeda inclui `payments`, `workPayments` e, na empresa, `companyPayments`. O total da empresa inclui ambos os escopos. A consulta por obra inclui títulos `WORK` daquela obra, inclusive sem contrato. Títulos sem fornecedor ou categoria entram normalmente nos totais.

Cada comparação contém `current` e `previous`, com `amount`, `entryCount` e `coverage`. A soma usa a data efetiva de cada baixa ou estorno e preserva o sinal da linha. A cobertura `RECORDED_MOVEMENTS` indica que há registros no intervalo. `NO_RECORDED_MOVEMENTS` indica ausência de linhas. O valor `"0.00"` com contagem zero descreve somente o que foi registrado e não comprova que o gasto real foi zero.

| `status` | Diferença e percentual |
|---|---|
| `RECORDED_MOVEMENTS` | Com registros nos dois períodos e base anterior positiva, `differenceAmount` é atual menos anterior e `percentageChange` é a diferença dividida pelo anterior, multiplicada por 100. Compara os registros disponíveis. |
| `INSUFFICIENT_RECORDS` | Um dos períodos não tem lançamentos registrados. Diferença e percentual são nulos. |
| `NON_POSITIVE_BASE` | Ambos têm registros, mas a base anterior é zero ou negativa. A diferença é calculada e o percentual é nulo. |

Valores e percentuais são strings decimais de duas casas. O percentual usa arredondamento HALF_UP. O frontend pode exibir diretamente o percentual com o símbolo apropriado e sinalizar a cobertura em cada linha. A API calcula os indicadores sobre o histórico registrado, sem afirmar que os períodos têm cobertura bancária completa.

Em 13/11/2026, **25 testes passaram** em PostgreSQL descartável e o Checkstyle passou. Foram verificados os limites dos períodos, estornos entre períodos, cobertura, base zero, escopo, moeda, permissões, isolamento e OpenAPI.

As duas rotas estão ativas em `localhost:8081` na imagem `canteiroio-api:local-cash-comparison-20261113`. O OpenAPI ativo acrescentou apenas essas rotas e seus cinco schemas. As duas rotas retornaram `401 application/problem+json` sem autenticação. A publicação preservou o schema V96 e o histórico Flyway, com migrations e seeds desativados. O container `canteiro-api-before-cash-comparison-20261113` foi preservado para retorno.

[OpenAPI exportado](../postman/canteiro-io.openapi.json) · `compareCompanyCashPayments` e `compareWorkCashPayments`.
