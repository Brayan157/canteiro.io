# F05-37 — ativação local da retenção proporcional por item

**Publicado e verificado somente em localhost:8081 em29/10/2026 às23:42:22SãoPaulo** (2026-10-30T02:42:22.055Z). Decisão humana explícita de retenção proporcional, autorização de atualização local em AGENTS14.1, candidato e DTO conferidos pelo frontend antes da troca. Sem migration, seed, login ou operação financeira real.

[Contrato atual](FRONTEND-INVOICE-ITEM-WITHHOLDING.md). [Candidato, testes e retorno](F05-37-WITHHOLDING-ITEMS-CANDIDATE.md).

## Pacote e instância

- JAR ativo: **E97C650CBC8F23B5DE8F62CE7438845D02D43574A38B18D9DC628AED2E8691BE**, mesmo pacote preservado em `tmp/withholding-items-candidate/canteiro.io-withholding-items.jar`.
- Imagem: `canteiroio-api:local-withholding-items-20261029`, ID `sha256:2b2a6da320718ed1e17fd7fc236b5b89c39ad011892cc199a2326327ba723eec`; montada por cópia do JAR, sem recompilar fontes, baixar imagem ou dependências.
- Container: `8a5a3a17eb8ae5f6a9fb33ea0d6aa11d155f90509e8df9f660245e6377e12d50`, iniciado02:42:01UTC e pronto02:42:22UTC.
- Retorno compatível: `canteiro-api-before-withholding-items-20261029`, parado, com JAR E09835F5734B5B6BBB5AEBF287D3EA01A2C6B2665DC00AD4D6E36011741950BE. Pacote também preservado como `tmp/withholding-items-candidate/rollback-e09835f5.jar`.
- Schema antes/depois: **91|success=true|checksum=-1539369050**, zero migrations falhas. Flyway e seeds de proprietário/demo false. Ambiente completo comparado e idêntico sem imprimir segredos; volume `canteiroio-api-private-storage` em `/app/var/storage`, rede `canteiroio_default`,8081→8080 e restartunless-stopped preservados.

## Contrato vivo e segurança

Export executável SHA256 **49F89DB0775210BFE9FF56D89487AC589C68CC8E37B9B1B6142D836791D27DDE** em `postman/canteiro-io.openapi.json`,148paths/257schemas. `/v3/api-docs` responde200application/json,OpenAPI3.1.0,147paths/257schemas. Componentes e todos os caminhos compartilhados idênticos ao export; única ausência é `/api/v1/webhooks/payments`, desabilitado neste ambiente. Não há paths inesperados.

Probe anônimo de GET billing-control retorna401application/problem+json com UNAUTHENTICATED e correlationId. Nenhum token, login, consulta de NF7, imposto ou lançamento real foi usado para validar. Testes financeiros usam PostgreSQL temporário.

## Resultado e limites

50testes/4suítes, Checkstyle main/test e bootJar aprovados. Novos líquidos e imposto por item são requirednullable canonicaldecimalstrings. Proposta TOTAL fecha os líquidos após retenção, sem segundo desconto. Parcial permanece manual; POST/aprovação revalidam sob locks. Histórico não é redistribuído: conflito com o novo líquido exige conciliação controlada; escopo entre títulos, base divergente e excesso de itens retidos também ficam indisponíveis explicitamente.

A indicação já publicada “Recebida — com retenção de imposto” permanece separada e não converte imposto em dinheiro. Frontend confirmou integração concluída no chat Fase8:63testes focados em3arquivos e4E2E de distribuição em4larguras aprovados; inspeção visual360/1600, lint, typecheckE2E, build e scanner aprovados. Cenário1000/itens100 e900/retido100 preenche90 e810, total900, sem POST automático. Bruto, retenção, líquido, saldo líquido e limites oficiais apresentados sem soma de tetos ou segundo desconto; parcial manual e motivos de conciliação preservados. Build index-wtIoEFoG. O frontend também conferiu OpenAPI vivo147paths/257schemas, DTO igual ao export49F89DB0,401 comcorrelationId e runtimeE97C/schema91. Nenhuma operação financeira real foi usada. Sem pendência de integração ou ativação neste recorte.

## Provas e retorno

Provas locais em `tmp/withholding-items-candidate`: `tests-summary.json`, `package-scope.json`, `runtime-evidence.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. Resultados originais em `tmp/validation-withholding-items/build/test-results/test`.

Se necessário, parar/preservar candidato e restaurar o container E09835F5 compatível, sem restaurar/apagar banco ou reprocessar dados. Nenhuma publicação remota/produção ou commit/push foi realizado neste recorte.
