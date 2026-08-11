# F00-08 — Constraints e timestamps de Company

- Estado: concluído
- Data: 2026-08-10

## Migration Flyway

A migration `V2__align_company_constraints_and_utc_timestamps.sql` preserva os
dados da V1 e interpreta os timestamps já existentes como UTC. Ela:

1. preenche timestamps nulos com o instante atual;
2. converte `created_at` e `updated_at` para `TIMESTAMP WITH TIME ZONE`;
3. torna os dois campos obrigatórios e mantém `CURRENT_TIMESTAMP` como padrão;
4. nomeia as constraints únicas de documento e e-mail como
   `uk_company_document` e `uk_company_email`.

## Código

`BaseJpaEntity`, domínio e resposta de `Company` agora usam `Instant`; a
entidade JPA declara comprimento, obrigatoriedade e nomes de constraints de
forma alinhada ao schema Flyway.

## Verificação

`CompanySchemaIntegrationTest` consulta o PostgreSQL do Testcontainers para
confirmar o tipo UTC, obrigatoriedade dos timestamps e as duas constraints
únicas. A execução requer Java 21 e Docker disponíveis.
