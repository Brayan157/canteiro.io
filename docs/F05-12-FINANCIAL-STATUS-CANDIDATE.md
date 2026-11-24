# F05-12/F05-33 — Candidato de situação financeira no controle

29/10/2026. **Testado, conferido pelo frontend e ativado somente em8081 em29/10/2026 às19:22:27SãoPaulo.** [Registro de ativação](F05-12-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-10-29.md). Plano anterior preservado abaixo. Sem nova migration, atualização de históricos, fórmulas paralelas ou operação financeira real.

[Guia de integração](FRONTEND-BILLING-FINANCIAL-STATUS.md). Camposaditivos emallocations:receivableFinancialStatusAvailable e receivableFinancialStatus(enumoficialnullable). MesmaBILLING.READ/tenant;RECEIVABLES.READ adicional para posiçãofinanceira. Originalcancelado/renegociado preservado,mesmostatusrepete emalocaçõesdamesmaparcela,não soma filhosnem comprova pagamentoindividual de serviço. RawinstallmentStatus continuainalterado.

JAR `tmp/billing-financial-status-candidate/canteiro.io-billing-financial-status.jar` SHA256 **4A2D498061A39273D99A0F3AF0C8459A4EFC0CD8BF3A22762BE03D5C5893C397**.
Comparação aoAF20D2D2ativo:4classesexistentesalteradas(BillingControlController/Response,GetBillingControlUseCase/BillingAllocationControl) e1configOpenAPIadicionada(BillingControlOpenApiConfiguration);zero remoções/inesperadas. Bibliotecas/configuraçõesempacotadas/migrationsV1–V91 idênticas; a configuraçãoSpringdoc nova atua somentenapropriedade nova paraenumpermitirJSONnull.

8testes distintos/3suítes aprovados emrodadas identificadas:5ManualInvoiceSettlementAPI,2GetBillingControlunit,1OpenAPI. Financeiro8passaram em1m15s;OpenAPIpós-ajustefinal1passou56s. Não somar reexecuções. Checkstyle main/test ebootJar aprovados. Casos:rawOPEN comcaixa30/100,estornoparcial/total,retido100 ecrédito100 semcashrecebido,cancelado/renegociadocomfilhorecebido,202mantendoOPEN,RECV.READausente=null,false;originalausente;403BILLING.READ/404outrotenant;posiçãoalémde100títulos. Provas emtmp/validation-billing-financial-status/final-financial-results e resultadosOpenAPIfinais;manifestounificado emtmp/billing-financial-status-candidate/validation-summary.json. Não executada toda asuíte.

As falhasiniciais foram fixturesdecrédito semtransação e reinserçãodepermissão desativada, corrigidasnos testes. O contratoSpringdoc necessitou admitirnull tambémnoenum(contraintenemenumcomtypeunion);adaptadorlocalcopiaenumimutávelantesdeincluirnull e testeexecutávelverificaJSONnull,com/v3/api-docs200. Não foram afrouxadas regrasdeaplicação.

OpenAPIexecutável3.1.0 `postman/canteiro-io.openapi.json` SHA256 **0DE96AE583EB332C4E4C345424BEF97B464B86E93156710BFAE3F31B4BDE2E9D**,148paths/257schemas. Somentepropriedadesnovas,required e descriçãorawstatus emBillingAllocationControlResponse;rotas/HTTP/outrosschemasidênticos. Enumda novaposição incluiJSONnull comtipostring|null.

Plano deativaçãoautorizada apósconferência: confirmarbaselineAF20D2D2/schema91checksum-1539369050;preservarcontainerAF20 compatível;imagemsócopiaoJARvalidado;substituirsomenteAPI8081preservandoenv/rede/volume/porta/restart,Flyway/seedsfalse. Verificarstartup,hashativo,OpenAPI200idênticoaocompartilhadoe401anônimo. Sem reprocessamentoreal,login ourecebimentocomoteste. Emfalha,retornarAF20 compatívelsemalterarbanco.
