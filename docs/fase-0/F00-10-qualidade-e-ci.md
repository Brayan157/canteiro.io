# F00-10 — Qualidade, cobertura e CI

- Estado: concluído
- Data: 2026-08-10

## Quality gates locais

`./gradlew check` agora executa:

1. Checkstyle com regras iniciais contra imports curinga, tipos SQL legados e
   escrita direta em `System.out`/`System.err`;
2. testes unitários e de integração;
3. JaCoCo com cobertura mínima inicial de **15% por instruções**.

O mínimo é deliberadamente de transição para a base já existente. Ele deve ser
elevado nas próximas fases junto com a cobertura de regras de negócio, tenant e
financeiro; não deve ser reduzido sem autorização explícita.

Relatórios são gerados em:

- `build/reports/checkstyle/`;
- `build/reports/jacoco/test/html/`;
- `build/reports/jacoco/test/jacocoTestReport.xml`.

## GitHub Actions

`.github/workflows/ci.yml` executa em push para `main` e branches `feature/**`,
além de pull requests para `main`. O workflow instala Temurin 21, usa cache de
dependências Gradle e roda `check jacocoTestReport`. Os testes de integração
sobem PostgreSQL real com Testcontainers no runner Linux.
