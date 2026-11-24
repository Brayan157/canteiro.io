# F03-19 — feedback de conciliação no encerramento

Publicado e verificado somente em `localhost:8081` em **05/11/2026 às12:54:46 São Paulo (15:54:46 UTC)**, pela autorização local AGENTS14.1. [Contrato do frontend](FRONTEND-CONTRACT-LIFECYCLE.md). Sem migration, seed, reparo histórico ou operação real de status, desconto, NF, recebimento ou estorno.

## Resultado

GET de elegibilidade preserva um motivo financeiro impeditivo autorizado mesmo quando o serviço consta COMPLETED. SERVICE_ALREADY_COMPLETED é fallback quando não há impedimento financeiro disponível. eligible continua false; sem os READs financeiros, não se resolve nem divulga o motivo privado. A guarda de PUT/aprovação continua exigindo origem/posição financeira comprovada.

O resumo do contrato e o motivo de409 distinguem CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED de CONTRACT_HAS_UNSETTLED_SERVICES. Origem não comprovada e crédito que exige conciliação têm prioridade sobre os demais impedimentos financeiros, independentemente da ordem dos serviços. O resumo continua considerando todos os serviços antes do filtro/paginação; não presume dinheiro faltante por ausência de rastreabilidade. Essa mudança de feedback não autoriza completar contrato ou serviço sem cumprir as guardas.

A eventual conciliação de histórico exige escopo/proposta próprios e autorização humana. Não foi iniciado workflow, endpoint, reatribuição ou correção de dados. Diagnóstico real foi limitado a consultas READ ONLY do alvo autorizado; dados reais/identificadores/valores não foram versionados nos documentos nem usados em fixtures.

## Testes e contrato

**21 testes distintos,23 execuções**, sem falhas/erros/skips, em duas rodadas. Primeira:17 testes de lifecycle/segurança/aprovação/concorrência e um OpenAPI; após o complemento do motivo do resumo, cinco testes direcionados: integração sintética de serviço concluído com impedimento/READs/fallback quitado, três testes de prioridade/conciliação/posição ausente e OpenAPI. Os dois reexecutados não são contados novamente como testes distintos. A rodada inicial levou5m44s; a direcionada4m56s. Checkstyle main/test sem erros e bootJar aprovados nas duas. Não foi executada toda a suíte do projeto.

PostgreSQL/Testcontainers temporário. Provas preservadas em `tmp/validation-closure-reconciliation/first-pass/{test,checkstyle}` e `final-pass/{test,checkstyle}`. Dados sintéticos não reproduzem identificadores ou valores do cliente. Preservação da F05-20 em `tmp/validation-installment-financial-status/verified-financial-read` antes da reutilização do diretório de compilação.

OpenAPI executável gerado e exportado em `postman/canteiro-io.openapi.json`: **F42E4998680B2BAF346D9FDA343904600BFF9A2C5C89ABFBD636EDACA3969423**,151paths/263schemas. Somente descrições de ClosureDecisionResponse e ContractClosureEligibilityResponse mudaram; nenhum path, propriedade ou DTO novo. Recebimentos continuam no fluxo próprio; nenhuma nova operação de conciliação foi documentada como entregue.

OpenAPI vivo:200application/json,3.1.0,150paths/263schemas. Componentes e caminhos compartilhados idênticos ao export, única ausência `/api/v1/webhooks/payments`, desabilitada localmente. GETs de lista/detalhe de cobrança/closure e PUTstatus anônimos retornam401application/problem+json/UNAUTHENTICATED/correlationId, usando IDs fictícios. Nenhum login ou operação real foi usado para essa verificação.

## Pacote e runtime

- JAR ativo: **D704704FA1C836580DB0F2573FF254FFB3A504A57EC008B2B54FB8E21EFF3A24**, preservado em `tmp/closure-reconciliation-feedback-candidate/canteiro.io-closure-reconciliation-feedback.jar`.
- Imagem: `canteiroio-api:local-closure-reconciliation-feedback-20261105`, **sha256:52a8d0659e2558f08a2c14b13003cd11dc2a838e0652b9ad6c922801f5ca8ab3**. Copiado o JAR validado, sem recompilar ou baixar dependências na publicação.
- Container: `00ceb72f1d8f4be3106ef42d7f847b48c703bca23d25232bcc15aaa4a7262175`, iniciado15:53:15 UTC, pronto15:54:46 UTC. Nenhum reinício durante a verificação.
- Delta integral: quatro classes de contratos alteradas (caso de uso de elegibilidade, classificação de motivo e dois DTOs de documentação), nenhuma adição/remoção. Demais classes, inclusive F05-20, bibliotecas, configuração e migrations V1–V91 idênticas à referência6477462F. Manifesto em `package-scope.json`.
- Ambiente completo comparado e idêntico, sem imprimir segredos; volume privado `canteiroio-api-private-storage`→`/app/var/storage`, rede `canteiroio_default`,8081→8080 e restartunless-stopped preservados. Flyway e ambos seedsfalse. Schema antes/depois91|success=true|checksum=-1539369050, zero falhas.
- Retorno compatível: **6477462FE7FC9B9F6E7D8A7BB7F9DA74616F4416765CF72BE48FB32D17006121** no container parado `canteiro-api-before-closure-reconciliation-feedback-20261105` e `tmp/closure-reconciliation-feedback-candidate/rollback-6477462f.jar`. Em falha, preservar/parar candidato e restaurar o anterior sem apagar/restaurar banco ou reprocessar negócios.

A janela inicial de60s não capturou a prontidão: diagnóstico mostrou container ativo, sem reinícios/erro de startup, banco conectado e inicialização concluída em84,714s. A primeira geração OpenAPI levou29,212s; o cliente com timeout30s cancelou a conexão e o servidor registrou ClientAbortException/Broken pipe. Uma repetição após geração do cache, com limite de60s, passou. Sem alteração do pacote/rebuild/reinício para esses ajustes de verificação. Avisos preexistentes do Springdoc não afetaram a igualdade final do contrato.

Provas em `tmp/closure-reconciliation-feedback-candidate`: `package-scope.json`, `runtime-evidence.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. SDD, backlog, catálogo e guia atualizados. Frontend recebeu somente contrato técnico congelado e prova de ativação; confirmação visual é separada. Sem commit/push ou publicação remota.

## Acompanhamento da integração frontend

A conversa Fase8 confirmou integração final do feedbackF42E4998: mensagem específica para CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED, com26 testes em dois arquivos aprovados. Lint de src/e2e/scripts aprovado com avisos anteriores; build511, artefato `index-BtZEfJi_` e scanner aprovados. Conferiu os dois schemas vivos contra o snapshot.

O cenário E2E de produção com API interceptada reaproveita regressões DIRECT/202/desconto e comprova serviço COMPLETED com bloqueio de conciliação sem novo PUT nessa etapa, em quatro larguras. Ao retomar, a sessão de console já havia expirado; a confirmação foi recuperada do `.last-run.json` com passed/failedTests=[] às12:59:26, após build12:58:04, e das quatro capturas12:59:24–25, incluindo inspeção da largura360. Não houve repetição da bateria ou reinício da API para recuperar a prova.

SDD/AGENTS/backlog do frontend atualizados. O lint global preexistente que inclui dependências em tmp permanece pendente; não afirmar aprovação de todos os checks gerais. Nenhum login, operação financeira/status real, migration ou seed. A conciliação de histórico continua fora do recorte, exigindo proposta/autorização próprias; nenhum workflow ou reparo foi iniciado.
