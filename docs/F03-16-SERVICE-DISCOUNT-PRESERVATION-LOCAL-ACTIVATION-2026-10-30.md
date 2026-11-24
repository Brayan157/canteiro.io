# F03-16 — ativação local da preservação de desconto

**Publicado e verificado somente em localhost:8081 em 30/10/2026 às 00:47:58, São Paulo** (03:47:58 UTC). Autorização local AGENTS14.1, candidato/contrato conferidos pelo frontend antes da troca. Sem migration, seed, recuperação automática ou operação financeira real.

[Guia atual](FRONTEND-SERVICE-UPDATE-DISCOUNT-PRESERVATION.md). [Reprodução, candidato e retorno](F03-16-SERVICE-DISCOUNT-PRESERVATION-CANDIDATE.md).

## Resultado

O PUT operacional de serviço preserva o desconto vigente quando o par de campos é omitido ou null, tanto em DIRECT como na aprovação. Status COMPLETED permanece incluído no cálculo. Aprovação não reaplica desconto antigo do snapshot; eco igual é permitido e intenção divergente deve usar a operação dedicada DISCOUNTS, com409/reason controlado. Não altera fórmulas, alçadas, vínculos de composição ou desconto de cabeçalho. Nenhum desconto perdido de contrato real foi reconstruído ou reaplicado.

## Pacote e retorno

- JAR ativo: **5E4601A1C460030EA91C57D89DEC6B67A61F0C975D1CCFF24DE541436EF7F06C**. Pacote preservado em `tmp/service-discount-preservation-candidate/canteiro.io-service-discount-preservation.jar`.
- Imagem: `canteiroio-api:local-service-discount-preservation-20261030`, ID `sha256:680383539511fb32acb5f50e7c635ef58f9ae0e13628d0d5583ce2274ffcfa78`. Construída por cópia do JAR aprovado, sem recompilar fontes nem baixar dependências.
- Container: `925fcbe428e1c0285e5dce7e5f49bcb2e7aaf45f182f81fa277708f3cf4839ac`, iniciado03:47:36 UTC, pronto03:47:58 UTC.
- Baseline compatível E97C650CBC8F23B5DE8F62CE7438845D02D43574A38B18D9DC628AED2E8691BE preservado no container parado `canteiro-api-before-service-discount-preservation-20261030` e em `tmp/service-discount-preservation-candidate/rollback-e97c650c.jar`.
- Delta integral: somente três classes de contratos alteradas; nenhuma adição/remoção. Bibliotecas, configuração, migrations V1–V91 e demais classes idênticas.
- Schema antes/depois: **91|success=true|checksum=-1539369050**, zero falhas. Flyway e ambos os seeds false. Ambiente completo comparado e idêntico sem imprimir segredos. Volume privado `canteiroio-api-private-storage` em `/app/var/storage`, rede `canteiroio_default`,8081→8080 e restartunless-stopped preservados.

## Validação

Reprodução anterior: dois testes falharam porque o desconto esperado voltou null, XML preservado. Após correção: **16 testes distintos/4 suítes**, Checkstyle main/test e bootJar aprovados em1m33s. Os dois testes de reprodução fazem parte dos16 finais, sem contagem duplicada. Não foi executada toda a suíte do projeto. PostgreSQL temporário em todos os testes financeiros.

OpenAPI executável exportado em `postman/canteiro-io.openapi.json`, SHA256 **4BFEA6EBE8C8BCF304C53B579A76C83E734960203C3CA1E0397779AB362B1CE2**,148paths/257schemas. `/v3/api-docs` retorna200application/json,OpenAPI3.1.0,147paths/257schemas; componentes e caminhos compartilhados idênticos ao export. A única ausência é o webhook de pagamento desabilitado; nenhum path inesperado. Descrições do Upsert e do PUT atualizadas, sem novos campos ou tipos manuais.

GET anônimo de billing-control e PUT anônimo de serviço com IDs fictícios retornam401application/problem+json, UNAUTHENTICATED e correlationId. O probe adicional de PUT inicialmente interpretou byte[] sem decodificação UTF8; corrigida somente a leitura do script e a prova foi aprovada. Não houve falha da API nem nova alteração de pacote nessa verificação.

## Evidências e limite da entrega

Em `tmp/service-discount-preservation-candidate`: `package-scope.json`, `runtime-evidence.json`, `live-contract-evidence.json`, `anonymous-update-evidence.json`, `live-openapi.json`, `startup.txt`. Resultados em `tmp/validation-service-discount-preservation/build/test-results/test`; reprodução anterior em `tmp/validation-service-discount-preservation/reproduced-before-fix.xml`.

Frontend confirmou a jornada concluída:37 testes focados em3arquivos e1E2E em4larguras aprovados, além de lint/build/scanner/typecheck. A jornada interceptada preservou serviço270000/desconto30000/cabeçalho13500/contrato256500 e comprovou PUT sem campos de desconto, sem nova operação de desconto implícita. Build index-Xvj5_YvO. Conferiu também OpenAPI vivo/descrições iguais ao4BFE,147paths/257schemas eGETservicesanônimo401. Essa prova de frontend é separada do probePUT anônimo do backend. A correção evita novas perdas; não repara automaticamente dados históricos já afetados. Nenhuma NF, imposto, baixa ou desconto real foi usado para teste. Sem commit/push ou publicação remota neste recorte.

Em falha, parar/preservar candidato e restaurar o container E97C compatível com schema91, sem apagar/restaurar banco ou reprocessar registros.

Recuperação de dados anteriores permanece separada e pendente: o frontend solicitou identificação do contrato/serviço e desconto anterior ao usuário; sem resposta confirmada, nenhuma recuperação foi iniciada ou inferida. A prevenção da falha e a publicação deste fix estão concluídas.
