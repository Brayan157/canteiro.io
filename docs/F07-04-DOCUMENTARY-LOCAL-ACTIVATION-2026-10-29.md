# F07-04 — Ativação da grade documental corrigida

**29/10/2026 às14:49:55 America/Sao_Paulo (17:49:55UTC), somente API localhost:8081. Concluído.**

Decisão humana confirmada diretamente no chatFase8, mensagem01a188bf-44d6-7352-930b-11f429694709: grade serviço/número do contrato/valor/parcelas/saldo; resumo contratado/faturado/saldo; NFcomsaldo acumulado1000−500−250−250→500/250/0. Esta definição posterior prevalece à interpretação anterior de caixa. Autorização permanente de atualização local da seção14.1 doAGENTS aplicada ao mesmo pedido; frontend conferiu candidato e confirmou layout antes da troca. Sem nova pergunta ou migration.

## Evidência de implementação e validação

[Guia atualizado](FRONTEND-CONTRACT-BILLING-PDF.md). [Candidato/plano](F07-04-DOCUMENTARY-CANDIDATE.md).

42testes distintos/3suítes aprovados emuma rodada:33ManualInvoiceSettlementApiIntegrationTest,8PdfBoxContractBillingPdfGeneratorTest,1OpenApiContractIntegrationTest. Após ajuste visual de espaçamento/alinhamento,8testes do gerador repassaram, Checkstyle main/test e bootJar aprovados32s; não somar reexecuções. Teste explícito lê oPDF e verifica saldos500/250/0. Quatro páginas finais sintéticas renderizadas e conferidas, comlogo/fallback. Nota apósNF não sobrepõe a tabela. Fonte do buildisolado `tmp/validation-billing-documentary/build`; provas completas `final-api-results` e resultados focados finais.

JAR: `tmp/billing-documentary-candidate/canteiro.io-billing-documentary.jar`.
SHA256 **CF376B05F4D96B24B75DE96277E11AF7BC76A704BCDE1C0AD7E7EFE5151460EE**.
Comparação ao386004E6anterior: somente5classes alteradas, nenhuma adicionada/removida/inesperada. Bibliotecas, configuração e migrationsV1–V91 idênticos. Domínio/persistência/captura dos snapshotsV91 não foram alterados; eles permanecem imutáveis, sem serem utilizados ou rotulados como saldo documental.

## Ativação e runtime

Imagem `canteiroio-api:local-billing-documentary-20261029`, ID `sha256:82398bec740f4ad7c70918129d3c1235570f4109a32d2aa2fbd180f52e0e9db7`.
Container `ccbfac231128ff510edf39d0eab5e1fc9fbf2ff439b77adb7f2ffb42bf4d4ecb`.

Baseline386004E6/a55290a3 conferido antes da substituição; imagem nova contém exatamente oJAR revisado. Não recompilou a árvore na publicação: copiou somente oartefato aprovado. Inicialização15.916s;17:49:55.025UTC.

Ambiente idêntico, redecanteiroio_default,volumeprivadocanteiroio-api-private-storage em/app/var/storage,porta8081→8080 e restartunless-stopped preservados. Flyway automático e seedsowner/demo continuamfalse. Schema **91**,checksum**-1539369050**,zero migrationscomfalha,nenhuma migrationexecutada nesta ativação. Sem alterações de banco, logins, exportação de cliente real, criaçãoNF, recebimento,estorno ou lançamento financeiroreal peloagente.

Container386004E6preservado **parado** como `canteiro-api-before-billing-documentary-20261029`; é compatível comV91/captura de snapshots para recuperação operacional, embora seu relatório não atenda à decisão mais recente. Não apagar ou restaurar banco para esse retorno.

## OpenAPI e segurança

Export `postman/canteiro-io.openapi.json` SHA256 **9831765A8401026AD8701174D97862DFE79192ABFC3082B450EF0E7114E176CB**.

Contrato vivo `GET /v3/api-docs` **200 application/json**,OpenAPI3.1.0,147paths/257schemas. Todos os paths compartilhados e componentes idênticos aoexport148paths; únicaausência esperada éwebhookdesabilitado localmente. Descrição da rotaPDF reflete grade/saldo documentais; HTTP/schema preservados.

GETanônimo `/api/v1/company/billing-control/contracts/00000000-0000-0000-0000-000000000001/report/pdf`: **401 application/problem+json**,UNAUTHENTICATED,correlationId presente. Sem uso de conta/relatório real como teste.

Provas `tmp/billing-documentary-candidate/{package-comparison.json,baseline-runtime.json,publication-runtime.json,startup.txt,live-contract-evidence.json,live-openapi.json,validation-summary.json}`. Frontend recebe esta confirmação para avisar usuário a baixar novamente pela mesma rota.
