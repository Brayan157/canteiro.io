# ADR 0002 — Identificadores UUID

- Estado: Aceito
- Data: 2026-08-10

## Contexto

A API será multiempresa e exposta a clientes externos. Identificadores
sequenciais facilitariam enumeração e dificultariam geração segura fora do
banco.

## Decisão

1. Entidades de domínio persistentes usam UUID como identificador primário.
2. As colunas PostgreSQL usam o tipo nativo `uuid`; Java usa `java.util.UUID`.
3. UUIDs são gerados pela aplicação antes da persistência, salvo decisão futura
   documentada para uma integração específica.
4. APIs recebem e retornam UUIDs; não expõem IDs numéricos alternativos.
5. UUID não é controle de acesso: toda leitura ou mutação continua validando
   tenant, autenticação e permissão.

## Consequências

Migrations, DTOs, relações e testes devem usar UUID. Não devem ser introduzidos
IDs incrementais como identificadores públicos de domínio.
