# Reconciliação da publicação local de faturamento e pagamentos

**Atualização após esta validação.** A V93 já constava como aplicada quando a publicação isolada de F05-39/40/41 começou. A publicação posterior preservou o histórico e levou apenas XML e cobertura de NF à API local. As referências ao pacote combinado abaixo descrevem a reconciliação anterior. [Evidência da publicação isolada](F05-39-41-VALIDATION-2026-11-06.md).

Em 07/11/2026, a consulta ao histórico Flyway do PostgreSQL local confirmou que a V92 já estava aplicada, com checksum `-335592003` e sem migrations com falha. O JAR preparado em 06/11 incluía uma variante da V92 com checksum `1842502814`, incompatível com esse histórico. A tabela de instruções de pagamento estava vazia na conferência.

A V92 no repositório foi restaurada ao conteúdo aplicado. O Flyway validou o histórico até V92 após essa restauração. A V93 adiciona uma chave estrangeira que exige vínculo entre o autor da instrução e a empresa, além de uma restrição que exige hash e tamanho quando houver PDF. A V93 preserva as revisões existentes e o histórico Flyway.

O teste de integração passou em PostgreSQL descartável com o caminho V91 → V92 → V93. Ele verifica a preservação do checksum e dos registros válidos, o bloqueio de autor de outra empresa e a rejeição de PDF sem hash ou tamanho. `checkstyleTest` e `bootJar` também passaram.

| Artefato | SHA-256 |
| --- | --- |
| JAR validado com V92 restaurada e V93 | `5A8B25A2A0183E89A5054D72D1E16106D8AE48ED961000B372F84850D1125A60` |
| Export OpenAPI 3.1 em `postman/canteiro-io.openapi.json` | `9FF228DE1D38A0EB61281C4C4DBE147F824A6F00C48FB4C8F31C4350BD0EEF42` |

A comparação dos JARs encontrou apenas a restauração da V92 e a inclusão da V93. Classes e dependências são idênticas às do pacote revisado em 06/11. O export do contrato permanece válido.

A API de `localhost:8081` ainda executa o pacote anterior. A V93 no banco persistente exige autorização específica conforme [AGENTS.md §14.1](../AGENTS.md). Após a autorização, a publicação inclui validar o histórico e a única migration pendente, guardar backup e referência de retorno, preparar a chave independente já autorizada, aplicar a V93 e verificar a API e o OpenAPI na instância local. A equipe de frontend recebe a confirmação após essa verificação.
