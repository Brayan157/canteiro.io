# ADR 0005 — Exclusão lógica e rastreabilidade

- Estado: Aceito
- Data: 2026-08-10

## Contexto

Registros de contratos, obras, medições e financeiro afetam saldo, lucro,
auditoria e documentos. Removê-los fisicamente destruiria evidências e
inviabilizaria reconciliação.

## Decisão

1. Não há exclusão física para dados de domínio, financeiros, medições,
   contratos, obras ou auditoria.
2. A remoção aparente é feita por status de negócio, cancelamento, inativação,
   `deleted_at`/`deleted_by` quando aplicável, ou estorno rastreável para efeitos
   financeiros.
3. `AuditEvent` é imutável e nunca usa exclusão lógica como mecanismo de limpeza.
   Sua retenção técnica mínima é de cinco anos.
4. Consultas normais ocultam registros logicamente excluídos/inativos quando a
   regra do módulo exigir; consultas históricas, auditorias e reconciliações os
   preservam.
5. Unicidades de registros ativos devem ser reforçadas por constraint ou índice
   parcial PostgreSQL, conforme o modelo de cada entidade.

## Consequências

Endpoints de `DELETE` não realizam `DELETE` SQL para estes domínios. Cancelar ou
estornar deve gerar auditoria e respeitar os efeitos financeiros definidos.
