# Cobrança recebida com retenção de imposto

29/10/2026. Pedido humano: “mas ai é imposto kkkkk, tem que colocar recebido”. **Correção publicada na API local8081 em29/10/2026 às20:01:42SãoPaulo**,JARE09835F5/V91apósconferênciafrontend. [Ativação](F05-12-WITHHOLDING-LABEL-LOCAL-ACTIVATION-2026-10-29.md). Não redefine dinheiro recebido, statusfinanceiro, filtros, snapshots, retenções ou crédito.

## Campo aditivo

Em `GET /api/v1/company/billing-control/contracts/{contractId}`, cada allocation recebe `receivableReceivedWithWithholding: boolean | null`, explicitamente presente e requirednullable noOpenAPI.

Fonte: a mesma posição financeira oficial do título ORIGINAL já carregada sobRECEIVABLES.READ+BILLING.READ. Não consulta novamente o banco, não soma valores nem recalcula saldo; conserva posição inteira internamente para derivar indicação restrita.

- true somente para SETTLED_WITH_ADJUSTMENTS, saldooficialzero, baixasCONFIRMED líquidas deestornos positivas, retenção positiva e créditooficialzero.
- false para demais posições disponíveis:aberto/parcial/RECEIVEDsemretenção,crédito ouajustemisto,100%retido semdinheiro,cancelado,renegociado ouestornado.
- null sempermissão ousem títulooriginal; não apresentarfalse fictício. Disponibilidade e contexto de título original permanecem osmesmos doscamposfinanceiros existentes.

## Frontend

Somente comREAD efetiva,receivableFinancialStatusAvailable=true,statusoficialSETTLED_WITH_ADJUSTMENTS eflag===true, apresentar **“Recebida — com retenção de imposto”**. Isso é indicação de quitação por dinheiro+retenção, não altera oenum técnico nem afirmaque imposto écash.

Exemplo:cobrança1000,impostoretido100,dinheiro900,saldo0 → “Recebida — com retenção de imposto”; dinheiro recebido continua900. Casocomcrédito/misto,retençãointegralsemcaixa ouindícioausente/desconhecido não recebe esse rótulo. Manter apresentação anterior deajustes/indisponibilidade quando aplicável. Não alterar outro módulo/filtro nesta tarefa ou realizarconsultasporlinha.

Dadosde202não mudam posição/indício oficiais atéaplicação. Estorno reabre/atualiza posição e pode retirar a indicação. RawinstallmentStatus/histórico permanecem intocados; campo se refere ao título original e se repete emlinhas da mesma parcela,sem prova de pagamentoindividualdeserviço.

## Entrega

Semmigration/seeds/reprocessamentoreal. Testes decontrolefinanceiro/autorização/estornos/ajustes ecasosespecíficos emexecução,exporte pacoteselecionado serão enviados para conferência antes daativação local jáautorizada.

9testes/3suítes,Checkstyle/bootJar aprovados. Export0C36E2214E12EDA914132C17EA94546F2657B4E8031DEE9409A6F8C964A87DF1,JARE09835F5 comparado4A2D4980somente4classesalteradas,migrations/libs/configiguais. [Candidato](F05-12-WITHHOLDING-LABEL-CANDIDATE.md).
