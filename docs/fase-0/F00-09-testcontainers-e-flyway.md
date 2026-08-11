# F00-09 — PostgreSQL de testes e Flyway

- Estado: concluído
- Data: 2026-08-10

## Base de integração

`AbstractPostgresIntegrationTest` centraliza a configuração dos testes que
dependem do banco. Ela ativa o perfil `test`, inicia o PostgreSQL via
Testcontainers e marca os testes com a tag `integration`.

O container usa a imagem fixa `postgres:17-alpine`, banco `canteiro_test` e
credenciais exclusivas de teste. O `@ServiceConnection` entrega a conexão ao
Spring Boot; nenhum endereço ou segredo local é necessário.

## Flyway

O perfil de teste mantém `ddl-auto=validate` e Flyway habilitado. Assim, o
schema só é criado pelas migrations. `FlywayMigrationIntegrationTest` confirma
que o container vazio recebe a versão mais recente da migration, enquanto o
teste de schema de `Company` consulta as constraints aplicadas.

## Execução

Com Java 21 e Docker disponíveis:

```powershell
.\gradlew.bat test
```

Os testes unitários não precisam do container; testes que herdam a base usam
PostgreSQL real e migrations Flyway.
