# F00-11 — Revisão de gate da Fase 0

- Estado: revisado com validação operacional pendente
- Data: 2026-08-10
- Branch revisada: `feature/fase-0-fundacao`

## Estrutura modular

| Verificação | Resultado |
| --- | --- |
| Novos módulos por funcionalidade e camadas `api/application/domain/infrastructure` | Conforme. |
| `Company` fora dos pacotes genéricos legados | Conforme, em `platform/company`. |
| Pacotes legados `dto`, `controller`, `entity`, `repository`, `security` e `service` | Ausentes. |
| Pacotes raiz restantes | `config` contém apenas a configuração OpenAPI herdada; `exception` está vazio. Nenhum é usado como padrão para código novo. |
| `bootstrap` | Ausente por decisão explícita do produto; deve ser recriado somente quando houver configuração de inicialização que justifique o módulo. |

## Migrations e persistência

| Verificação | Resultado |
| --- | --- |
| Flyway | Conforme: V1 é preservada e V2 é uma migration incremental. |
| UUID | Conforme no ID JPA e no domínio `Company`. |
| Timestamps técnicos | Conforme: `Instant` no código e `TIMESTAMP WITH TIME ZONE` na V2. |
| Constraints de `Company` | Conforme: timestamps obrigatórios e unicidades nomeadas para documento/e-mail. |
| Exclusão | Conforme: desativação lógica por `active=false`; não há `DELETE` SQL no módulo. |

## Segredos e ambientes

| Verificação | Resultado |
| --- | --- |
| Credenciais em `application*.properties` | Conforme: somente referências a variáveis de ambiente. |
| Docker Compose | Conforme: recebe credenciais por variáveis; não contém senha fixa. |
| `.env` | Ignorado pelo Git. |
| `.env.example` | Contém apenas valor de exemplo, que deve ser substituído em cada ambiente. |
| CI | Workflow instala Java 21, executa `check` e relatório JaCoCo. |

## Qualidade e testes

Checkstyle, JaCoCo (mínimo inicial de 15%), GitHub Actions e testes com
PostgreSQL/Testcontainers estão configurados. As migrations são exercitadas por
teste de integração e o schema de `Company` é verificado em PostgreSQL real.

## Pendências obrigatórias antes da Fase 1

1. Executar e aprovar `./gradlew check jacocoTestReport` em ambiente com Java
   21 e Docker, localmente ou no GitHub Actions.
2. Confirmar a primeira execução verde do workflow `CI` na branch antes de
   iniciar identidade, tenant, permissões e auditoria.

Essas pendências são operacionais: nenhuma alteração de escopo, invariantes ou
schema adicional foi identificada nesta revisão.
