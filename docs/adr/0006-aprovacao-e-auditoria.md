# ADR 0006 — Aprovação e auditoria

- Estado: Aceito
- Data: 2026-08-10

## Contexto

Empresas definem alçadas por módulo: alguns usuários alteram diretamente;
outros apenas propõem alterações que precisam de aprovação. Toda ação precisa
ser rastreável.

## Decisão

1. Permissões são granulares por módulo e ação: consulta, alteração direta,
   solicitação, aprovação, rejeição, exportação, envio e gestão.
2. Uma ação com alçada direta é aplicada imediatamente e sempre cria
   `AuditEvent` com ator, empresa, ação, entidade, instante e antes/depois
   aplicáveis.
3. Uma ação sem alçada direta cria `ChangeRequest`; ela contém operação,
   entidade, versão, proposta, valores antes/depois, solicitante e justificativa
   quando necessária. Não altera o dado oficial.
4. Solicitações pendentes não entram em consultas ou relatórios operacionais,
   salvo filtro explícito de pendências.
5. O solicitante nunca aprova a própria solicitação. Um aprovador autorizado no
   módulo aprova ou rejeita; a rejeição exige motivo.
6. Uma aprovação qualificada é suficiente. A aplicação da proposta, a mudança
   de estado da solicitação e os eventos de auditoria ocorrem atomicamente.
7. Status de aprovação e status de negócio são distintos.
8. Ações de suporte também são auditadas. Suporte não aprova solicitações de
   clientes nem altera planos, dados estruturais da empresa ou papéis.

## Consequências

Controllers não podem decidir aprovação sozinhos. Casos de uso centralizam
permissão, transação, `ChangeRequest` e auditoria. Nunca se concede
autoaprovação como atalho operacional.
