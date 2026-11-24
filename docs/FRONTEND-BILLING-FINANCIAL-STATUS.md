# Situação financeira oficial no detalhe do faturamento

29/10/2026. **Publicado na API local8081 em29/10/2026 às19:22:27SãoPaulo**, JAR4A2D4980/V91 após conferência dofrontend. [Ativação verificada](F05-12-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-10-29.md). Recorte F05-12/F05-33, sem migration, mudança de saldo ou reprocessamento de registros reais.

## Contrato combinado

`GET /api/v1/company/billing-control/contracts/{contractId}` mantém BILLING.READ e tenant da sessão. Cada item de allocations recebe:

- `receivableFinancialStatusAvailable: boolean` obrigatório.
- `receivableFinancialStatus: "OPEN" | "PARTIALLY_RECEIVED" | "RECEIVED" | "SETTLED_WITH_ADJUSTMENTS" | "CANCELLED" | "RENEGOTIATED" | null`, obrigatório e explicitamente nullable.

`installmentStatus` continua o **status persistido** da parcela, sem alteração de semântica/histórico. Pode permanecer OPEN após recebimento; não usá-lo como posição financeira atual.

A nova posição exige **RECEIVABLES.READ adicional a BILLING.READ** e título original existente no mesmo contrato/empresa. Sem permissão ou sem esse título: available=false e status=null, nunca OPEN/RECEIVED/zero inventado. Não retorna valores financeiros novos nem dados pessoais adicionais. 403 semBILLING.READ e404 decontratoausente/foraempresa continuam Problem Details.

## Fonte e interpretação

Reutiliza a projeção oficial `ReceivableReadProjection.financialPosition`, que usa `ReceivableFinancialPosition.from` e os agregados existentes: baixasCONFIRMED líquidas de estornos, retenções registradas e créditos líquidos de reversões. Não consulta NF para inferir recebimento, não usa datas para inferir quitação e não há cálculo oficial no frontend.

| Status | Leitura humana |
| --- | --- |
| OPEN | Em aberto, saldo positivo sem caixa confirmado líquido |
| PARTIALLY_RECEIVED | Parcialmente recebida, com saldo positivo |
| RECEIVED | Recebida em dinheiro, conforme posição oficial |
| SETTLED_WITH_ADJUSTMENTS | Encerrada com ajustes; não afirmar recebimento integral em dinheiro |
| CANCELLED | Título original cancelado |
| RENEGOTIATED | Título original renegociado; sucessores têm posições próprias |

A posição é do **título original da cobrança/parcela** (`renegotiationSourceReceivableId=null`), não da alocação ou serviço isolado. Várias linhas da mesma parcela repetem essa situação; não apresentá-la como prova de pagamento individual de serviço. Um sucessor recebido não transforma o originalRENEGOTIATED emRECEIVED. Não soma títulos renegociados nem altera dados históricos.

## Consumo frontend

Usar o novoenum apenas quando available=true, RECEIVABLES.READ efetiva e estado conhecido. Sem posição: “Situação financeira indisponível”, mantendo “Situação registrada: …” separadamente. Nunca mapear ausência paraOPEN ou encerradoporajustes paraRECEIVED. 202mantém posiçãooficial atéaplicação; baixas/estornos diretos exigem refetch/invalidação jáexistente. Não criar chamadas porlinha nem somaslocais.

## Consulta e entrega

Backend consulta a projeção de recebíveis por contrato/tenant em páginas100, dentro do mesmo REPEATABLE_READ do controle, e mapeia posições porUUID da parcela. Não adiciona consultas poralocação, nem as executa semRECEIVABLES.READ. Não altera os N+1 legados de outras partes do controle fora deste recorte.

Testes aprovados:rawOPEN comcaixaparcial/integral e estorno, retenção/crédito semfalsoRECEIVED, cancelado/renegociado comsucessor recebido,202, ausência de título/permissão e tenant. ExportOpenAPI/pacote conferidos pelo frontend e ativados; contrato vivo200/enumJSONnull e401anônimo verificados,sem migration. Nenhuma operaçãofinanceirareal é necessária para verificação.

Fechamento:8testesdistintos/3suítes aprovados emrodadas,Checkstyle/bootJar;export0DE96AE5...148paths/257schemas eJAR4A2D4980...preservados/comparadosaoAF20D2D2,4classesalteradas/1adicionada,libs/config/migrationsidênticos. [Candidato e provas](F05-12-FINANCIAL-STATUS-CANDIDATE.md).


## Correção de apresentação por imposto retido —29/10/2026

Publicado em8081 em29/10/2026 às20:01:42SãoPaulo,JARE09835F5: receivableReceivedWithWithholding:boolean|null permite “Recebida — com retenção de imposto” somente com posiçãooficialencerrada,dinheiropositivo,retido positivo ecrédito zero. Enum/caixa/saldos não mudam; créditos/mistos e100%retençãosemdinheirocontinuam distintos. [Contrato específico](FRONTEND-BILLING-WITHHOLDING-LABEL.md).
