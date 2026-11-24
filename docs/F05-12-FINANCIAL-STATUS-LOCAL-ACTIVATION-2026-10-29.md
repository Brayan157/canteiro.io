# F05-12/F05-33 — Ativação da situação financeira no controle de faturamento

**29/10/2026 às19:22:27 America/Sao_Paulo (22:22:27UTC), somente API localhost:8081. Concluído no backend.**

Pedido humano confirmado diretamente no chatFase8: “Tem algumas cobranças que já recebi, mas a situação delas aparece em aberto, no detalhe do faturamento”. Conferência decontrato/candidato/export pelo frontend antes daativação. Autorização local14.1AGENTS aplicada ao mesmo pedido; semnovamigration ou operaçãofinanceira real.

[Guia](FRONTEND-BILLING-FINANCIAL-STATUS.md). [Candidato e plano](F05-12-FINANCIAL-STATUS-CANDIDATE.md).

## Correção

GET `/api/v1/company/billing-control/contracts/{contractId}` conserva rawinstallmentStatus e acrescenta,porallocation,receivableFinancialStatusAvailable/receivableFinancialStatus. RECV.READ adicional aBILLING.READ;sempermissão/títulooriginal=false/null,nãozero/OPENfictício. Posição do títulooriginal,mesmotenantecontrato;reutilizaReceivableReadProjection.financialPosition,semcálculoparalelo/inferênciaporNF/data. Leitura emlotes100,nãoportadorporalocação. Cancelado/renegociadopreservados,filhosnãoagregados;ajustessemcashnãoRECEIVED;202semoficialização.

Não regravou status histórico nem saldos/baixas/impostos/créditos/renegociações. A nova projeção é uma consulta dos dados oficiais existentes. Frontend utilizará o campo separado; valor repetido emlinhasdamesmaparcela não comprova pagamentoindividual de serviço.

## Validação

8testes distintos/3suítes aprovados emrodadasidentificadas:5ManualInvoiceSettlementAPI,2GetBillingControlunit,1OpenAPI. Rodadafinanceira8passaram1m15s;depoistesteOpenAPIfinalrepassou56s comenumadmitindoJSONnull. Não somarreexecuções,nãoexecutadatodaasuite. Checkstyle main/test ebootJar aprovados.

Casos:rawOPENcomcash30/100,estornoparcial/total;retido100/crédito100semcash;cancelado/renegotiatedoriginalcomfilhorecebido;202/permissãoausente/títuloausente/403BILLING.READ/404outrotenant;posiçãoapós100títulos. Fixturesiniciais corrigidas semrelaxar regras. CustomizaçãoSpringdoc local admiteJSONnullnoenumcopiandoalistadevaloresimutável;testeverifica/v3/api-docs200 eJSONnullreal.

## Pacote/runtime

JARativo **4A2D498061A39273D99A0F3AF0C8459A4EFC0CD8BF3A22762BE03D5C5893C397**, `tmp/billing-financial-status-candidate/canteiro.io-billing-financial-status.jar`.
Delta aoAF20:4classesalteradas e1configOpenAPIadicionada,nenhuma remoção/inesperada;bibliotecas/configuraçãoempacotada/migrationsV1–V91 idênticos. ImagemcopiasomenteJARvalidado,semrecompilarárvoreduranteativação.

Imagem `canteiroio-api:local-billing-financial-status-20261029`, ID `sha256:741677e42a0586d37bdb473511eeb0d2ce20270b5f26e0b07a77877cd9179abf`.
Container `468bfa9b831446b47ba618e4bb9463814e552c48e732d5fa72d2151a3d376908`.
Startup12.864s,22:22:27.107UTC;hashconferido apósstartup.

Ambienteidêntico,redecanteiroio_default,volumecanteiroio-api-private-storage em/app/var/storage,8081→8080,restartunless-stoppedpreservados. Flyway automático e seedsowner/demo continuamfalse. Schema91/checksum-1539369050/zero falhasinalterados. Semmigration/seeds/login/reprocessamentohistórico/desconto/NF/baixa/estornoreal/relatório real peloagente.

AF20preservadoparado em `canteiro-api-before-billing-financial-status-20261029`,compatívelV91/indicadordesconto/relatóriodocumentalpararetorno. Não restaurar/apagar banco para retornar.

## OpenAPI/proteção

Export `postman/canteiro-io.openapi.json`, SHA256 **0DE96AE583EB332C4E4C345424BEF97B464B86E93156710BFAE3F31B4BDE2E9D**,OpenAPI3.1.0/148paths/257schemas. Só BillingAllocationControlResponse mudou:2campos,requiredcorrespondentes e descriçãodorawstatus. EnumnullableincluiJSONnull.

Contrato vivo200application/json,147paths/257schemas,todoscompartilhados/componentesidênticosaoexport;únicaausênciaesperadawebhooklocaldesabilitado. GETanônimobilling-control/contracts/UUIDsentinela:401application/problem+json,UNAUTHENTICATED,correlationIdpresente. Não foi usado registro real para testeautenticado.

Provas em `tmp/billing-financial-status-candidate/{package-comparison.json,validation-summary.json,openapi-evidence.json,baseline-runtime.json,publication-runtime.json,startup.txt,live-contract-evidence.json,live-openapi.json}`. Frontend recebeu contrato/tiposgerados e confirmação daativação; integraçãoUI/testes seguem naconversa original.
