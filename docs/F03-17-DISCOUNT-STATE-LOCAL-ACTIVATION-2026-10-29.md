# FRONT-F06-14 / F03-17 — Ativação do indicador de desconto contratual

**29/10/2026 às18:44:52 America/Sao_Paulo (21:44:52UTC), somente API local8081. Concluído.**

Pedido humano confirmado diretamente no chatFase8: “Contratos que nunca dei descontos aparecem escrito, reverter desconto [...] Precisamos arrumar isso”. Não foram consultados registros reais do exemplo. Pacote/contrato conferidos pelo frontend antes da ativação, sob autorização local existente da seção14.1AGENTS; semnova migration ou operaçãofinanceira.

[Guia](FRONTEND-CONTRACT-DISCOUNT-STATE.md). GET `/api/v1/company/contracts/{contractId}` agora retorna hasActiveContractDiscount booleanoficial; true por existência de ContractDiscount ativo mesmo com efeitozero; falso semativo/revertido. Ajustemediçãopositivosozinho não implica true. Null emlistagem/mutações significa nãoavaliado. MesmaCONTRACTS.READ/tenant e404; nãoexpõe novosvalores/tipos financeiros nem concede alçada de reversão. 202nãoaltera estadooficial. Front integra===true/consulta semerro e autorização decancelamento; serviços usam tipo e valor cadastrado, não efeito calculado.

## Validação e pacote

4testes/2suítes aprovados na rodadafinal1m15s:3WorkAndContractApiIntegrationTest e1OpenApiContractIntegrationTest. Cenários semativo/proposta202,ativo percentualbasezero, reversãopendente202mantendoativo,basepositiva,reversãodiretafalse,listagemnull; ajuste10.00noagregado comindicadorfalse,403semREAD/404outrotenant e regressõesobras/contratos. Primeira rodada tinha2falhas emfixtures/permissões/atores; corrigidos os testes, sem relaxar aplicação. Checkstyle main/test e bootJar aprovados. Não foi executada toda asuíte do repositório.

JARativo **AF20D2D23FE3DE2C32B4D96FEC7E4BC6CC0F38C4D4AEFE3A8019D5BA5787747C**, `tmp/contract-discount-state-candidate/canteiro.io-contract-discount-state.jar`.
Comparação aoCF376B05:2classesalteradas(ContractController/ContractResponse),2adicionadas(GetContractDetailsUseCase/Details),zero removidas/inesperadas. Bibliotecas/configuração/migrationsV1–V91 e relatórioPDF/ledger idênticos. ImagemcopiasomenteJARtestado,semrecompilarárvore durantepublicação.

## Runtime e proteção

Imagem `canteiroio-api:local-contract-discount-state-20261029`, ID `sha256:960c816493ed047860632c66774b353713f67a6b39951fa46c3c6cd3f60cbf74`.
Container `3f520cfba39b853fc2dc4e79c0343c7de07bbc8c698341df93ab2f49cdf2dc6f`.
Inicialização11.3s,21:44:52.649UTC. Hashativo conferido apósstartup.

Ambienteidêntico,volumeprivadocanteiroio-api-private-storage em/app/var/storage,redecanteiroio_default,8081→8080,restartunless-stopped preservados. SPRING_FLYWAY_ENABLED/BOOTSTRAP_PLATFORM_OWNER_ENABLED/BOOTSTRAP_TRUST_UNLOCK_DEMO_ENABLED continuamfalse. Schema91/checksum-1539369050/zero falhas inalterados. Semmigration,seeds,login,criação/reversão de desconto real,relatório real ou outro lançamento financeiro peloagente.

CF376B05preservadoparado em `canteiro-api-before-contract-discount-state-20261029`, compatívelV91/snapshots e relatório documental para retorno operacional. Não apagar/restaurarbanco para retorno.

## OpenAPI e frontend

Export `postman/canteiro-io.openapi.json`, SHA256 **24BE6633792CEA5DBC5DCAA4322255111F20327965843B58212C56A2499D0625**,OpenAPI3.1.0,148paths/257schemas; só propriedade/required deContractResponse mudaram,semalterarrotas/HTTPdemais schemas. Contrato vivo200application/json com147paths/257schemas:todoscompartilhados/componentesidênticosaoexport,únicaausênciaesperadawebhooklocaldesabilitado.

GETanônimo `/api/v1/company/contracts/00000000-0000-0000-0000-000000000001`:401application/problem+json,UNAUTHENTICATED,correlationId presente.

Frontend confirmou hash/tiposgerados/conferência decontrato,34testes/3arquivos,lint/buildfinal eE2E3/3 em3.3s naslarguras360/768/1280/1600, semBILLING.READ/HTTPfinanceiro/mutação,semoverflow/erro de página. Esses resultados pertencem àconversa dofrontend; nãoforam reexecutados pelo backend.

Provas `tmp/contract-discount-state-candidate/{package-comparison.json,validation-summary.json,baseline-runtime.json,publication-runtime.json,startup.txt,live-openapi.json,live-contract-evidence.json}` e resultados emtmp/validation-contract-discount-state/build/test-results/test. Frontend recebeu confirmação da ativação para fechamento.
