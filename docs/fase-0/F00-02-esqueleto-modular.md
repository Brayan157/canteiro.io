# F00-02 — Esqueleto modular de pacotes

- Estado: concluído
- Data: 2026-08-10

## Decisão aplicada

O código novo seguirá um monólito modular orientado a funcionalidade. Cada
módulo de negócio possui as camadas `api`, `application`, `domain` e
`infrastructure`. Os diretórios vazios possuem `.gitkeep` apenas para tornar a
estrutura visível e versionável; eles não representam comportamento.

## Compatibilidade com o esqueleto existente

Os pacotes legados `controller`, `dto`, `entity`, `repository`, `service`,
`config`, `exception` e `security` foram deliberadamente preservados. Nenhum
arquivo foi movido nesta tarefa. A migração de `Company` ocorrerá de forma
incremental nas tarefas próprias da Fase 0, sem quebrar a API existente.

## Regras para novos módulos

1. Código novo não deve criar pacotes genéricos na raiz.
2. Controllers pertencem à camada `api`; casos de uso transacionais à
   `application`; entidades, regras e portas a `domain`; JPA e adaptadores a
   `infrastructure`.
3. O módulo `shared` reúne somente preocupações realmente compartilhadas.
4. A estrutura não substitui as regras de tenancy, autorização, auditoria ou
   persistência registradas no `AGENTS.md` e nos ADRs.
