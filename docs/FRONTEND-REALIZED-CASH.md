# Caixa realizado da empresa e por obra

Primeiro recorte de F06-12, com recebimentos da F06-10. Integração FRONT-F09-08.

| Consulta | Rota | Permissão |
|---|---|---|
| Empresa | `GET /api/v1/company/cash-flow/realized?from=2025-02-01&to=2025-02-28` | `RECEIVABLES.READ` ou `PAYABLES.READ` |
| Obra | `GET /api/v1/company/cash-flow/works/{workId}/realized?from=2025-02-01&to=2025-02-28` | Uma das permissões financeiras acima e `WORKS.READ` |
| Obra, acumulado | `GET /api/v1/company/cash-flow/works/{workId}/realized/accumulated` | Uma das permissões financeiras acima e `WORKS.READ` |

Nas consultas por período, envie as duas datas em `YYYY-MM-DD`, inclusivas e limitadas ao dia atual UTC. Para um mês em andamento, use o dia atual UTC como fim. A consulta acumulada dispensa datas. O backend define `from` pela primeira movimentação efetiva das fontes autorizadas, entre todas as moedas, e `to` pelo dia atual UTC. Sem movimentos autorizados, `from` é nulo. Movimentos registrados antes do cadastro da obra ou após sua conclusão entram pelas respectivas datas efetivas. O tenant vem da sessão. Respostas usam `400` para período inválido, `401` sem sessão, `403` sem acesso e `404` para obra inexistente ou fora da empresa.

O `200` retorna `workId` (nulo na Empresa), `from`, `to`, `calculatedAt` em UTC e `totalsByCurrency` com uma linha por BRL, USD e EUR. Cada linha contém:

- `receipts` — baixas de recebíveis confirmadas, líquidas de estornos pela data efetiva de cada linha (`receivedOn`). O valor vem dos contratos, inclusive quando o recebível é histórico. Retenções, créditos, NFs e propostas pendentes ficam fora.
- `payments`, `workPayments` e `companyPayments` — pagamentos registrados líquidos de estornos pela data efetiva (`effectiveOn`). Na Empresa, `payments` inclui os dois escopos. Na obra, inclui apenas `WORK` daquela obra, com ou sem contrato, e `companyPayments` é omitido.
- `netMovement` — entradas menos saídas, com os respectivos sinais oficiais. Nulo quando falta permissão para um dos componentes. Um estorno em outro período pode produzir um total negativo.
- `cashResultPercentage` — no caixa por obra, variação líquida dividida pelas entradas líquidas registradas e multiplicada por 100, na mesma moeda e no mesmo período. String decimal de duas casas, com arredondamento HALF_UP. Retorna nulo na visão da empresa, sem permissão para uma das fontes ou com entradas líquidas iguais ou inferiores a zero. É um indicador de caixa registrado. Para comparar obras, consulte as duas com as mesmas datas e selecione a mesma moeda.

Cada componente tem `coverage`, `amount` e `entryCount`. `RECORDED_MOVEMENTS` cobre apenas os movimentos oficiais registrados. `0.00` com contagem zero indica ausência de lançamentos registrados naquele filtro e não atesta cobertura histórica ou conciliação bancária. `NO_PERMISSION` deixa valor e contagem nulos. A API só consulta as fontes autorizadas. Quantias são strings decimais com duas casas, sem conversão entre moedas.

O indicador representa a variação líquida do período. Próximos vencimentos e comparação de gastos ficam para os próximos recortes da F06-12.

O [OpenAPI exportado](../postman/canteiro-io.openapi.json) contém as operações `getCompanyRealizedCash` e `getWorkRealizedCash`.

Em 13/11/2026, a validação em PostgreSQL descartável passou com 23 testes, incluindo as duas consultas, isolamento, permissões, datas, moedas, estornos em outro período e contrato OpenAPI. Checkstyle passou. O export foi regenerado pelo teste contratual e preserva as operações anteriores. O recorte reutiliza o schema existente.

## Ativação local — 13/11/2026

- A imagem `canteiroio-api:local-realized-cash-20261113` está ativa em `localhost:8081`. O JAR tem SHA-256 `6a9ba2a8ed25cf352869d68519f1e8794348adde0049619b2cd458b81b9e35c6`. O container anterior `canteiro-api-before-realized-cash-20261113` foi preservado para retorno.
- `GET /v3/api-docs` respondeu `200`. Os dois GETs e seus schemas correspondem ao export. Ambas as rotas responderam `401 application/problem+json` com `correlationId` sem sessão. As consultas autenticadas foram verificadas nos testes isolados.
- A primeira troca retornou automaticamente à versão anterior porque o verificador esperava uma tag global. A segunda ativou a mesma imagem após corrigir o verificador. Configuração da API, histórico Flyway V96 e dados financeiros foram preservados durante a ativação.
