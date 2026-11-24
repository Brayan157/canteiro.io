# F05-37 — Pacote revisável e retorno, sem migrations

Preparado e **publicado somente na API local8081 em29/10/2026 sob nova autorização humana específica**, sem migrations ou operações financeiras reais. A ativação anterior F05-32 não foi reutilizada como autorização. [Provas da publicação](F05-37-LOCAL-ACTIVATION-2026-10-29.md), [contrato e exemplos](FRONTEND-TOTAL-INVOICE-SETTLEMENT-PROPOSAL.md).

## Artefatos e escopo

- JAR preservado: `tmp/invoice-total-proposal-candidate/canteiro.io-f05-37.jar`, SHA-256 `B971C83CB82DBDA6BACBF1485DECCC5220F38D8B8D115FE96432FB359F09CF37`.
- OpenAPI executável aprovado: `postman/canteiro-io.openapi.json`, cópia preservada `tmp/invoice-total-proposal-candidate/canteiro.openapi.candidate.json`, SHA-256 `7BF8E2214BE0046868B431A48F60B5021857DC0E61CAA88F2EDBD8852F07C3E8`,147paths/257schemas. Apenas GET opções/schema correspondente e2schemas novos diferem; segurança/ProblemDetails preservados.
- Referência ativa conferida por leitura em29/10 às09:08:15 São Paulo: JAR F05-32 `0E25A12A80175D955C17383E1859A8CFB1754A51700B7CD77864275537D576ED`, imagem `canteiroio-api:local-manual-invoice-settlements-20261029`, ID `sha256:701376c965ca028e21fe41a97785d1767df0e00c657c4a50b77c1955ee92d48c`; API em execução desde11:19:12UTC, schema90 bem-sucedido/checksum1503018190, Flyway automático/owner seed/demo seed desligados. Prova em `tmp/invoice-total-proposal-candidate/baseline-runtime.json`. Conferir novamente antes de eventual troca.
- Comparação integral por hash de cada entrada descomprimida com o JAR ativo:1541→1544 entradas,6alteradas/3adicionadas/nenhuma removida ou inesperada. Allowlist explícita de classes, sem permitir módulo inteiro. Bibliotecas/configuração/loader e **todas as migrations V1–V90 idênticos**, preservando o ledger e fluxos F05-32.
- Classes alteradas: controller de opções, InvoiceSettlementOptionsResponse e InvoiceSettlementService/Options; Option/Result foram regenerados, mas `javap -p -c -s -constants` confirmou desassemblagens idênticas. Adicionados TotalProposal e os dois records de resposta. Comparador/manifesto/prova em `tmp/invoice-total-proposal-candidate/compare-package.ps1`, `package-comparison.json` e `nested-record-review.json`.
-22 testes/2suítes e Checkstyle main/test aprovados em3m42s. BootJar no mesmo diretório de validação aprovado em17s, compileJava/processResources UP-TO-DATE; nenhuma nova rodada de testes contabilizada. Resultados em `tmp/validation-invoice-total-proposal/build`; cópias XML em `tmp/invoice-total-proposal-candidate/validation-22-test-results`.

Não há mudança de política de retenção/rateio: somente distribuição total inequivocamente determinada. Caso ambíguo conserva manual; proposta não cria recebimento nem reserva. POST, aprovação, idempotência, origem exata e estornos existentes permanecem. Sem operações financeiras reais ou homologação autenticada real.

## Sequência caso haja nova autorização específica

1. Confirmar aprovação humana para atualizar **somente API local8081 com este JAR, sem migrations**, depois da revisão frontend. Manifesto ou mensagem de agente não substituem autorização humana.
2. Conferir novamente hash ativo0E25..., schema90/checksum, ambiente/rede/porta/volume e imagem atuais. Se referência divergir, parar a substituição para revisão. Não incluir código novo da árvore compartilhada.
3. Criar imagem somente por cópia do JAR preservado sobre a base ativa conferida, sem build de fontes/download de bibliotecas. Conferir hash dentro da imagem antes da troca. Preservar container/imagem anteriores, configuração privada, rede/porta8081, volume e restart policy; não imprimir segredos.
4. Trocar somente a aplicação após preparar o substituto, com **SPRING_FLYWAY_ENABLED=false** e seeds owner/demo false. Não executar runner Flyway, SQL de migração, clean, repair, seed, backfill ou restauração de banco. Não reutilizar o helper de publicação V90, que aplica migration; o helper antigo que exige V89 também não serve.
5. Verificar startup, JAR ativo, flags, schema90 inalterado, `/v3/api-docs`200, todos os caminhos compartilhados/componentes idênticos ao export aprovado. Única ausência prevista: webhook payments local desabilitado. Verificar401 ProblemDetails/correlationId sem login nas rotas protegidas. Sem POST financeiro autenticado, upload ou estorno real.
6. Entregar provas ao frontend. A configuração local F05-32 existente não precisa ser recriada; o botão de preenchimento usa a proposta oficial somente após ela existir. Falta de campo mantém manual. Não publicar remoto nem absorver outras tarefas.

## Retorno compatível

O retorno previsto é **à aplicação F05-32 atual0E25...**, mantendo schema90, ledger, recebimentos, propostas e volume intactos. Ela já entende os mesmos vínculos de recebimento/estorno V90; este candidato só acrescenta leitura/projeção e não introduz formato persistido novo. O frontend deve conservar fallback manual se a proposta de leitura desaparecer.

Não retornar ao F05-36/FCEE... nem a builds anteriores ao ledgerV90 para executar mutações financeiras. Nunca apagar tabelas, baixar versões Flyway, restaurar backup sobre operações atuais ou estornar dinheiro para trocar aplicação. Se a referência de retorno mudar ou as provas falharem, interromper a troca e revisar; não afirmar restauração sem verificar startup/hash/configuração.
