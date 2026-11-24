# Planejamento — cobrança por serviço e tributo por base/alíquota

**Registro histórico substituído:** o usuário confirmou base inicial primeiro e depois medições por data oficial de aceite; alíquota até quatro casas. A implementação e sua validação estão no [guia efetivo F05-27/F05-28](FRONTEND-SERVICE-BILLING-AND-TAX.md). As opções/perguntas abaixo registram o planejamento anterior, não decisões atuais nem contrato para gerar tipos.

26/10/2026. **Planejamento, não contrato executável.** Recortes autorizados: FRONT-F08-19/F05-27 e FRONT-F08-20/F05-28. Não criar tipos manuais nem usar este documento como comprovação de rota/campo implementado. O último OpenAPI validado permanece com SHA-256 `53F7EAC14CD2F644BCD35035A6E6670D18839FC6364AE8C01B75912E83F91FC9` (137 paths/234 schemas).

## F05-27 — cobrança por serviço

Experiência autorizada: selecionar serviço, ver disponível líquido oficial e escolher todo disponível ou valor parcial. Base inicial e medições são composição informativa, não escolhas técnicas obrigatórias.

Evidências: `CreateNetBillingRequest`/`CreateNetBillingService` recebem alocações exatas; omitir `measurementContractCompositionId` significa **somente base negociada**. `NetBillingCapacityService.State.availableForService` já soma capacidades elegíveis no servidor, mas não é contrato agrupado/completo nem comando por serviço. F05-21 distribui **desconto**, não define o consumo de uma cobrança parcial.

Entrega prevista após decisão: disponibilidade agrupada a partir de todas as origens, com cobertura/completude, bloqueios, moeda e versão; resolução oficial de todo/parcial em origens exatas, sem soma/rateio no frontend; intenção e resolução idempotentes sob lock/versão; alçada, `202` sem reserva, revalidação na aprovação e `409`; preservação do legado e da cadeia alocação → NF → título → atribuição imutável de baixa/estorno. Manter BILLING.READ + MEASUREMENTS.READ até autorização distinta. Não ampliar um “todo disponível” silenciosamente na aprovação porque surgiram novos avanços.

**Decisão humana pendente:** distribuição do parcial entre múltiplas origens. Opções ainda não aprovadas:

| Política | Base disponível 600.00 + medição disponível 400.00; cobrar 500.00 |
|---|---|
| Proporcional ao líquido elegível | Base 300.00; medição 200.00. Confirmar fechamento determinístico de centavos. |
| Base primeiro; depois medições por aceite | Base 500.00; medição 0.00. Definir desempate estável entre medições. |
| Medições por aceite primeiro; depois base | Base 100.00; medição 400.00. Definir desempate estável entre medições. |

O valor cobrado é igual, mas o financeiro da medição/origem do recebimento muda. Não escolher política automaticamente nem delegá-la ao navegador. Testar capacidades completas, desconto livre, todo/parcial, origens, replay/aprovação/concorrência/tenant/histórico e exportar somente contratos implementados.

## F05-28 — tributo por valor ou base × alíquota

Autorizado: valor explícito **ou** base e alíquota informadas pelo usuário, cálculo oficial no backend e conferência antes da gravação. Não inferir base do total da NF nem escolher incidência, regime, percentual, isenção, tratamento ou vencimento. Não é motor fiscal.

Hoje `CreateTaxObligationRequest` aceita somente `amount`; domínio/banco não guardam base/alíquota. `TaxObligation.positiveAmount` e ADR 0003 já usam **HALF_UP em duas casas monetárias**. Não alterar essa regra.

Entrega prevista: entradas mutuamente exclusivas, decimais canônicos/BigDecimal; cálculo `base × alíquota / 100`, sem arredondamento intermediário prematuro; conferência oficial sem tributo/Payable/retenção antecipada e revalidação na confirmação/aprovação; rastreabilidade de modo/base/alíquota/resultado por migration aditiva, sem percentuais fictícios no legado; idempotência por intenção, nunca por nome/valor presumido. Preservar tenant, vínculos/saldo, `PAYABLE_BY_COMPANY`, `WITHHELD_BY_CUSTOMER`, `INFORMATIONAL`, aprovação/202 e histórico. Não duplicar tributo/pagamento/recebimento nem registrar conteúdo fiscal sensível em logs. Testar os três tratamentos, cálculo/arredondamento, divergência, tenant, replay/aprovação/concorrência e OpenAPI.

**Decisão humana pendente:** precisão aceita/persistida para alíquota, inexistente no contrato atual. Proposta a confirmar: positiva com até quatro casas, sem arredondar a entrada; base monetária com duas casas e resultado com duas casas HALF_UP já vigente. Não selecionar percentual/faixa fiscal automaticamente nem alterar as regras de valor positivo ou capacidade de retenção.

## Estado e limites

Escopo e decisões registrados; nenhuma implementação, migration nova, rota/DTO ou export fictício publicado. Nenhum deploy, início/reinício de API/Docker, infraestrutura, push ou operação financeira real. Implementação das partes dependentes aguarda as decisões humanas acima.
