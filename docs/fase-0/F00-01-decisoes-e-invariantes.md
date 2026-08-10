# F00-01 — Decisões e invariantes impactados

- Estado: concluído
- Data: 2026-08-10
- Fontes revisadas: [AGENTS.md](../../AGENTS.md) e
  [SDD MVP](../SDD-MVP-Gestao-Financeira.md)

## Resultado da revisão

Esta tarefa não altera nenhuma decisão de produto, schema ou código. Ela
formaliza os limites que todas as tarefas seguintes da Fase 0 devem obedecer.
As seis decisões transversais estão registradas nos
[ADRs](../adr/README.md) e permanecem **aceitas**.

## Invariantes que direcionam a Fase 0

| Tema | Regra obrigatória | Efeito nas próximas tarefas |
| --- | --- | --- |
| Arquitetura | API REST backend em monólito modular, Java 21, Spring Boot, PostgreSQL, Flyway, Docker e Spring Security. | F00-02 organiza pacotes sem trocar stack ou criar frontend. |
| Organização | Novos módulos usam `api`, `application`, `domain` e `infrastructure`; entidades JPA não são expostas pela API. | O esqueleto legado de `Company` não é padrão para código novo e será corrigido gradualmente. |
| Identificadores | Entidades persistentes usam UUID; dinheiro usa `BigDecimal`; datas de negócio usam `LocalDate`; instantes técnicos usam `Instant`/UTC. | F00-07 e F00-08 preservam ID no update e alinham entidade/migration sem adotar tipos numéricos ou `double`. |
| Migrações | Flyway é a única fonte de schema; migration já aplicada nunca é editada, renomeada ou apagada. | Qualquer correção de `Company` cria nova migration compatível; `ddl-auto` não cria schema. |
| Exclusão e histórico | Domínios financeiros e operacionais não sofrem exclusão física; usam status, inativação, cancelamento ou estorno rastreável. | F00-07 implementa exclusão lógica de `Company`, sem `DELETE` SQL. |
| Tenancy | O tenant é a `Company`; `company_id` não é aceito livremente para usuário de empresa e todo acesso é filtrado no backend. | Fase 0 não cria atalho que contorne o futuro `TenantContext`; módulos persistentes terão testes negativos entre empresas na Fase 1. |
| Aprovação e auditoria | Ação direta é auditada; ação sem alçada cria proposta pendente; solicitante não se autoaprova. | Fase 0 não simula aprovação em controllers nem grava auditoria incompleta como solução temporária. |
| Domínio financeiro | Hierarquia `Company → FinalCustomer → Work → Contract → ContractService`; faturamento/saldo pertencem ao contrato e resultado/gastos à obra. | A Fase 0 não cria CRUD financeiro nem altera essa hierarquia. |

## Decisões de implementação para a Fase 0

1. F00-02 criará somente os pacotes modulares vazios ou de suporte; não moverá
   comportamento legado ainda.
2. F00-03, F00-04 e F00-05 não devem expor segredos em arquivos versionados,
   logs ou OpenAPI.
3. F00-06 estabelece convenções HTTP compartilhadas, sem colocar autorização,
   aprovação ou regra financeira em controllers.
4. F00-07 e F00-08 são a primeira correção do legado: devem preservar UUID,
   criar auditoria quando ela estiver disponível e usar migration nova para
   qualquer ajuste persistente.
5. F00-09 usa PostgreSQL real em Testcontainers, com migrations Flyway, para
   evitar que testes em memória escondam diferenças de banco.

## Limites que exigem autorização explícita

Não é permitido nesta fase alterar a hierarquia de domínio, o dono do
faturamento/saldo, fórmulas ou descontos, escopo de gastos, isolamento entre
empresas, retenção de auditoria, poderes de suporte, stack, estratégia Flyway
ou realizar migration destrutiva. Esses limites são definidos na seção 14 do
`AGENTS.md`.
