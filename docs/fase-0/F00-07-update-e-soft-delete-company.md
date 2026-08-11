# F00-07 — Update e exclusão lógica de Company

- Estado: concluído
- Data: 2026-08-10

## Correção

O adaptador JPA de `Company` diferencia criação de atualização. Quando o
domínio possui UUID, ele carrega a entidade existente, aplica os campos
alterados e persiste a mesma linha. Isso preserva o ID e impede que um update
gere uma nova empresa.

A remoção permanece lógica: `deactivate` altera somente `active` para `false`.
O registro continua recuperável para histórico e auditoria; nenhum `DELETE` SQL
é executado.

## Teste de integração

`CompanyApplicationServiceIntegrationTest` usa PostgreSQL real via
Testcontainers e verifica que:

1. a atualização mantém o UUID e não aumenta a quantidade de empresas;
2. o campo alterado é recuperado do banco;
3. a desativação mantém o registro e altera `active` para `false`.

A execução local requer Java 21, Docker e as dependências Gradle disponíveis.
