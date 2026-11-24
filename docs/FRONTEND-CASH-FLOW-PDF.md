# PDF do relatório financeiro

| Escopo | Rota |
|---|---|
| Empresa | `GET /api/v1/company/cash-flow/report/pdf` |
| Obra | `GET /api/v1/company/cash-flow/works/{workId}/report/pdf` |

Envie `from` e `to` no formato `YYYY-MM-DD`, com limites inclusivos até hoje UTC. `horizonDays` aceita de 1 a 365 e o padrão é 30. `currency` é obrigatória e aceita `BRL`, `USD` ou `EUR`. O PDF inclui uma moeda por arquivo.

O download exige `REPORTING.EXPORT` e pelo menos uma fonte com leitura e exportação efetivas. Para recebimentos, são `RECEIVABLES.READ` e `RECEIVABLES.EXPORT`. Para pagamentos, são `PAYABLES.READ` e `PAYABLES.EXPORT`. A rota de obra exige também `WORKS.READ`. Uma fonte sem alçada aparece como indisponível. A variação líquida exige as duas fontes e a comparação exige acesso à fonte de pagamentos. O tenant vem da sessão.

O arquivo apresenta caixa realizado no período, posição dos vencidos e a vencer no corte UTC e comparação de pagamentos com o intervalo anterior de mesma duração. Os gráficos usam os mesmos agregados e cobertura das consultas JSON. As barras preservam o sinal dos movimentos. A pizza mostra a distribuição dos saldos apurados a pagar entre vencidos e a vencer quando ambos os montantes estão disponíveis e a soma é positiva.

O `200` devolve `application/pdf`, `Cache-Control: no-store` e `Content-Disposition: attachment`, com nome `relatorio-financeiro-empresa-BRL.pdf` ou `relatorio-financeiro-obra-BRL.pdf`, conforme o escopo e a moeda. Use o nome do header ao salvar o arquivo. O `400` cobre filtros inválidos, o `401` sessão ausente, o `403` falta de alçada e o `404` obra ausente no tenant. Os erros anteriores ao streaming usam Problem Details.

O backend audita a exportação com formato e filtros. O PDF base usa [caixa realizado](FRONTEND-REALIZED-CASH.md), [vencimentos](FRONTEND-CASH-DUE.md) e [comparação geral](FRONTEND-CASH-PAYMENTS-COMPARISON.md). A abertura por categoria segue seu [contrato próprio](FRONTEND-CASH-PAYMENTS-BY-CATEGORY.md).

As duas rotas estão ativas em `localhost:8081` desde 13/11/2026. A comparação do OpenAPI ativo com o anterior confirmou apenas a adição das rotas de PDF. O contrato executável e os testes de integração com PostgreSQL descartável passaram, com 28 testes no total. A publicação preservou o schema V96 e o histórico Flyway, com migrations e seeds desativados. [OpenAPI exportado](../postman/canteiro-io.openapi.json)

O layout revisado foi publicado em 13/11/2026 na imagem `canteiroio-api:local-cash-flow-editorial-20261113`. A primeira página reúne caixa realizado e vencimentos. A segunda traz a comparação quando pagamentos estão autorizados. O cabeçalho identifica empresa, obra quando aplicável, período, moeda e corte UTC. Os valores são alinhados em colunas, as barras usam uma escala comum em cada gráfico e a pizza indica valor e participação. A amostra sintética de duas páginas foi renderizada e conferida. O OpenAPI ativo permaneceu idêntico, assim como o histórico Flyway V96.
