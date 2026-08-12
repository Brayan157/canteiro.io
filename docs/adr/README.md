# Architecture Decision Records (ADRs)

Este diretório registra decisões arquiteturais duradouras da API. ADRs aceitos
são obrigatórios para implementações futuras, em conjunto com o
[AGENTS.md](../../AGENTS.md) e o [SDD](../SDD-MVP-Gestao-Financeira.md).

| ADR | Decisão | Estado |
| --- | --- | --- |
| [0001](0001-tenancy-e-isolamento.md) | Tenancy e isolamento por empresa | Aceito |
| [0002](0002-identificadores-uuid.md) | Identificadores UUID | Aceito |
| [0003](0003-dinheiro-quantidades-e-arredondamento.md) | Dinheiro, quantidades e arredondamento | Aceito |
| [0004](0004-datas-horas-e-utc.md) | Datas, horas e UTC | Aceito |
| [0005](0005-exclusao-logica-e-rastreabilidade.md) | Exclusão lógica e rastreabilidade | Aceito |
| [0006](0006-aprovacao-e-auditoria.md) | Aprovação e auditoria | Complementado por 0007 |
| [0007](0007-alcada-direta-efetiva.md) | Alçada direta efetiva para editor/aprovador | Aceito |

Uma mudança nestas decisões, especialmente nas regras marcadas como
invariantes no `AGENTS.md`, exige autorização explícita do responsável pelo
produto e uma nova ADR que substitua ou depreque a anterior.
