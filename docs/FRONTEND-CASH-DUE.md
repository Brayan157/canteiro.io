# Caixa previsto por vencimento

F06-12 · FRONT-F09-08

| Consulta | Rota |
|---|---|
| Empresa | `GET /api/v1/company/cash-flow/due?horizonDays=30` |
| Obra | `GET /api/v1/company/cash-flow/works/{workId}/due?horizonDays=30` |

`horizonDays` é opcional, de 1 a 365, com padrão de 30 dias corridos. O corte `asOf` é sempre o dia UTC atual do servidor. `dueThrough = asOf + horizonDays - 1`. O grupo `overdue` inclui vencimentos anteriores ao corte, e `upcoming` inclui hoje e o último dia do horizonte. A consulta usa a posição oficial atual. Ela não oferece consulta histórica por data de corte.

As duas rotas exigem sessão de empresa e pelo menos uma das permissões `RECEIVABLES.READ` e `PAYABLES.READ`. O detalhe exige também `WORKS.READ`. Cada permissão financeira libera seu próprio componente. O tenant vem da sessão. Horizonte inválido retorna `400`, falta de sessão `401`, falta de permissão `403` e obra ausente no tenant `404`. Erros usam Problem Details.

O `200` retorna `workId` (nulo na empresa), `asOf`, `dueThrough`, `horizonDays`, `calculatedAt` e `totalsByCurrency` para BRL, USD e EUR. Cada moeda inclui `receivables`, `payables`, `workPayables` e, na empresa, `companyPayables`. A obra recebe somente os recebíveis de seus contratos e os gastos `WORK` vinculados, inclusive sem contrato. Os valores de cada moeda são independentes.

O saldo residual usa baixas e estornos oficiais. No recebível, também considera retenções registradas e créditos efetivos. Títulos cancelados, substituídos por renegociação e rascunhos ficam fora. O saldo de um título sucessor entra somente pelo próprio vencimento e posição oficial. Valores já quitados e propostas pendentes não entram na projeção.

Cada grupo `overdue` ou `upcoming` traz `coverage`, `amount` em texto decimal de duas casas, `titleCount` e `unavailableTitleCount`.

| Cobertura | Interpretação |
|---|---|
| `OFFICIAL_RESIDUALS` | Soma dos saldos positivos oficiais. `"0.00"` indica nenhum saldo residual no recorte. |
| `PARTIAL` | Soma somente dos saldos comprováveis, com contagem dos títulos indisponíveis. |
| `UNAVAILABLE` | Há títulos com posição inconsistente e nenhum saldo positivo comprovado. O valor é nulo. |
| `NO_PERMISSION` | Valor e contagens são nulos. |

Esses valores descrevem compromissos registrados por vencimento. A comparação entre períodos usa pagamentos pela data efetiva, em consulta própria. [Contrato da comparação](FRONTEND-CASH-PAYMENTS-COMPARISON.md).

[OpenAPI exportado](../postman/canteiro-io.openapi.json) · `getCompanyCashDue` e `getWorkCashDue`.

Em 13/11/2026, 24 testes passaram em PostgreSQL descartável e Checkstyle passou. A suíte cobriu saldo residual, corte, horizonte, moeda, escopo, autorização, isolamento e cobertura parcial. O recorte reutiliza o schema existente.

## Ativação local — 13/11/2026

- Imagem `canteiroio-api:local-cash-due-20261113` ativa em `localhost:8081`. JAR SHA-256 `69d9a8e852bbb1b95b185c6870c88c04e27d964db14b28f87379a3687cdafd0e`. Container anterior preservado para retorno em `canteiro-api-before-cash-due-20261113`.
- `GET /v3/api-docs` respondeu `200`. As duas rotas e seus schemas correspondem ao export, com os contratos anteriores preservados. As duas consultas retornaram `401 application/problem+json` com `correlationId` sem sessão. Os casos autenticados foram verificados em PostgreSQL descartável.
- Configuração e histórico Flyway V96 preservados. Nenhum lançamento financeiro real foi executado.
