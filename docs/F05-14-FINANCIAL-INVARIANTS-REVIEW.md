# F05-14 — revisão da cadeia financeira

Data: 14/10/2026. Escopo: API backend; composição aceita → contrato → alocações → NF → recebível → baixa/estorno. Não cria faturamento próprio da medição e não altera fórmulas financeiras.

## Resultado e limites operacionais

A composição é persistida no `ContractService.measurementCompositionAmount`; `effectiveNetAmount` preserva a base negociada e soma composições vigentes. O cálculo contratual usa essa posição efetiva, antes do ajuste separado de desconto do contrato. `APPLIED` não é apenas uma indicação visual.

Os testes consultam os GETs oficiais de serviços e controle de faturamento, além dos repositórios. Cobrem `CREATE_NEW_SERVICE`, `ADD_TO_EXISTING_SERVICE`, vínculo tardio e os modos `FIXED`/`MEASUREMENT_BASED`. Aceite sem destino continua sem composição: não é uma falha de cálculo nem autoriza selecionar destino por nome.

Nenhum destino, valor ou lançamento foi aplicado manualmente à medição real `da573be2-fd62-4302-ab64-7322a1a3ca01` ou ao contrato `b6c84258-57fb-4ce3-9033-a2c387cad5b3`. Esta revisão não cria migration. Após autorização explícita do usuário, a API local foi reconstruída/reiniciada, e o export foi obtido de seu `/v3/api-docs`, não copiado de um contrato antigo.

## Falhas demonstradas e correções

1. **Dinheiro em número JSON.** Respostas legadas de alocação, resumo paginado, NF/item, recebível/saldo, baixa e imposto usavam `BigDecimal` com serialização numérica padrão. Agora usam serialização explícita em string com duas casas, sem ponto flutuante, notação científica ou arredondamento silencioso. O Java continua usando `BigDecimal`. O OpenAPI declara `string`, `format: decimal`, exemplo e padrão canônico.
2. **Faturamento de serviço cancelado.** A validação podia aceitar uma alocação de serviço cancelado quando outro serviço deixava capacidade no contrato. A ativação agora rejeita esse caso com conflito, sem gravar parcela/título/alocação.
3. **Redução de serviço já faturado.** A edição/desconto verificava somente o teto global do contrato. Agora verifica também o comprometimento aprovado do próprio serviço. Outro serviço não pode encobrir esse excesso. A edição comum não pode cancelar serviço com composições aceitas vigentes: a origem exige reversão formal.
4. **Desconto negativo sem lastro.** Reduzir ou reverter o desconto contratual podia deixar alocações negativas ativas sem desconto oficial correspondente. A operação agora exige lastro em desconto suficiente; antes de removê-lo, é necessário cancelar/estornar as alocações dependentes. A mesma validação vale na aprovação e no caso de uso legado.
5. **Snapshot de alocação obsoleto na NF.** A criação direta podia reutilizar alocações validadas antes de adquirir o lock contratual. A NF agora resolve situação, cadeia e capacidade depois do lock, como a aprovação já fazia. Uma alocação cancelada enquanto a operação espera não é admitida.
6. **Leitura JPA anterior ao lock.** O teste concorrente demonstrou que repetir a validação depois do lock não atualiza uma entidade já carregada no contexto de persistência. A criação/aprovação de parcela agora lê serviços/origens somente após o lock, e edição/desconto/reversão direta ou aprovada também bloqueiam o contrato antes de ler o serviço. O teste concorrente é repetido três vezes; o unitário de criação verifica que nenhuma leitura de serviço aconteceu antes do bloqueio.
7. **Base sem origem encoberta por composição.** Um serviço de base `100.00` e composição `100.00`, com faturamento sem origem `100.00`, ainda passava pelo teto efetivo quando sua base era reduzida para `50.00`. Agora a base líquida também precisa cobrir suas próprias alocações sem origem. Composição aceita não é usada silenciosamente para financiar faturamento antes atribuído à base.

Alterações comerciais diretas/aprovadas de serviço compartilham o lock contratual versionado com composição/faturamento, antes das leituras usadas na decisão financeira. Solicitações `202` de edição não adquirem esse lock de escrita, não incrementam a versão financeira do contrato e não alteram valores oficiais. Aprovar uma proposta antiga revalida os compromissos atuais; conflito não aplica parcialmente a proposta.

## Evidências executáveis

| Invariante | Testes principais |
|---|---|
| Base preservada, destinos explícitos, `APPLIED` persistido e GETs coerentes nos dois modos | `MeasurementContinuousApiIntegrationTest.persistsBothCommercialDestinationsAndTheCompleteDiscountInvoiceReceiptReversalChain` |
| Próximo avanço usa o mesmo destino e só aplica o incremento | `keepsAcceptedItemsInTheMainBodyAcrossEmptyAndPopulatedAdvancesAndComposesOnlyNewValue` |
| Vínculo tardio, repetição e composição idempotente | `linksAnAcceptedStandaloneMeasurementAndPreservesItsDestination`, `serializesConcurrentCompositionAndOriginBillingWithoutDuplicatingValue` |
| Aprovação de serviço independente da medição; pendência não antecipa valores | `separatesAcceptanceFromServiceApprovalAndReusesThePendingOriginRequest`, `keepsAnExistingDestinationCommerciallyUnchangedUntilItsOwnServiceApproval` |
| Serviço não pode ficar abaixo de sua própria alocação; aprovação revalida | `cannotReduceABilledServiceBelowItsOwnCommitmentEvenWhenTheContractHasSpareCapacity`, `revalidatesServiceReductionOnApprovalWithoutAnticipatingAnyOfficialEffect` |
| Composição não encobre excesso de faturamento sem origem sobre a base | `cannotUseAcceptedCompositionToHideAUntracedBillingCommitmentAboveTheNegotiatedBase` |
| Preço/faturamento concorrentes não excedem capacidade | `serializesServicePriceReductionAndBillingAgainstTheSameContractCapacity`, `BillingPersistenceIntegrationTest.blocksOneOfTwoConcurrentReservationsThatWouldExceedTheContractBalance` |
| Desconto separado, negativo e com lastro oficial | `cannotRemoveContractDiscountBackingWhileItsNegativeBillingAllocationIsActive`, `SetContractDiscountUseCaseTest`, `ValidateContractBillingLimitUseCaseTest` |
| NF parcial referencia a alocação e não consome saldo contratual novamente | Teste integrado completo; `InvoiceRegistrationServiceTest`, `BillingPersistenceIntegrationTest.blocksOneOfTwoConcurrentInvoiceItemsThatWouldExceedTheSameAllocation` |
| NF não usa alocação cancelada enquanto aguardava o lock | `InvoiceRegistrationServiceTest.rejectsAnAllocationCancelledBeforeTheContractLockIsAcquired` |
| Baixa parcial e estorno mantêm origem e centavos, sem duplicar caixa | Teste integrado completo; `attributesTwoOriginsAndWithholdingAfterPartialReceiptWithoutLosingACent`, `preservesFinancialOriginAcrossCreditRenegotiationPartialReceiptAndReversals` |
| Correções preservam aceitos; redução faturada exige reversão | `reconcilesCorrectiveCopiesAndVoidWithoutRecountingTheFrozenOriginal`, `preventsAcceptedReductionUntilTheOriginalBillingIsCancelled` |
| Tenant, permissões, concorrência otimista, histórico seguro | `isolatesTenantAndFinancialPermissionAndRequiresAnOptimisticDestinationVersion`, testes negativos de `BillingPersistenceIntegrationTest` |
| Runtime JSON decimal exato e OpenAPI com status reais | `FinancialResponseSerializationTest`, `OpenApiContractIntegrationTest` |

No cenário integrado: base `100.00` + duas origens aceitas de `100.00` formam subtotal `300.00`. O ajuste contratual `60.00` resulta em líquido `240.00`. Duas NFs de `40.00` e `60.00` sobre uma alocação não mudam o faturado `240.00`. Uma baixa de `120.00` é atribuída em `40.00` para cada uma das três origens de serviço, sem entregar o recebimento inteiro às duas medições. Estorno reabre a dívida; cancelamento das NFs e parcela devolve a capacidade contratual, sem apagar o valor aceito ou o histórico.

## Reprodução

Usar Java 21 e Docker/Testcontainers. O build está fora do diretório sincronizado pelo OneDrive. Exemplo PowerShell, na raiz do backend:

```powershell
$env:JAVA_HOME = 'C:\Users\oqdel\.jdks\ms-21.0.12'
$env:OPENAPI_EXPORT_PATH = "$PWD\postman\canteiro-io.openapi.json"
.\gradlew.bat test `
  --tests 'com.renovar.canteiro.io.receivables.*' `
  --tests 'com.renovar.canteiro.io.contracts.*' `
  --tests 'com.renovar.canteiro.io.measurements.*' `
  --tests 'com.renovar.canteiro.io.OpenApiContractIntegrationTest' `
  --tests 'com.renovar.canteiro.io.FlywayIntegrationTest' `
  --fail-fast checkstyleMain checkstyleTest --no-daemon
```

Resultado final em 14/10/2026: **184 testes, 51 suítes, zero falhas, erros ou testes ignorados**; `checkstyleMain` e `checkstyleTest` sem violações. A execução completa terminou com `BUILD SUCCESSFUL`. O cenário concorrente de preço/faturamento foi aprovado nas três repetições.

`GET /v3/api-docs` respondeu `200 application/json` na aplicação de teste; o contrato de teste contém 123 paths e 192 schemas, com SHA-256 `07C80A04D9A190A5E47524A36A9C85C85DA3FA7665FDB8DE57CB6D7C07EDCBC5`.

**Publicação local concluída e autorizada em 14/10/2026:** o container `canteiro-api` atende `localhost:8081` com a imagem `sha256:938650aa96ad1389124f864658bfbce24482254cd2a950ab3b18a0afba7e4470`. O `/v3/api-docs` publicado retornou `200 application/json`; os 15 campos monetários revisados são `string/decimal`, e as cinco operações de criação/estorno verificadas contêm `201`, `202`, `400`, `403`, `404` e `409`.

O export atual de `postman/canteiro-io.openapi.json` vem da instância publicada: OpenAPI 3.1, 122 paths e 192 schemas; SHA-256 `6A025C9C48D96A728F75439C592A1969AE7A0554A6833AC07A97B867A62B27F9`. Perfis/rotas condicionais e URL do servidor podem diferir do contrato de teste; o artefato publicado é a referência para os tipos do frontend.

Flyway validou as 83 migrations e informou schema atualizado, sem migration necessária. PostgreSQL, Mailpit e volumes foram preservados. Nenhuma composição ou alteração comercial manual foi executada sobre os dados reais. Nenhuma tela do frontend foi alterada pelo backend.

## Integração do frontend

- Gerar os tipos do export atualizado; não declarar dinheiro manualmente como `number`.
- `GET /contracts/{contractId}/services`: exibir `netAmount` (base), `measurementCompositionAmount` e `effectiveNetAmount`, sem somá-los no cliente.
- `GET /billing-control/contracts/{contractId}`: usar `serviceSubtotal`, `contractDiscountAmount`, `contractNetAmount` e saldos oficiais.
- Após composição/aceite/vínculo/aprovação, invalidar serviços, controle contratual, visão acumulada e resumo financeiro. Em `200` do coordenador, verificar cada estado; só `APPLIED` significa incremento oficial aplicado.
- `202` não remove item, não altera preço, não incorpora origem e não atualiza saldo. Recarregar após aprovação.
- Não permitir a interface resolver conflitos financeiros alterando valores locais. Exibir `409 Problem Details` e atualizar a posição oficial.
- Rotas e projeções: [FRONTEND-API-ENDPOINTS.md](FRONTEND-API-ENDPOINTS.md) e [FRONTEND-MEASUREMENT-CONTINUOUS.md](FRONTEND-MEASUREMENT-CONTINUOUS.md).
