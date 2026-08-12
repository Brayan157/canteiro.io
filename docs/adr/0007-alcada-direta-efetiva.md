# ADR 0007 — Alçada direta efetiva para editor/aprovador

- Estado: Aceito
- Data: 2026-08-11
- Complementa: [ADR 0006](0006-aprovacao-e-auditoria.md)

## Contexto

Em empresas pequenas, uma mesma pessoa pode executar e supervisionar alterações de um
módulo. Exigir que ela crie uma pendência que não poderá aprovar torna a operação
desnecessariamente bloqueada, embora a auditoria continue necessária.

## Decisão

Para cada módulo e operação (`CREATE`, `UPDATE` ou `CANCEL`), o caso de uso calcula a
**alçada direta efetiva** antes de alterar qualquer dado oficial:

1. A permissão direta correspondente concede alçada direta efetiva.
2. A combinação da permissão de solicitação correspondente com `APPROVE` no mesmo módulo
   e operação também concede alçada direta efetiva.
3. Com alçada direta efetiva, o caso de uso aplica a alteração imediatamente e registra um
   `AuditEvent` com ator, empresa, ação, entidade, instante e valores antes/depois.
4. Sem alçada direta efetiva, a alteração cria `ChangeRequest` e não modifica o dado
   oficial até decisão de outro usuário autorizado.
5. `APPROVE` isolada não concede criação, edição ou cancelamento. `REQUEST_*` isolada não
   concede aprovação.
6. A proibição de autoaprovação permanece: um solicitante não decide sua própria
   `ChangeRequest`. A combinação definida nesta ADR não é autoaprovação, pois nenhuma
   solicitação é criada.

## Consequências

Os casos de uso não podem decidir por uma única anotação de controller. Eles devem avaliar
a operação e o módulo, resolver a alçada direta efetiva e, em todos os caminhos, gravar a
auditoria apropriada. Interfaces devem explicar essa regra sem confiar nela como barreira de
segurança: a decisão é sempre do backend.
