# Proposta de preenchimento do recebimento total — 29/10/2026

**Complemento posterior autorizado em29/10:** a retenção compartilhada pode ser distribuída proporcionalmente entre InvoiceItems exatos da NF, com líquidos oficiais e centavos reconciliados. A recusa anterior de rateio de retenção abaixo é histórica e foi substituída somente nesse escopo; parciais e caixa histórico permanecem manuais/inalterados. Complemento testado e publicado somente em8081 às23:42:22SãoPaulo; [registro](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md). [Contrato atual por item](FRONTEND-INVOICE-ITEM-WITHHOLDING.md).

Recorte somente de leitura, aditivo ao GET existente `GET /api/v1/company/receivables/{receivableId}/invoice-settlement-options?page=&size=`. Implementado e validado no código. **Publicado somente na API8081 em29/10/2026 após nova autorização humana específica, sem migrations ou operações financeiras reais.** [Provas, hashes e retorno](F05-37-LOCAL-ACTIVATION-2026-10-29.md).

## Origem e contrato

O usuário definiu que a distribuição deve partir dos serviços e valores já escolhidos na cobrança. A API preserva `billingAllocationId`, NF, item de NF e composição aceita; nunca usa nome do serviço como chave. Itens de NF são a cobertura documental dessas alocações. Recebimentos exatos anteriores são descontados somente de suas próprias origens; não são redistribuídos.

O GET acrescenta `totalSettlementProposal`, independente da página de opções exibida:

```json
{
  "available": true,
  "unavailableReason": null,
  "amount": "100.00",
  "items": [
    {
      "invoiceId": "00000000-0000-0000-0000-000000000001",
      "invoiceItemId": "00000000-0000-0000-0000-000000000002",
      "billingAllocationId": "00000000-0000-0000-0000-000000000003",
      "measurementContractCompositionId": null,
      "amount": "100.00"
    }
  ]
}
```

Dinheiro é string decimal canônica com duas casas. Schemas: `TotalInvoiceSettlementProposalResponse` e `TotalInvoiceSettlementProposalItem`. Este objeto **não é ChangeRequest nem recebimento**, não reserva saldo e não gera auditoria de mutação. Continua exigindo RECEIVABLES.READ + INVOICES.READ e tenant da sessão; não concede permissão de criação. Cache-Control no-store.

Quando indisponível: `available=false`, `amount=null`, `items=[]`, motivo explícito. `eligible=true` do título não garante proposta automática disponível. O limite de consulta da proposta é100 origens documentadas, mesmo que algumas já tenham sido recebidas; acima disso, manter operação manual e paginação existente.

| Motivo adicional | Comportamento |
| --- | --- |
| `SETTLEMENT_TOTAL_DISTRIBUTION_REQUIRED` | Há mais de uma distribuição possível; não escolher proporção ou ordem |
| `SETTLEMENT_TOTAL_INVOICE_COVERAGE_REQUIRED` | Cobertura documental elegível não comporta o saldo total da cobrança |
| `SETTLEMENT_TOTAL_TOO_MANY_ITEMS` | Mais de100 origens documentadas; proposta automática não é produzida |

Motivos já existentes de título, histórico ou origem não aceita também podem impedir a proposta. Não transformar ausência/indisponibilidade em zero.

## Quando os valores são determinados

- Cobrança60/40, NF cobrindo60/40 e nenhum recebimento: proposta60/40, total100.
- Recebimento anterior30/20 sobre essas origens: proposta residual30/20, total50, sem alterar o histórico.
- Duas NFs, cada uma com uma única origem desta cobrança,60 e40; retenção10 na primeira: limites conjuntos determinam50/40, total90. Não houve escolha de rateio entre serviços de uma NF.
- Uma NF com serviços60/40 e retenção10: total90 pode ser60/30 ou50/40, entre outras divisões. A API retorna indisponibilidade, sem sugestão parcial ou arbitrária.
- Cobrança sem NF suficiente: não usar alocações ainda sem cobertura para fabricar linhas de NF.

O backend considera conjuntamente capacidades residuais dos itens, saldo líquido de cada NF (incluindo dinheiro de outros títulos dessa NF) e saldo da cobrança. Só propõe uma solução única. Nunca soma `maximumLineAmount`, rateia retenção proporcionalmente, escolhe FIFO ou atribui todo o caixa de um contrato a um serviço.

**Única decisão adicional para o exemplo ambíguo:** qual valor líquido recebido pertence a cada serviço após a retenção? O usuário ainda não definiu uma política de distribuição dessa diferença. A solução atual conserva o preenchimento manual; parciais continuam manuais, mesmo quando a proposta do total estiver disponível.

## Integração

Com proposta disponível, preencher total e linhas oficiais, permitir conferência e exigir confirmação explícita. Não enviar automaticamente ao abrir a cobrança. Reutilizar o POST `/invoice-settlements` existente, com operationId, data, meio e campos adicionais normais; enviar as linhas exatas confirmadas. O POST e a aprovação revalidam tudo sob locks; a proposta não garante saldo futuro. `201/200/202`, idempotência e tratamento de resposta inconclusiva permanecem iguais. Não mudar a intenção sob o mesmo operationId.

Se a API ativa ainda não retornar o novo campo, conservar a operação manual. Não criar DTO manual, recalcular a proposta, somar páginas ou apresentar prévia local como oficial. A publicação posterior exige autorização específica; ativação anterior da F05-32 não cobre este novo código. POST legado permanece bloqueado.

## Validação e export

22 testes distintos/2suítes aprovados em3m42s:21 de ManualInvoiceSettlementApiIntegrationTest e1 contratual OpenAPI, sem falhas/erros/ignorados; Checkstyle main/test aprovado. Quatro cenários novos cobrem proposta completa independente da página/nenhuma escrita/aplicação das linhas pelo POST existente, resíduo após recebimento parcial com composição preservada, retenção unívoca por NF versus ambígua entre serviços e cobertura documental ausente. Os demais18 são reexecuções do fluxo manual/contrato, incluindo permissões, tenant, concorrência, aprovação202 e histórico. Todas as operações de teste ocorreram em PostgreSQL temporário, não no banco real.

Export executável gerado pelo teste contratual e copiado mecanicamente para `postman/canteiro-io.openapi.json`: OpenAPI3.1.0,147paths/257schemas, SHA-256 `7BF8E2214BE0046868B431A48F60B5021857DC0E61CAA88F2EDBD8852F07C3E8`. Fonte dos tipos novos; não significa publicação. Snapshot da API publicada27037B87... preservado em `tmp/validation-invoice-total-proposal/canteiro.openapi.published-27037B87.json`. Resultados XML/Checkstyle em `tmp/validation-invoice-total-proposal/build`. JAR candidato B971C83C... preservado após bootJar17s e comparação integral com F05-32, sem mudanças de bibliotecas/configuração/migrations. [Pacote, hashes e plano de retorno compatível](F05-37-CANDIDATE-AND-ROLLBACK.md). Posteriormente publicado somente em8081 sob autorização específica; imagem por cópia do pacote, sem migration. [Registro](F05-37-LOCAL-ACTIVATION-2026-10-29.md).
