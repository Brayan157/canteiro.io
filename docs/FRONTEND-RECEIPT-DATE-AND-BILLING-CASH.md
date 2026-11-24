# Recebimentos: data oficial, caixa no faturamento e contenção temporária

Recorte F05-29/F05-30/F05-31 iniciado em 26/10/2026, validado e publicado somente em `http://localhost:8081` em 27/10/2026. Sem nova migration neste recorte. O fluxo manual completo por NF/item permanece pendente e novos recebimentos estão temporariamente bloqueados.

## Novas baixas temporariamente bloqueadas

Por decisão humana, **todos os novos recebimentos estão temporariamente bloqueados**, mesmo quando já houver NF. O bloqueio REST evita que o caminho legado registre dinheiro sem rastreabilidade documental exata; não é a implementação completa do vínculo manual por NF/item.

`POST /api/v1/company/receivables/{receivableId}/settlements` mantém autenticação, tenant e alçada. Para uma nova baixa estruturalmente válida e autorizada, retorna `409 application/problem+json` com `code: "BUSINESS_RULE_VIOLATION"`, `reason: "SETTLEMENT_INVOICE_LINK_REQUIRED"` e `correlationId`. A regra vale para aplicação direta e solicitação; não cria proposta `202`, lançamento, reserva, atribuição ou auditoria de sucesso. Propostas antigas também são revalidadas e bloqueadas ao aprovar, permanecendo pendentes sem efeito oficial.

Sem autenticação/permissão ou com título fora do tenant, permanecem `401`/`403`/`404`. Campos inválidos, inclusive data futura, podem retornar `400` antes da contenção. Não contornar o bloqueio com outra NF, outro endpoint, outra origem ou repetição. Não há configuração de runtime para liberar o caminho inseguro.

Consultas, histórico e estornos autorizados continuam disponíveis. Estorno de erro registrado não é devolução bancária; não orientar estorno fictício de recebimento verdadeiro para substituir uma NF. Registrar ou cancelar NF não registra recebimento nem apaga a cobrança. Cancelamento de cobrança indevida segue as regras existentes.

## Data do recebimento: calendário UTC

`receivedOn` representa a data real do recebimento, não vencimento ou agendamento. Uma nova baixa não pode usar data posterior ao dia atual **UTC**, obtido do `Clock` oficial do backend, inclusive na aprovação e na aplicação transacional. Retorna `400 VALIDATION_FAILED`, com `violations: [{"field":"receivedOn","message":"..."}]` e `correlationId`.

O `GET /api/v1/company/receivables/{receivableId}/balance`, protegido por `RECEIVABLES.READ`, acrescenta:

```json
{
  "maxReceivedOn": "2026-10-26",
  "settlementCreationAvailable": false,
  "settlementCreationBlockReason": "SETTLEMENT_INVOICE_LINK_REQUIRED"
}
```

A data acima é exemplo; a resposta traz o dia oficial no instante da consulta. Usar `maxReceivedOn` como `max` do campo de data, preservando `YYYY-MM-DD` sem transformar em timestamp, aplicar timezone do navegador ou deduzir fuso de assinatura/webhook. Reconsultar quando necessário na virada do dia; a API sempre revalida. Sem resposta oficial, uma orientação local conservadora pode usar o calendário UTC, nunca liberar a operação contra `settlementCreationAvailable=false`. O limite de data não prova elegibilidade nem reserva saldo. Esses metadados também integram o schema de saldo reutilizado pelas respostas existentes.

Baixas históricas, inclusive datas futuras anteriormente gravadas, não são apagadas, corrigidas, reclassificadas ou excluídas automaticamente dos totais por esta entrega. Inconsistências exigem conciliação própria.

## Recebido oficial no controle de faturamento

Rotas existentes, sem mudança dos filtros/paginação/tenant:

```http
GET /api/v1/company/billing-control/contracts
GET /api/v1/company/billing-control/contracts/{contractId}
```

`BillingControlSummaryResponse` (cada item da página) e `ContractBillingControlResponse` acrescentam:

```json
{
  "currency": "BRL",
  "receivedAmountAvailable": true,
  "receivedAmount": "4000.00"
}
```

`BILLING.READ` continua necessário. O agregado de caixa exige adicionalmente `RECEIVABLES.READ` efetivo na empresa da sessão. Sem essa permissão, o restante do controle continua disponível, mas `receivedAmountAvailable=false` e `receivedAmount=null`, nunca um zero fictício. Sem registros de caixa e com autorização, retorna `true`/`"0.00"`.

`receivedAmount` é a soma oficial, no banco, dos lançamentos `CONFIRMED` assinados de recebimento e estorno do contrato. Não conta NF, retenção, crédito, valor nominal de títulos descendentes ou proposta `202` como dinheiro. Inclui as baixas históricas dos títulos renegociados e seus descendentes uma única vez por lançamento; registros cancelados não entram. Nunca recalcular somando páginas/títulos/versões no frontend. A moeda é a do contrato, sem câmbio ou total misturando moedas. Este campo é somente o agregado contratual: não representa recebido por serviço/NF nem elegibilidade para nova baixa.

Consultas usam snapshot transacional consistente, tenant/cadeia coerentes e agregado somente dos contratos da página; sem permissão não consultam o ledger de recebimentos. Não persistem atraso ou qualquer efeito financeiro/auditoria de alteração.

## Integração e próximo recorte

Gerar tipos do OpenAPI executável/exportado, incluindo `ProblemDetail.reason`/`violations`, os três metadados de saldo e os dois campos de faturamento. A UI deve mostrar o recebido oficial quando disponível e explicar indisponibilidade/bloqueio, sem cálculo financeiro local ou permissões inferidas.

O fluxo completo posterior terá valores manuais por NF/InvoiceItem/alocação/origem exatos, sem rateio automático entre itens, serviços ou NFs. Migration aditiva e testes em banco temporário foram autorizados separadamente, mas **não estão incluídos nesta publicação de contenção**; não aplicar no banco real nem apresentar contrato fictício de elegibilidade. Conciliação de histórico e substituição de NF já recebida continuam decisões/fluxos próprios. Retenção do cliente e imposto pago pela empresa permanecem fatos distintos.

## Evidências

**85 testes distintos em oito suítes aprovados**, mais **24 reexecuções focadas em seis suítes** em 27/10/2026 após a correção de nulabilidade; zero falhas/erros/ignorados nos lotes finais. Checkstyle main/test sem erros, `bootJar` e `git diff --check` aprovados. PostgreSQL/Testcontainers usado apenas com dados sintéticos/bancos temporários.

Os testes operacionais usam a política real, sem mock, e comprovam bloqueio com NF presente/ausente, alçada direta/solicitação/aprovação pendente, autenticação/permissão/tenant, ausência de efeitos oficiais, erro de data futura e limite UTC na virada do dia. Cobrem também caixa líquido de estornos, retenção/crédito/NF excluídos de dinheiro, histórico/renegociação, nulidade sem permissão, precisão alta e export executável. As suítes preexistentes de histórico e medição usam mock explícito da política **somente para gerar fixtures do fluxo legado** e testar sua atribuição; não são evidência de disponibilidade atual do POST. Estorno histórico é testado com a política real e permanece autorizado.

Provas locais ignoradas pelo Git: `tmp/validation-receipt-date-control/regression-85` e `tmp/validation-receipt-date-control/final-24` (XMLs de testes e Checkstyle).

Snapshot definitivo: [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado de `/v3/api-docs` da aplicação de teste: **OpenAPI 3.1.0, 142 paths e 243 schemas**. `settlementCreationBlockReason` é `type: [string, null]`, sem enum restritivo que exclua JSON null; o motivo atual em runtime continua controlado. Não houve edição manual do JSON.

```text
SHA-256 export: 49B3C55C267AA1A921995BF9DF7EF28E10C5075519E2FCA22D1BB491A90BEA04
SHA-256 JAR:    3798D6B22CEC578A6C9F592005D40E7DB563E4FBD23FA3A654E1AFCD4A0BD5BA
```

Publicação local autorizada concluída em **27/10/2026 às 09:58:28 America/Sao_Paulo** (`2026-10-27T12:58:28.769243977Z`). Container `canteiro-api`, imagem `canteiroio-api:local-receipt-guard-20261027`; hash do JAR interno idêntico ao build testado e `Started Application` confirmado, sem falha de startup. `/v3/api-docs` vivo retorna `200` com os novos campos e os GETs de NF preservados. SHA-256 do documento vivo: `4FD7933AEA3754651E81F96993A687AEAB8267A295DE8DBD0194E9F3B0CF056A`. Comparação estrutural confirma contrato igual ao export, desconsiderando **somente `servers`**, que contém a URL da instância.

`SPRING_FLYWAY_ENABLED=false` e bootstraps de proprietário/demo desabilitados na instância local; catálogo local preexistente foi conferido por leitura. Histórico real antes/depois: 89 entradas, versão máxima V89, todas com sucesso; nenhuma migration executada e nenhuma V90 criada neste recorte. PostgreSQL/Mailpit não foram reiniciados, volume `canteiroio-api-private-storage` preservado. Container anterior parado e recuperável: `canteiro-api-before-receipt-guard-20261027`; não iniciar simultaneamente, pois também usa 8081. Nenhum volume foi removido. Futuras migrations/publicações exigem avaliação/autorização própria, sem remover essa proteção silenciosamente.

Verificação real limitada a OpenAPI público, identidade do build, startup, schema e `401 application/problem+json` no GET de faturamento sem autenticação. **Não houve login, chamada autenticada de negócio ou operação financeira real**; o fluxo autenticado na interface ainda deve ser conferido pelo usuário/frontend. Sem deploy remoto, alterações frontend ou push nesta entrega.

## Fechamento da integração frontend — FRONT-F08-23/24

Integração informada como concluída em 27/10/2026 e registrada nas [provas do frontend](../../canteiro-front/docs/F08-23-24-RECEBIMENTO-SEGURO-E-CAIXA.md), consultadas para este fechamento. Snapshot final `49B3C55C...` copiado mecanicamente e tipos regenerados pelo frontend, sem remendo manual. Lista/detalhe mostram o recebido oficial; minimização e cache respeitam `RECEIVABLES.READ`. O campo de data prefere `maxReceivedOn` UTC. Disponibilidade falsa/ausente ou motivo desconhecido não abrem formulário, não solicitam alçada e não enviam POST; saldo/histórico/estorno autorizado permanecem acessíveis.

O relatório frontend registra **185 testes em 13 arquivos**, **41/41 E2E no artefato de produção com API interceptada**, **31 testes da política de distribuição**, lint/tipagem/build/scanner aprovados e verificação visual em 360/768/1280/1600 px. São provas da integração frontend, não reexecutadas pelo backend e não somadas às contagens das suítes backend ou aos lotes intermediários do frontend. As fixtures `201`/`202` permanecem somente para regressão do protocolo legado, não para afirmar disponibilidade operacional de novas baixas.

O frontend também confirmou por leitura o OpenAPI público na 8081 (142 paths/243 schemas e nulabilidade/campos novos) e o `401` anônimo de faturamento. **Não houve login, leitura autenticada de dados reais ou operação financeira real** nessa integração. Os E2E interceptados não são prova de jornada autenticada contra o banco local.

FRONT-F08-23/24 encerram somente A/B/contenção e não concluem FRONT-F08-21 ou F05-32. Elegibilidade/limites e atribuição MANUAL por NF/InvoiceItem/alocação/composição exatos, conciliação histórica e substituição de NF já recebida continuam separados, sem rateio automático ou estorno fictício de dinheiro verdadeiro. Esse fechamento não autoriza criar/aplicar migration no banco real, nova publicação/reinício ou qualquer operação financeira.
