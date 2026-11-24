# Retenção proporcional por item de NF e recebimento total

Decisão humana de 29/10/2026: **“Sim, distribuir proporcionalmente entre os serviços”**. Publicado somente na API local8081 em29/10/2026 às23:42:22SãoPaulo, após testes e conferência do frontend. [Ativação e provas](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md). Complementa F05-37 e substitui apenas a recusa anterior de distribuição da retenção compartilhada. Recebimentos parciais continuam manuais.

## Contrato

`GET /api/v1/company/receivables/{receivableId}/invoice-settlement-options?page=&size=` mantém autenticação, tenant, RECEIVABLES.READ + INVOICES.READ, paginação e `Cache-Control: no-store`.

Cada opção acrescenta três campos obrigatoriamente presentes, nullable, `string` decimal canônica de duas casas:

- `withheldItemAmount`: parcela do imposto registrado WITHHELD_BY_CUSTOMER atribuída ao InvoiceItem.
- `netInvoicedItemAmount`: valor bruto oficial do item menos essa parcela do imposto.
- `netAvailableItemAmount`: líquido do item menos dinheiro confirmado já atribuído exatamente a ele, incluindo estornos.

`invoicedItemAmount` continua bruto. `itemReceivedAmount` continua dinheiro efetivamente registrado. `maximumLineAmount` aplica, sobre o saldo líquido do item, os limites compartilhados da NF e do título. Os tetos individuais continuam não aditivos. Sem retenção, a parcela de imposto é `"0.00"` quando a origem for comprovada; indisponibilidade não vira zero.

`totalSettlementProposal` usa as capacidades líquidas residuais e não desconta o imposto novamente. A proposta independe da página, exige conferência e confirmação humana e não reserva nem grava nada. O frontend usa os valores oficiais, sem rateio, soma de páginas ou cálculo financeiro próprio. POST e aprovação revalidam todos os limites e a data UTC sob os locks existentes. `201/200/202`, operationId e vínculos exatos são preservados. Parcial continua recebendo as linhas explicitamente informadas pelo usuário.

## Regra oficial

Pesos são os valores brutos dos **InvoiceItems exatos da mesma NF**, nunca nomes ou preços atuais dos serviços. Usa `ProportionalMoneyAllocation`: ordenação lexicográfica de UUID, truncamento para centavos e distribuição do resíduo em ordem inversa, limitada à capacidade. Soma das parcelas de imposto fecha exatamente com a retenção registrada. Não há divisão entre títulos/cobranças diferentes.

Exemplo: NF de1000, itens100/900, retido100 → imposto10/90 e líquido90/810. Recebimento total900 mantém imposto100 separado do dinheiro. No caso sintético de itens87777.78/702222.22 e retenção5000, a soma líquida é785000.00.

Somente a NF integralmente comprovada no escopo de um título original pode ter retenção distribuída: itens positivos registrados, alocações ativas, mesma empresa/contrato/parcela, total dos itens reconciliado com a NF e todas as retenções vinculadas ao mesmo título. Não inferir vínculos fiscais ou históricos.

## Indisponibilidade segura

Os novos campos e `maximumLineAmount` ficam null quando a base/histórico não for comprovável. A proposta total fica `available=false`, `amount=null`, `items=[]`. Uma nova gravação recebe409 Problem Details com o mesmo motivo controlado quando aplicável:

| Motivo | Significado |
| --- | --- |
| SETTLEMENT_WITHHOLDING_SCOPE_REQUIRES_RECONCILIATION | Retenção ou itens fora do mesmo título original; não distribuir entre cobranças |
| SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION | Base incompleta, itens legados incompatíveis ou total divergente |
| SETTLEMENT_WITHHOLDING_HISTORY_REQUIRES_RECONCILIATION | Dinheiro histórico de algum item excede seu novo líquido proporcional |
| SETTLEMENT_WITHHOLDING_TOO_MANY_ITEMS | NF com retenção tem mais de100 itens; leitura da base limitada a101 por NF |

Os motivos anteriores de permissão, título, origem não aceita, crédito, renegociação e histórico continuam vigentes. Não reinterpretar, apagar ou redistribuir baixas antigas. Estorno legítimo continua usando exatamente as linhas originais; não orientar estorno fictício de dinheiro recebido apenas para adequar o rateio.

## Rótulo da cobrança

A indicação **“Recebida — com retenção de imposto”** já publicada permanece separada: quitação por dinheiro e retenção, sem crédito. Não transforma imposto em dinheiro nem altera o enum financeiro. [Contrato do rótulo](FRONTEND-BILLING-WITHHOLDING-LABEL.md).

## Entrega

Sem migration, seed, reprocessamento ou operação financeira real. Testes usam PostgreSQL temporário. O export executável e o candidato foram conferidos pelo frontend antes da atualização local autorizada pela seção14.1 de AGENTS.md.

Candidato validado:50 testes/4suítes, Checkstyle main/test e bootJar aprovados; export49F89DB0775210BFE9FF56D89487AC589C68CC8E37B9B1B6142D836791D27DDE/148paths/257schemas. JARE97C650C comparado ao ativoE09835F5, somente receivables; migrations/libs/config iguais. [Pacote e retorno](F05-37-WITHHOLDING-ITEMS-CANDIDATE.md). Ativado e verificado em8081; detalhes no registro de ativação.

Frontend confirmou integração e validação final:63testes focados,4E2E em4larguras, inspeção visual, lint/typecheck/build/scanner aprovados. Preenchimento100/900 comretido100 gera90/810,total900 semPOST automático; API viva e contrato conferidos. Sem pendência neste recorte. [Provas conjuntas](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md).
