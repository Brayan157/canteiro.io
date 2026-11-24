# F07-04 — Correção documental solicitada em29/10/2026

**Candidato conferido pelo frontend e ativado em8081 às14:49:55SãoPaulo, JARCF376B05/V91.** [Registro de ativação](F07-04-DOCUMENTARY-LOCAL-ACTIVATION-2026-10-29.md). Texto do plano anterior preservado como histórico. Esta correção não muda banco, migraçõesV1–V91, snapshots financeiros, NF, recebíveis ou domínio de faturamento.

A decisão humana posterior, confirmada diretamente no chatFase8, substitui saldo de caixa na apresentação: grade Serviço/descrição | Número do contrato | Valor | parcelas sequenciais | Saldo; resumo contratado/faturado/saldo; gradeNF parcela/referência | número | emissão | valor | saldo documental acumulado por emissão/ID na base contratual atual. Caixa somente em cobranças abertas adicionais autorizadas. SnapshotsV91 preservados e não usados como saldoNF.

Validação final da regra:42testes/3suítes emuma rodada (33API,8gerador,1OpenAPI), todos aprovados; inclui cenário1000−500−250−250→500/250/0. Ajuste visual posterior restrito a espaçamento apósNF/alinhamento de valores está em verificação focada no gerador. Não somar reexecuções. Checkstyle main/test em fechamento. Provas em tmp/validation-billing-documentary/final-api-results; preservada rodada intermediária41semfalhas.

[Amostra com logo](../output/pdf/billing-control-synthetic-logo.pdf) e [fallback](../output/pdf/billing-control-synthetic-fallback.pdf), inteiramente sintéticas. [Guia](FRONTEND-CONTRACT-BILLING-PDF.md). Pacote/manifesto em tmp/billing-documentary-candidate. Conferir hashes e versão ativa novamente antes da substituição.

Publicação prevista após conferência: preservarcontainer386004E6, substituindosomenteAPI8081 peloJARvalidado; semrecompilarárvore, semmigration/seeds/operaçõesfinanceirasreais. Manterambiente/rede/volume/porta/restart. CompararOpenAPIvivoe401anônimo depoisda readiness. O386004E6 é compatível comV91/snapshots e pode ser retomado caso haja falha operacional nesta troca; nenhumretorno de banco ou exclusão de histórico é necessário. Retorno não significa que seu relatório antigo atenda à decisão nova.

Fechamento:8testes do gerador pós-ajuste visual aprovados32s, Checkstyle main/test e bootJar aprovados;42distintos/3suítes considerados semsomar reexecuções. Candidato SHA256 `CF376B05F4D96B24B75DE96277E11AF7BC76A704BCDE1C0AD7E7EFE5151460EE`; somente5classesalteradas emrelaçãoao386004E6, zero entradasadicionadas/removidas/inesperadas, bibliotecas/configuração/migrations idênticos. ExportOpenAPI SHA256 `9831765A8401026AD8701174D97862DFE79192ABFC3082B450EF0E7114E176CB`.
