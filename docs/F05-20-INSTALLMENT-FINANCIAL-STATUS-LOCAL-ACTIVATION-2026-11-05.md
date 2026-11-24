# F05-20 — situação financeira na lista e detalhe de cobranças

Publicado e verificado somente em `localhost:8081` em **05/11/2026 às 12:08:09 São Paulo (15:08:09 UTC)**, pela autorização local AGENTS14.1. [Guia do frontend](FRONTEND-BILLING-INSTALLMENT-READ.md). Sem migration, seed ou alteração de cobranças, status, recebimentos ou outros dados reais.

## Correção

Os GETs `/api/v1/company/billing-installments` e `/{installmentId}` agora acrescentam os três campos da projeção financeira já existente no controle de faturamento: `receivableFinancialStatusAvailable`, `receivableFinancialStatus` e `receivableReceivedWithWithholding`. Campos obrigatórios; os dois últimos são anuláveis. A posição canônica vem somente do título original, com dinheiro confirmado líquido de estornos, retenções e créditos oficiais. Sem `RECEIVABLES.READ` adicional a `BILLING.READ`, ou sem original, retornam false/null/null. Renegociação conserva a situação do original mesmo quando um sucessor foi recebido; não somar sucessores.

O hint de recebido com retenção exige saldo zero, dinheiro positivo, retenção positiva e crédito zero, com status SETTLED_WITH_ADJUSTMENTS. Permite a apresentação “Recebida — com retenção de imposto”, sem converter imposto em caixa ou regravar status. A regra foi centralizada em ReceivableFinancialPosition e reutilizada em BillingAllocationControl; seu contrato anterior permanece idêntico.

`status` permanece cadastral/histórico, assim como seus filtros/ordenações. `netAmount` continua o líquido histórico das alocações. Nenhuma nova rota, fórmula monetária, enum ou valor privado foi publicado. Paginação e totais mantidos; posições são carregadas numa query batch dos IDs da página/tenant, não por linha nem por varredura de todo contrato. A leitura não altera auditoria/estado oficial; propostas202 não entram no cálculo. A UI usa o financeiro autorizado como principal, cadastral secundário, cache por permissão e fallback seguro para indisponibilidade/enum futuro.

## Validação e escopo do pacote

**16 testes distintos em quatro suítes**, sem falhas, erros ou skips: sete cenários de projeção/segurança/batch no ManualInvoiceSettlementApiIntegrationTest; cinco regressões dos GETs de parcelas no MeasurementContinuousApiIntegrationTest; três de domínio ReceivableFinancialPosition; um OpenApiContractIntegrationTest. Cobrem parcial/total/estorno, retenção com ou sem caixa, crédito/misto, original cancelado/renegociado com sucessor pago, ausência de título/permissão,202 sem efeito, duas empresas, batch limitado à página, filtros/ordenação/paginação e snapshots de dados/auditoria inalterados pelos GETs. Testes em PostgreSQL/Testcontainers temporário. Não foi executada toda a suíte do projeto.

Checkstyle main/test sem erros, bootJar aprovado; build concluído em7m13s. Os resultados finais desta bateria foram preservados em `tmp/validation-installment-financial-status/verified-financial-read/test` e os relatórios Checkstyle em `verified-financial-read/checkstyle`, antes de reutilizar somente o diretório de compilação em validação posterior. O diagnóstico de duração mostrou o processo Java ativo executando integração; não houve nova execução da bateria nem reconstrução após testes.

Delta integral do JAR contra a F03-19:14 classes alteradas e uma adicionada (Summary interno da leitura), todas em receivables; nenhuma removida. Somente leitura, DTOs, mapper, customizer de nulabilidade, batch do repositório e extração da regra existente de apresentação. Classes da F03-19, bibliotecas, configuração e migrations V1–V91 byte a byte idênticas. Manifesto completo em `tmp/installment-financial-status-candidate/package-scope.json`.

## OpenAPI executável

`postman/canteiro-io.openapi.json`, SHA256 **ADD231B994E6E40F446E14F740B19F28F30D0010C2E7D803C5B8062674AEBC42**,151paths/263schemas, gerado da aplicação no teste contratual. Somente os dois GETs e os schemas `BillingInstallmentSummaryResponse`/`BillingInstallmentDetailsResponse` mudaram. Tipo/enum anulável inclui null de fato, além de seis estados oficiais; campos de dinheiro existentes preservados como strings decimais canônicas.

`/v3/api-docs` vivo:200application/json/OpenAPI3.1.0,150paths/263schemas. Todos os caminhos compartilhados e componentes idênticos ao export; a única ausência é `/api/v1/webhooks/payments`, desabilitado localmente. Ambos GETs de cobranças e o GET de elegibilidade da F03-19 retornam401application/problem+json/UNAUTHENTICATED/correlationId sem autenticação e com IDs fictícios. Não foi usado login ou dado real para testar situação financeira.

## Runtime e retorno

- JAR ativo: **6477462FE7FC9B9F6E7D8A7BB7F9DA74616F4416765CF72BE48FB32D17006121**, preservado em `tmp/installment-financial-status-candidate/canteiro.io-installment-financial-status.jar`.
- Imagem: `canteiroio-api:local-installment-financial-status-20261105`, **sha256:815de44216904ac8ee46798c617cd406429af4cffda585ed97ada767a0d2989e**. Cópia do mesmo JAR validado, sem recompilar fontes ou baixar dependências na publicação.
- Container: `c8b73ce7c7f4c9c5a06a9831da1ffa024931cb61c8c8087e7e1ece1cf73e3058`, iniciado15:07:01 UTC, pronto15:08:09 UTC. Nenhum reinício durante a verificação.
- A primeira janela de45s foi insuficiente; uma tentativa HTTP feita antes da prontidão falhou por conexão interrompida. Diagnóstico: processo ativo, CPU em uso e banco conectado, sem exceção de startup. Uma janela adicional limitada a60s confirmou startup em64,286s; a repetição HTTP após prontidão passou. Nenhuma mudança de código, novo build ou reinício foi necessário para esse ajuste de verificação.
- Ambiente completo comparado sem imprimir segredos e idêntico ao anterior. Volume privado `canteiroio-api-private-storage`→`/app/var/storage`, rede `canteiroio_default`,8081→8080 e restartunless-stopped preservados. Flyway e ambos seedsfalse. Schema antes/depois91|success=true|checksum=-1539369050, zero falhas.
- Referência anterior F03-19 **0F247DFF16B878C16BAD8B3F7547C7BD1E90A3AB849901062AE50815D759C95A** preservada no container parado `canteiro-api-before-installment-financial-status-20261105` e arquivo `tmp/installment-financial-status-candidate/rollback-0f247dff.jar`. Em falha, preservar/parar candidato e restaurar o container compatível anterior, sem apagar/restaurar banco ou reprocessar lançamentos.

Provas em `tmp/installment-financial-status-candidate`: `package-scope.json`, `runtime-evidence.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. SDD, backlog e catálogo de endpoints atualizados. O frontend recebeu o export congelado e a comprovação de ativação; validação visual/tipos é informada separadamente. Sem commit/push ou publicação remota neste recorte.

## Confirmação da integração frontend — 05/11/2026

A conversa Fase8 confirmou F08-05 integrada ao exportADD231B9, com tipos regenerados. Lista e detalhe agora usam situação financeira como principal e estado cadastral secundário; filtros/ordenação são identificados como cadastrais. Cache de lista considera RECEIVABLES.READ; projeções sem permissão, nulas ou com enum futuro têm apresentação segura. Usa o hint oficial de retenção, sem soma de saldo nem busca de recebível por linha. O consumidor de tributos/NF que compartilha a chave de cache foi alinhado sem mudança de POST. Conferiu os dois schemas vivos contra o snapshot e leu a evidência do runtime6477462F; módulo frontend atual respondeu200. F03-19 preservada.

Evidências reportadas pelo frontend, separadas da bateria backend:108 testes distintos em quatro arquivos em rodadas, com104 resultados iniciais e reexecução direcionada de55 testes em dois arquivos após correção de quatro expectativas;31 testes de política; sete E2E com API interceptada mais uma reexecução suplementar do caso afetado, em quatro larguras. Não somar104+55 como testes distintos. Build/scanner e lint da aplicação aprovados, artefato final `index-g7HVpuJ9.js`/CSS `7O283G6k`. Não houve nova execução de backend ou reinício para essa confirmação.

O lint global preexistente continua pendente porque inclui dependências em tmp; o frontend não afirmou aprovação de todos os checks gerais. Nenhum login, alteração de status/desconto, operação financeira real, migration ou seed. Integração funcional local confirmada; essa prova de UI usa API interceptada e não equivale a teste financeiro autenticado com registros reais.

Runtime posteriormente substituído em05/11 pela [melhoria de feedback de encerramento](F03-19-CLOSURE-RECONCILIATION-FEEDBACK-LOCAL-ACTIVATION-2026-11-05.md). Classes da F05-20 preservadas byte a byte; este registro continua descrevendo a ativação histórica6477462F.
