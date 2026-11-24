# F03-19 — encerramento, situação e descontos: entrega local

Publicado e verificado somente em `localhost:8081`, em 05/11/2026 às 09:25:11 São Paulo (12:25:11 UTC), pela autorização local AGENTS14.1. O frontend recebeu o export congelado antes da troca e a confirmação de ativação. Nenhuma migration, seed, recuperação de desconto ou operação real foi executada.

## Resultado

[Guia de integração](FRONTEND-CONTRACT-LIFECYCLE.md), [catálogo de rotas](FRONTEND-API-ENDPOINTS.md) seção2.6 e SDD atualizados. As rotas implementadas são:

- GET `/api/v1/company/contracts/{contractId}/closure-eligibility`: paginação, filtro exato `serviceId`, resumo sobre todos os serviços e decisões oficiais sem valores financeiros.
- PUT `/api/v1/company/contracts/{contractId}/status`: somente status, completedOn opcional e justificativa.
- PUT `/api/v1/company/contracts/{contractId}/services/{serviceId}/status`: somente status e justificativa.

Conclusão exige posição oficial da origem quitada por dinheiro confirmado e retenção comprovada por item. Crédito/legado sem identidade exigem conciliação. Contrato exige filhos terminalmente compatíveis; cancelamento exige todos cancelados e compromissos resolvidos. Não há cascata. Contrato encerrado exige reabertura explícita antes de novos serviços/composições. CANCEL exige alçada CANCEL;202 somente proposta e aprovação revalida sob lock, sem reaplicar perfil/preço/desconto antigos.

Cobrança com saldo oficial positivo bloqueia novo desconto mesmo sem NF; líquido proposto não pode ficar abaixo do caixa confirmado líquido de estornos. As restrições anteriores de faturamento/origem/desconto comprometido continuam válidas. Não há rateio de caixa, quitação por crédito ou alteração automática de histórico.

## Verificação

131 testes distintos em oito suítes, sem falhas, erros ou skips:59 de recebimento manual/NF (16 novos casos de lifecycle),7 de obra/contrato,51 de medição contínua,2 de conversão,3 do caso de uso de desconto,4 do cálculo líquido,4 de domínio de serviço e1 OpenAPI. Checkstyle main/test sem erros e bootJar aprovado. O primeiro passe de26 testes está preservado mas não foi somado aos 131; não foi executada toda a suíte do projeto. PostgreSQL/Testcontainers temporário. Resultados em `tmp/validation-contract-lifecycle/build/test-results/test` e relatórios Checkstyle no mesmo build.

O pacote foi conferido integralmente contra a referência ativa 5E46. Somente classes de contratos, adapter de encerramento financeiro, cálculo canônico de capacidade com projeção e duas guardas de composição de medição foram alteradas/adicionadas; nenhuma classe removida. Bibliotecas, configurações e migrations idênticas. O detalhe integral está em `tmp/contract-lifecycle-candidate/package-scope.json`.

OpenAPI executável exportado em `postman/canteiro-io.openapi.json`: **71C62D9911C22DEC2B1C55280BA28257F322FD79CA78846C91793D9A60B36294**,151 paths/263 schemas. `/v3/api-docs` retorna200 application/json,OpenAPI3.1.0,150 paths/263 schemas. Componentes e paths compartilhados idênticos; única ausência é `/api/v1/webhooks/payments`, desabilitado no perfil local. Decisões elegíveis/modo têm nulabilidade explícita, inclusive null no enum de modo. GETclosure e ambos PUTstatus anônimos, com IDs fictícios, retornam401 application/problem+json/UNAUTHENTICATED/correlationId.

## Runtime e retorno

- JAR: **0F247DFF16B878C16BAD8B3F7547C7BD1E90A3AB849901062AE50815D759C95A**, preservado em `tmp/contract-lifecycle-candidate/canteiro.io-contract-lifecycle.jar`.
- Imagem: `canteiroio-api:local-contract-lifecycle-20261105`, **sha256:c6feb0d23521b2291ff39596b212339ab1aa38b65b0ea316b7b7c967cd02a8d9**. Construída copiando o mesmo JAR testado, sem recompilar ou baixar dependências.
- Container ativo: `2f0abfb0a4d0dc3710505a223082e27b279e5d26f9b3698019610b35975f49d7`, iniciado12:24:54 UTC, pronto12:25:11 UTC.
- Retorno compatível:5E4601A1C460030EA91C57D89DEC6B67A61F0C975D1CCFF24DE541436EF7F06C no container parado `canteiro-api-before-contract-lifecycle-20261105` e arquivo `tmp/contract-lifecycle-candidate/rollback-5e4601a1.jar`. Em falha, preservar/parar candidato, renomear/restaurar o anterior, sem apagar/restaurar banco nem reprocessar lançamentos.
- Schema antes/depois:91|success=true|checksum=-1539369050, zero falhas. Flyway e ambos seedsfalse. Ambiente completo comparado sem imprimir valores, volume privado `canteiroio-api-private-storage`→`/app/var/storage`, rede `canteiroio_default`,8081→8080 e restartunless-stopped preservados.

Provas: `tmp/contract-lifecycle-candidate/package-scope.json`, `runtime-evidence.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. Validação de interface/tipos do frontend é separada; nenhum teste contra dados financeiros reais. Sem commit/push ou publicação remota neste recorte.

## Retorno da integração frontend — 05/11/2026

A conversa Fase8 confirmou FRONT-F06-27 integrada localmente ao export71C62D99, com cópia do contrato e tipos regenerados. Conferiu os três paths novos e schemas correspondentes contra8081. Edição no detalhe, ações específicas de situação, consulta exata de elegibilidade por serviço, alçada UPDATE/CANCEL, DIRECT/202 e motivos de desconto foram integrados sem cálculo financeiro local nem reenvio de preços/descontos.

Evidência informada pelo frontend:75 testes distintos em seis arquivos (rodada73, depois24 com dois novos; não somar as reexecuções),31 testes de política, build/scanner final `index-CslGk-M-` e dois E2E com API interceptada em quatro larguras aprovados. Vite5173 iniciado com API e protótipo desativado; módulo atual200 e auth/me anônimo401 com CORS para localhost5173. Nenhuma operação financeira/status real, login, migration ou seed.

Pendência separada do frontend: o lint geral sem caminhos inclui `tmp/validation-invoice-taxes/dependency-package/.../utilsBundle.js` e falha em dependência temporária. Lint de src/e2e/scripts e arquivos alterados aprovado. O frontend registrou “Integrado local — lint geral pendente”; a confirmação não significa que todos os checks gerais do frontend passaram. Não exige repetir a bateria backend nem reiniciar a API.

Referência de runtime posteriormente substituída, ainda em05/11/2026, pela [correção de leitura financeira de cobranças](F05-20-INSTALLMENT-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-11-05.md). As classes/regras da F03-19 foram preservadas byte a byte; este registro continua descrevendo a ativação histórica0F247DFF.
