# Refatoração modular de Company

- Data: 2026-08-10

O módulo legado de empresa foi migrado para `platform/company` sem criar novos
endpoints ou alterar a tabela `company`.

| Camada | Responsabilidade |
| --- | --- |
| `api` | Controller, requests, response e mapeamento de borda. |
| `application` | Commands e casos de uso transacionais. |
| `domain` | Entidade `Company` e porta de repositório. |
| `infrastructure` | Entidade JPA, Spring Data, mapper e adaptador de persistência. |

Os pacotes legados equivalentes foram removidos. A atualização agora preserva a
entidade JPA existente quando o domínio possui ID, evitando a perda do UUID em
uma operação de update. A adequação de timestamps/constraints e os testes de
integração continuam pertencendo às tarefas F00-07 e F00-08.
