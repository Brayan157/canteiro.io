# Controle de faturamento — grade documental por contrato

Correção humana de29/10/2026 substitui a definição anterior de saldo histórico. **Publicado na API local8081 em29/10/2026 às14:49:55SãoPaulo, JARCF376B05/V91 após testes e conferência do frontend.** [Ativação verificada](F07-04-DOCUMENTARY-LOCAL-ACTIVATION-2026-10-29.md).

## Rota e autorização

`GET /api/v1/company/billing-control/contracts/{contractId}/report/pdf`

Bearer, tenant da sessão e BILLING.READ+EXPORT / INVOICES.READ+EXPORT. PDF inline, filename UTF-8 sanitizado, no-store e streaming. 400/401/403/404/409 Problem Details com correlationId antes do início do corpo. Origem documental inválida:409 BILLING_REPORT_RECONCILIATION_REQUIRED. Sem URL pública ou companyId livre.

RECEIVABLES.READ+EXPORT adicionais habilitam somente a seção separada de cobranças abertas. As três partes documentais principais não dependem dessas permissões financeiras nem usam caixa recebido.

## Estrutura solicitada

1. Cabeçalho: controle de faturamento, identificação do contrato, data, empresa emitente/logo privada com fallback, cliente e obra.
2. Grade: **Serviço (nome e descrição) | Número do contrato | Valor do serviço | 1ª parcela | 2ª parcela ... | Saldo total**. Sem coluna extra Total NF serviço. Identificação do contrato vem do cadastro, não é inferida do nome.
3. Resumo em três colunas: **Valor total do contrato | Valor total faturado | Saldo total**. Não exibir recebido em dinheiro no resumo principal.
4. Grade de NFs: **Parcela/referência | Número NF | Data emissão | Valor da NF | Saldo histórico**. Canceladas são identificadas, sem consumir acumulado.
5. Cobranças em aberto, separadamente e apenas com permissão: inclui títulos semNF, cobrado, recebido e saldo oficial pendente após retenções/créditos/baixas. Não soma cobrança e NF como receitas distintas.

## Fórmulas documentais oficiais

As parcelas das colunas representam a sequência das NFs registradas, com os valores oficiais de InvoiceItem atribuídos exatamente ao serviço/alocação/contrato/tenant. Não são parcelas semNF; estas permanecem na seção de cobranças. Ordem: issuedOn crescente e, em empate, UUID da NF crescente. Mesma sequência na matriz e na gradeNF. NFs com várias referências de cobrança contam uma só vez; referências adicionais aparecem sem repetir valores/saldo.

Valor comercial atribuído do serviço: compromisso líquido + capacidade líquida não comprometida segundo distribuição F05-21 existente; preserva desconto consumido e origens de composição. Saldo do serviço = valor comercial atribuído − todos os itens de NFs registradas do serviço. Sem unir nomes iguais; serviços ainda semNF permanecem. Origens/ajustes legados não conciliáveis recebem indisponibilidade explícita, sem rateio inventado.

Resumo: líquido contratual oficial; total das NFs REGISTERED reconciliadas; diferença entre eles. Saldo histórico documental da NF = **líquido contratual oficial na base deste relatório − soma acumulada das NFs REGISTERED até aquela linha**, por emissão/ID. Não usa caixa, recebíveis ou snapshot de registro daV91. Não reconstrói o valor contratual de uma data antiga: aditivos posteriores podem mudar a base. O PDF contém nota breve explicando a base atual. NFs canceladas não entram no acumulado e exibem saldo não aplicável. Valores assinados não são artificialmente limitados a zero.

Exemplos humanos: contrato1000; tubo500/suporte500; NF1 tubo100/suporte0; NF2 tubo0/suporte250 → saldos400/250 e resumo1000/350/650. Separadamente: contrato1000, NFs500/250/250 → saldo porNF500/250/0.

## Preservação e consistência

V91 já aplicada e snapshots de caixa existentes são **preservados integralmente**, inclusive captura em novos registros/aprovações. Não apagar, reescrever, reutilizar ou rotular esse snapshot como saldo documental. Esta correção não exige nova migration e não muda NF, título, recebimento, aprovação ou fórmulas de faturamento. 202 não altera dado oficial.

Cache temporário PDFBox, janelas100serviços×3NFs, histórico100referências porconsulta, descrições longas sem truncar e cabeçalhos repetidos. Transação REPEATABLE_READ no callback, tenant/segurança propagados e revalidados, auditoria mínima EXPORT após geração. Nenhuma soma/rateio oficial no frontend. Blob e contratoHTTP preservados; OpenAPI deve ser gerado do código corrigido.

## Estado

42testes distintos/3suítes aprovados,8reexecuções focadas pós-ajuste visual, Checkstyle e bootJar aprovados. Quatro páginas finais sintéticas conferidas, candidato revisto pelo frontend e ativado somente em8081; sem migrations/seeds/operações financeiras reais. OpenAPI9831765A... publicado/validado contra a instância200;401anônimo confirmado. [Candidato e provas](F07-04-DOCUMENTARY-CANDIDATE.md). [Publicação anterior preservada](F07-04-REVISION-LOCAL-ACTIVATION-2026-10-29.md).
