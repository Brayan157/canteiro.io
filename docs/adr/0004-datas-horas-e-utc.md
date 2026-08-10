# ADR 0004 — Datas, horas e UTC

- Estado: Aceito
- Data: 2026-08-10

## Contexto

Vencimentos, emissão de NF e execução de obra são datas de negócio. Auditoria,
segurança e integrações precisam de uma linha do tempo sem ambiguidade de fuso.

## Decisão

1. Datas de negócio sem horário — como emissão, vencimento, início e fim
   contratuais — usam `LocalDate` e PostgreSQL `date`.
2. Instantes técnicos — criação, atualização, login, auditoria, webhooks e
   expiração — usam `Instant` e PostgreSQL `timestamp with time zone` (`timestamptz`).
3. A aplicação grava e compara instantes em UTC. O fuso de exibição é uma
   responsabilidade da borda da aplicação/cliente, não do domínio financeiro.
4. Casos de uso dependem de `Clock` injetável; não chamam `Instant.now()` ou
   `LocalDate.now()` diretamente em regras de negócio.

## Consequências

Testes podem controlar o tempo de forma determinística. Não devem ser usados
`LocalDateTime` ou timestamp sem fuso para representar eventos auditáveis.
