# Controle documental de faturamento por obra

O controle por obra consolida a posição oficial dos contratos vinculados à obra da empresa autenticada. Cobranças, notas fiscais, serviços e saldos preservam a origem contratual.

| Método | Rota | Permissões |
| --- | --- | --- |
| GET | `/api/v1/company/billing-control/works/{workId}` | `WORKS.READ`, `BILLING.READ` e `INVOICES.READ` |
| GET | `/api/v1/company/billing-control/works/{workId}/report/pdf` | As três permissões acima, mais `BILLING.EXPORT` e `INVOICES.EXPORT` |

## Posição JSON

A resposta contém `workId`, `workName`, `workReference`, `totalsByCurrency` e `contracts`. O campo `contracts` usa o envelope paginado padrão com `items`, `page`, `size`, `totalItems` e `totalPages`. A ordenação é fixa por UUID de contrato crescente. Envie `page` a partir de 0 e `size` de 1 a 100. Os valores padrão são 0 e 20.

Cada total de moeda informa `currency`, `contractCount`, `contractNetAmount`, `invoicedAmount` e `documentaryBalance`. Cada contrato informa `contractId`, `contractReference`, `contractIdentifier`, `contractName`, `contractStatus`, `currency`, os mesmos três valores monetários e `detailsUrl` para o controle contratual. Use `contractReference`, que vem de `contract.reference`, na coluna **Número do contrato**. Quando o campo for nulo, exiba **Não informado**. `contractIdentifier` mantém seu significado anterior.

Os três valores monetários são strings decimais de duas casas. O contratado líquido inclui os descontos e ajustes oficiais da base contratual. O faturado soma o total de cada NF registrada uma vez. O saldo documental é a diferença calculada pelo backend. Uma NF vinculada a várias cobranças entra uma única vez no total. NFs canceladas ficam no histórico do relatório contratual e fora dos totais. O frontend exibe os valores da API por moeda, com BRL, USD e EUR quando houver contratos dessas moedas.

Os totais abrangem toda a obra em cada resposta, inclusive numa página de contratos vazia. A consulta lê os contratos em lotes por cursor no PostgreSQL e mantém somente a página requisitada e os totais em memória. A obra sem contratos devolve `200` com `totalsByCurrency: []` e `contracts.items: []`.

## PDF oficial

Use o fluxo de download autenticado existente, respeitando o `Content-Disposition` inline e o nome de arquivo. O PDF tem `Content-Type: application/pdf` e `Cache-Control: no-store`.

1. Título e referência oficial da obra.
2. Uma linha por contrato, com nome, referência oficial na coluna **Número do contrato**, valor do contrato, valor faturado e saldo.
3. Resumo total por moeda com a soma do valor dos contratos, do faturado e do saldo.

As três colunas monetárias usam formatação pt-BR e as cifras `R$`, `US$` e `€` nas linhas de contrato e no resumo. O sinal negativo aparece antes da cifra quando houver saldo negativo. A resposta JSON mantém os montantes em strings decimais, separados da moeda.

Os detalhes de serviços, notas e cobranças permanecem no relatório por contrato.

O PDF usa consultas limitadas por lote e cache em disco para os fluxos de página. Inclui a obra inteira sem paginação pelo cliente ou limite fixo de contratos. PDFBox finaliza o documento antes de gravá-lo no fluxo HTTP. A exportação concluída gera auditoria.

## Erros e disponibilidade

- `400` para UUID ou paginação inválidos, `401` sem autenticação e `403` sem permissão.
- `404` para obra inexistente ou de outro tenant.
- `409` com `BILLING_REPORT_RECONCILIATION_REQUIRED` quando a origem de uma NF registrada exige conciliação, como no relatório contratual.

Foram aprovados 11 testes focados em 08/11/2026, incluindo a extração do texto do PDF, a regressão do relatório contratual e o OpenAPI executável. Os testes de integração usaram PostgreSQL descartável limitado à V93. O Checkstyle passou.

O [OpenAPI exportado](../postman/canteiro-io.openapi.json) é 3.1.0 com 159 paths e 287 schemas. O SHA-256 do ajuste de referência e cifras é `61691402DB9C2BA3920EA6F3601F1ABED652A5C64282819893B393D8E270CB5A`. A comparação confirmou a adição de `contractReference` e a atualização da descrição do PDF por obra. Os demais campos, schemas e operações foram preservados.

O ajuste passou em dez testes. A extração do PDF verificou a distinção entre referência e identificador, a ausência de referência, as três cifras nas duas seções e o sinal do saldo negativo. Também passaram os testes do PDF contratual e do OpenAPI executável.

## Publicação local

Em 08/11/2026, o recorte F05-43 foi ativado em `localhost:8081` na imagem `canteiroio-api:local-work-billing-control-20261108`, com JAR SHA-256 `19D25EAE6D5EF0BC337D09F273694036969405707FA6A4F1F4EC7AC24397A993`. Um teste focal passou com as classes exatas da composição isolada, incluindo a posição por obra, a compatibilidade contratual e o texto do PDF.

O OpenAPI vivo respondeu `200`, com 154 paths. As duas operações novas conferem com o export revisado. A comparação preservou as operações e schemas anteriores, incluindo o PDF contratual, a prévia XML e a cobertura de NF. As cinco rotas retornaram `401` com Problem Details e `correlationId` sem credenciais.

O histórico Flyway permaneceu idêntico no esquema V93. O container anterior foi preservado como `canteiro-api-before-work-billing-control-20261108`.

O ajuste de referência e cifras foi publicado no mesmo dia, na imagem `canteiroio-api:local-work-billing-reference-20261108`, JAR SHA-256 `E54879A4414F99A5428A64C484D61CD3B7184146A85B38AC49FED79B42A0D60B`. O OpenAPI de `localhost:8081` confirmou `contractReference` e manteve os 154 paths. As duas rotas por obra, o PDF contratual, a prévia XML e a cobertura de NF responderam `401` com Problem Details sem credenciais. O histórico Flyway permaneceu na V93 e o container imediatamente anterior foi preservado como `canteiro-api-before-work-billing-reference-20261108`.
