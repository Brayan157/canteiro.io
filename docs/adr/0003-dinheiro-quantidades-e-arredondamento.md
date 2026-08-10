# ADR 0003 — Dinheiro, quantidades e arredondamento

- Estado: Aceito
- Data: 2026-08-10

## Contexto

Contratos, medições, descontos, faturamento e resultado de obra exigem cálculos
financeiros reproduzíveis. `double` e `float` introduzem erros binários.

## Decisão

1. Valores monetários usam `BigDecimal` e PostgreSQL `numeric(19,2)` em BRL.
2. Quantidades técnicas (kg, metros, m² e fatores de medição) usam `BigDecimal`
   e `numeric(19,4)`.
3. Cálculos intermediários preservam escala suficiente para as quantidades e só
   são monetariamente arredondados no valor persistido, exibido ou usado como
   lançamento financeiro.
4. O arredondamento monetário é `RoundingMode.HALF_UP` para duas casas decimais.
5. Valores recebidos pela API são convertidos e validados explicitamente; nunca
   são calculados com `double`/`float` nem com construtor `new BigDecimal(double)`.
6. A soma de valores já persistidos é a fonte de verdade para saldo, faturamento
   e relatórios; valores agregados não são mantidos como saldo editável.

## Consequências

Fórmulas de medição e descontos devem ter testes com casos de fração e de
arredondamento. Uma futura mudança de moeda, escala ou regra de arredondamento
exige nova ADR e autorização explícita.
