# F05-32 — Recebimento manual com origem exata na NF

**Complemento posterior autorizado em29/10:** a retenção compartilhada pode ser distribuída proporcionalmente entre InvoiceItems exatos da NF, com líquidos oficiais e centavos reconciliados. A recusa anterior de rateio de retenção abaixo é histórica e foi substituída somente nesse escopo; parciais e caixa histórico permanecem manuais/inalterados. Complemento testado e publicado somente em8081 às23:42:22SãoPaulo; [registro](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md). [Contrato atual por item](FRONTEND-INVOICE-ITEM-WITHHOLDING.md).

Estado em 29/10/2026: **V90 aplicada e pacote final publicado somente na API local8081**, após autorização humana específica. JAR0E25A12A... ativo, OpenAPI vivo200, componentes/caminhos compartilhados idênticos ao export27037B87..., seeds/Flyway automático desligados. Frontend pode habilitar a configuração local autorizada após conferir [as provas de ativação](F05-32-LOCAL-ACTIVATION-2026-10-29.md). O fluxo genérico antigo continua bloqueado; nenhum recebimento real foi executado nesta publicação.

## Rotas implementadas no código

Complemento F05-37 de29/10: o export atual de desenvolvimento passou a7BF8E221.../257schemas para incluir `totalSettlementProposal`, **publicado somente em8081 em29/10 após nova autorização específica, sem migrations**. [Provas atuais](F05-37-LOCAL-ACTIVATION-2026-10-29.md). As provas/hashes27037B87... abaixo identificam o release F05-32 já ativo. [Contrato aditivo validado e fallback manual](FRONTEND-TOTAL-INVOICE-SETTLEMENT-PROPOSAL.md).

- `GET /api/v1/company/receivables/{receivableId}/invoice-settlement-options?page=0&size=20`: `InvoiceSettlementOptionsResponse`; exige RECEIVABLES.READ e INVOICES.READ. Ordem fixa por emissão da NF, invoiceId, invoiceItemId, crescente. page>=0, size1..100. A página interna `items` tem items/page/size/totalItems/totalPages. Sem agrupamento por nome e sem soma local de páginas.
- `POST /api/v1/company/receivables/{receivableId}/invoice-settlements`: `CreateInvoiceSettlementRequest`; exige alçada CREATE de RECEIVABLES e INVOICES.READ. `201` novo recebimento, `200` repetição idempotente, `202` somente proposta. Erros 400/401/403/404/409 em Problem Details/correlationId.
- Estorno mantém `POST /api/v1/company/receivables/{receivableId}/settlements/{settlementId}/reversal`, com as permissões/alçada existentes; copia somente os vínculos exatos do original em linhas negativas, sem reatribuir dinheiro.
- Lista/detalhe de NF preservam `receiptPosition` e passam a preenchê-la somente quando comprovável pelo ledger exato; sem RECEIVABLES.READ, valores continuam indisponíveis. Histórico desconhecido, crédito/renegociação sem vínculo suficiente não vira zero fictício.

`receiptPosition.status` acrescenta `UNPAID`, `PARTIALLY_RECEIVED`, `RECEIVED` e `SETTLED_BY_ADJUSTMENTS`, mantendo `UNAVAILABLE`. Este último estado continua com amounts null; `SETTLED_BY_ADJUSTMENTS` significa saldo encerrado somente por ajuste/retenção, sem afirmar dinheiro recebido. O novo motivo `HISTORY_REQUIRES_RECONCILIATION` cobre histórico/origem sem prova suficiente; suportar motivos controlados desconhecidos sem liberar o comando.

Fontes: `InvoiceSettlementController`, `CreateInvoiceSettlementRequest`, `InvoiceSettlementOptionsResponse`, `InvoiceSettlementOptionResponse`, `InvoiceSettlementResponse` e `InvoiceSettlementLineResponse`, em `src/main/java/com/renovar/canteiro/io/receivables/api`; regra em `InvoiceSettlementService`, persistência aditiva em `V90__preserve_manual_invoice_item_receipt_links.sql`.

## Request manual

```json
{
  "operationId": "00000000-0000-0000-0000-000000000001",
  "receivedOn": "2026-10-28",
  "amount": "75.00",
  "paymentMethod": "PIX",
  "note": null,
  "justification": null,
  "items": [
    {
      "invoiceId": "00000000-0000-0000-0000-000000000002",
      "invoiceItemId": "00000000-0000-0000-0000-000000000003",
      "billingAllocationId": "00000000-0000-0000-0000-000000000004",
      "measurementContractCompositionId": null,
      "amount": "75.00"
    }
  ]
}
```

IDs e valores ilustrativos. Utilizar exatamente os IDs retornados nas opções, incluindo composição quando existir. Entre 1 e 100 linhas; nenhum invoiceItemId repetido. Dinheiro como string decimal positiva de duas casas, no máximo 17 dígitos inteiros; número JSON é rejeitado. A soma das linhas deve igualar amount. Data efetiva não futura segundo calendário UTC do servidor e não anterior à emissão do título; repetir validação na aprovação. Não inferir horário ou movimentar dinheiro em banco.

`note` e `justification` são opcionais, com no máximo1000 caracteres cada. O limite final de note foi alinhado ao VARCHAR(1000) existente;1000 aceitos e1001 rejeitados com400 foram verificados. Não enviar dados bancários/fiscais desnecessários nessas observações.

## Limites compartilhados e ausência de rateio

As opções publicam `outstandingAmount` do título e, por linha, `invoicedItemAmount`, `itemReceivedAmount`, `invoiceNetAmount`, `invoiceReceivedAmount`, `eligible`, `maximumLineAmount` e `unavailableReason`. O máximo individual é limitado simultaneamente pelo item, NF e título; **máximos de várias linhas se sobrepõem e não podem ser somados**. O POST revalida a soma por NF e o total por título na mesma transação. Não recalcular saldo oficial no cliente.

Retenção já registrada aponta para uma NF/título: limita o líquido oficial dessa NF e desse título. Não há divisão de retenção entre itens nem distribuição de pagamento. Cada quantia por item é informada manualmente. Créditos sem vínculo suficiente à NF, renegociação e recebimentos legados sem atribuição comprovável exigem conciliação e permanecem bloqueados; não fabricar vínculos pelo valor ou por serviço compartilhado.

`options.eligible` trata a elegibilidade do título; cada opção tem sua própria elegibilidade, razão e capacidade. Opção indisponível não pode ser enviada. Uma página vazia não autoriza usar um item fora do contrato/tenant ou reutilizar dado de outra página/empresa. Todas as referências devem pertencer à cadeia do título autenticado. Uma NF mantém exatamente um contrato.

## Aprovação e repetição segura

`202` responde somente `changeRequestId`, `mode=REQUEST_APPROVAL` e `replayed`; não devolve recebimento/itens/totais fictícios nem reserva capacidade. Não remover ou atualizar posição financeira oficial por causa desse resultado. Repetir o mesmo operationId com conteúdo equivalente devolve o mesmo pedido pendente, sem novo pedido. Uma proposta decidida/rejeitada exige nova intenção; uma aprovada retorna seu recebimento existente.

Resultado direto inclui `settlement`, `items` com vínculos oficiais, `currency`, `confirmedSettlementAmount`, `outstandingAmount`, `mode` e `replayed`. Idempotência é por tenant: mesmo operationId e conteúdo devolvem o mesmo recebimento; conteúdo diferente retorna409. Em resultado inconclusivo de transporte, preservar a intenção e os valores: não gerar operationId novo automaticamente. Repetir a mesma intenção é seguro após confirmação humana/releitura apropriada; não distribuir valores novamente ou alterar linhas silenciosamente.

Invalidar/reler recebível, saldo, histórico de baixas, opções do título, NFs relacionadas e, conforme a tela/permissão, controle de faturamento/medição. Estorno confirmado deve reler as mesmas consultas. `balance.settlementCreationAvailable=false` continua descrevendo **o comando legado `/settlements`**, e não deve habilitá-lo. O novo formulário depende das opções e usa exclusivamente `/invoice-settlements` após ativação autorizada da nova versão.

O GET histórico existente continua retornando cabeçalhos de baixas/estornos e seus vínculos original/reversal; não foi expandido com payload livre, observações bancárias ou lista de linhas da NF. Os vínculos exatos ficam persistidos e aparecem no resultado direto/idempotente do novo comando, e a posição oficial da NF é consultada nos seus GETs. Não atribuir sucesso de um comando inconclusivo por coincidência de valor/data no histórico.

## Limites desta entrega

Sem substituição fiscal externa, NF com múltiplos contratos, estorno fictício ou novo rateio. Cancelamento da NF continua sujeito à guarda F05-36, inclusive bloqueios conservadores por título compartilhado. Migração aditiva validada em PostgreSQL descartável e aplicada somente no banco local em29/10 após autorização específica, sem backfill ou mudança histórica. API8081 ativa no pacote final; habilitação local do frontend autorizada e coordenada pela conversa responsável. Ver provas atuais no registro de ativação.

## Validação e export executável

Rodada ampla: **89 testes distintos em11 suítes**, sem falhas/erros/ignorados; checkstyleMain/checkstyleTest aprovados, BUILD SUCCESSFUL4m48s. Após revisão da cadeia item/serviço e alinhamento do limite note1000, a validação final focada repetiu19 casos/3suítes (17API manual,1UTC na aprovação,1OpenAPI), todos aprovados, com Checkstyle/bootJar e BUILD SUCCESSFUL2m16s. Esses19 e o primeiro lote de56 são reexecuções/subconjuntos, não novos casos somados aos89. A primeira reprodução detectou conversão incorreta de Instant no novo mapper e um nome de tabela errado na fixture; corrigidos antes da rodada válida. Não são testes autenticados com dados reais.

| Suíte | Casos |
| --- | ---: |
| ManualInvoiceSettlementApiIntegrationTest | 17 |
| InvoiceTaxReadApiIntegrationTest | 25 |
| InvoiceReadApiIntegrationTest | 12 |
| BillingReceiptsAndSettlementDateApiIntegrationTest | 12 |
| ReceivableSettlementHistoryApiIntegrationTest | 10 |
| ReceivableSettlementServiceTest | 6 |
| FinancialCancellationServiceTest | 3 |
| InvoiceSettlementServiceTest | 1 |
| ReceivableSettlementCreationPolicyTest | 1 |
| FlywayMigrationIntegrationTest | 1 |
| OpenApiContractIntegrationTest | 1 |

Inclui: duas empresas/negativas de permissão; soma exata/string decimal/dataUTC; itens duplicados/limite100/paginação; recebimento parcial/total e mesma alocação em duas NFs; retenção existente sem divisão de dinheiro; composição aceita e seu estorno exato; idempotência direta/202; ausência de mutação oficial de versões/datas em202; proibição de autoaprovação; revalidação após consumo de saldo/cancelamento da NF; concorrência sem excesso; proteção contra update/delete no ledger; bloqueio de crédito/renegociação/legado sem prova; manutenção da contenção genérica. Teste unitário verifica data futura na aprovação antes de qualquer lock/escrita.

Relatórios: `tmp/validation-invoice-manual-settlement/build`. OpenAPI gerado de `/v3/api-docs` no contexto executável, depois copiado sem edição manual para `postman/canteiro-io.openapi.json`: versão3.1.0,147paths/255schemas, SHA-256 `27037B8762036EA7F6B695D5D6A966F92BB73C602F31DBB8055DD8764833B607`. Snapshot anterior976... preservado em `tmp/invoice-manual-settlement-candidate/canteiro.openapi.before-v90.json`. Desde29/10 a instância8081 serve o release final: caminhos compartilhados e componentes idênticos ao export, com a única ausência prevista do webhook local desabilitado.

Resultados amplos89/11 preservados em `tmp/invoice-manual-settlement-candidate/validation-89-test-results`; a última rodada19/3 está em `validation-final-19-test-results` nessa mesma pasta e no diretório build. O snapshot inicial978FE33B... foi substituído pelo final27037B87..., somente após validação executável, e preservado separadamente. A correção note2000→1000 não modificou a V90.

BootJar final aprovado, JAR preservado `0E25A12A80175D955C17383E1859A8CFB1754A51700B7CD77864275537D576ED`; comparação integral com F05-36 confirmou apenas o recorte e a V90, sem dependências/configuração/migrations anteriores alteradas. O candidato inicial C81618AF... não deve ser publicado. [Artefato, hashes, plano de aplicação V90 e limites do retorno](F05-32-CANDIDATE-AND-MIGRATION-PLAN.md). Não reutilizar o helper anterior que espera schemaV89; autorização de troca da API sem migrations não autoriza este release.
