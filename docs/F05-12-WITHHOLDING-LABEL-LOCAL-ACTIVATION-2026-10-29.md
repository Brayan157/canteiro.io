# F05-12 — Ativação do indício recebido com retenção

**29/10/2026 às20:01:42 America/Sao_Paulo (23:01:42UTC), somente API localhost:8081. Concluído no backend.**

Pedido humano direto neste chat: “mas ai é imposto kkkkk, tem que colocar recebido”, no contexto de quitação por recebimento+imposto retido. Contrato/pacote/export conferidos pelo frontend antes da troca; atualização local autorizada conforme14.1AGENTS. Semnova migration ou lançamento financeiro.

[Guia](FRONTEND-BILLING-WITHHOLDING-LABEL.md),[candidato/plano](F05-12-WITHHOLDING-LABEL-CANDIDATE.md).

## Resultado e preservação

allocations.receivableReceivedWithWithholding:boolean|null deriva somente da mesma posiçãofinanceira autorizada já carregada. True para SETTLED_WITH_ADJUSTMENTS comsaldo0,cashCONFIRMED líquido deestornospositivo,retençãopositiva ecrédito0;falseoutrasposiçõesdisponíveis,nullsemREAD/títulooriginal. Permite rótulo “Recebida — com retenção de imposto”. Enum/caixa/saldos/filtros/histórico/snapshots não mudam; não considera imposto comodinheiro. Crédito/misto,100%retidosemcash,parcial,cancelado,renegociado eestorno continuam distintos.

Nenhuma consulta adicional ao banco ou porlinha; nenhum reprocessamento de recebimentos/impostos reais. Exemplo1000/retido100/cash900 continua recebidoemdinheiro900; não foi alterado para1000.

## Validação e pacote

9testes/3suítes aprovados em1m56s:6ManualInvoiceSettlementAPI,2GetBillingControlunit,1OpenAPI. Checkstylemain/test ebootJar aprovados; nãorodada de todaasuite. Provas emtmp/validation-withholding-label/build/test-results/test. Caso90cash+10retido/0saldo geraindíciotrue econsulta nãomuda dadosoficiais;estornofalse;crédito/misto,parcial,retençãointegralsemcash/cancelado/renegociadofalse;READ/títuloausentenull;202/tenant/403404 e>100títulos preservados.

JARativo **E09835F5734B5B6BBB5AEBF287D3EA01A2C6B2665DC00AD4D6E36011741950BE**,tmp/withholding-label-candidate/canteiro.io-withholding-label.jar. Delta4A2D4980:só4classesalteradas,zero adições/remoções/inesperados,bibliotecas/config/migrationsV1–V91 idênticos. Imagemsomente copiaoJARvalidado,semrecompilarárvore naativação.

## Runtime e contrato

Imagem `canteiroio-api:local-withholding-label-20261029`,ID `sha256:0986127243bcdcc71176e7e8b3fa7914c7b5497fe04cf83b520b1cc82ffeafea`.
Container `5980f3d9fa1889a3f5cd2fcb3ea861754f17c2d69727a1c119d3f4a6d258dd0d`.
Startup22.609s,23:01:42.943UTC;hashativo conferido apósstartup.

Ambienteidêntico,volumeprivadocanteiroio-api-private-storage em/app/var/storage,redecanteiroio_default,8081→8080,restartunless-stoppedpreservados. Flyway automático e seedsowner/demo continuamfalse. Schema91/checksum-1539369050/zero falhas inalterados. Semmigration,seeds,login,recebimento,estorno,imposto ououtrolançamentofinanceiroreal peloagente.

Anterior4A2D4980preservadoparado como `canteiro-api-before-withholding-label-20261029`,compatívelV91/posiçõesfinanceiras/indicadordesconto/relatóriodocumentalpararetorno. Não apagar/restaurarbanco para esse retorno.

OpenAPIoficial `postman/canteiro-io.openapi.json` SHA256 **0C36E2214E12EDA914132C17EA94546F2657B4E8031DEE9409A6F8C964A87DF1**,3.1.0/148paths/257schemas; sócampoindicador e requiredcorrespondente emBillingAllocationControlResponse,semalteraroutrosschemas/rotas/HTTP. Contrato vivo200application/json com147paths/257schemas,todoscompartilhados/componentesidênticosaoexport;únicaausênciaesperadawebhookdesabilitado. GETanônimobillingcontrolporcontratoUUIDsentinela401ProblemDetailsUNAUTHENTICATED/correlationId.

Provas tmp/withholding-label-candidate/{package-comparison.json,validation-summary.json,openapi-evidence.json,baseline-runtime.json,publication-runtime.json,startup.txt,live-contract-evidence.json,live-openapi.json}. Frontend avisado daativação; UI/testes finais seguem naconversa original,sempagamentosreais.
