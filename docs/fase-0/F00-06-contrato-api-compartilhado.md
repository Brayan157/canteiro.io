# F00-06 — Contrato de API compartilhado

- Estado: concluído
- Data: 2026-08-10

## Erros

Erros HTTP usam RFC 9457 `ProblemDetail` com `type`, `title`, `status`,
`detail`, `code` e `correlationId`. Os códigos estáveis são definidos em
`shared.api.error.ErrorCode`; clientes devem reagir ao campo `code`, não à
mensagem humana.

Validações incluem `violations`, uma lista de `{ field, message }`. Exceções
inesperadas não expõem detalhes internos.

## Correlação

Toda resposta contém o cabeçalho `X-Correlation-Id`. O cliente pode enviar um
valor alfanumérico de até 64 caracteres; caso contrário, a API gera um UUID.
O mesmo valor é inserido no MDC e nos `ProblemDetail` para correlacionar logs e
erros.

## Paginação, ordenação e filtros

- Paginação: `page` inicia em zero; `size` tem padrão 20 e máximo 100.
- Ordenação: repita `sort=campo,asc` ou `sort=campo,desc`.
- Filtros: repita `filter=campo:OPERADOR:valor`, com `EQ`, `CONTAINS`, `GTE`,
  `LTE` ou `IN`.
- Todo endpoint define explicitamente os campos permitidos para ordenação e
  filtro. Campos não permitidos retornam `INVALID_PAGINATION` ou
  `INVALID_FILTER`.
- Listagens retornam `PageResponse` com itens, página, tamanho, total de itens
  e total de páginas.

Essa base não cria filtros JPA genéricos nem endpoints novos. Cada módulo deve
converter os critérios permitidos para sua consulta sem permitir nomes de campo
livres no banco.
