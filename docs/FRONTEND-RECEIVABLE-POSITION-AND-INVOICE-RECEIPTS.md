# F05-33/34 — Posição de recebíveis e recebimento da NF

27/10/2026 — consultas validadas e publicadas somente na API local 8081 às 20:20:25 (America/Sao_Paulo). F05-33 concluída; F05-34 entrega disponibilidade honesta, mas situação paga/parcial comprovável permanece parcial e dependente do ledger F05-32.

Complemento F05-32 implementado em28/10 e **publicado somente em8081 em29/10/2026**, após autorização humana específica e V90 pelo Flyway: [manual por NF/item](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md) acrescenta posição comprovável UNPAID/PARTIALLY_RECEIVED/RECEIVED/SETTLED_BY_ADJUSTMENTS para vínculos íntegros, mantendo indisponibilidade/null no legado sem prova. Export27037B87... disponível para tipos e verificado contra o contrato vivo. [Provas atuais da ativação](F05-32-LOCAL-ACTIVATION-2026-10-29.md); as evidências anteriores abaixo são históricas. Genérico permanece bloqueado e não houve operação financeira real.

## Recebíveis

`GET /api/v1/company/receivables?financialView=OUTSTANDING&page=0&size=20`

Exige `RECEIVABLES.READ`, tenant da sessão. `financialView` é aplicado no banco antes de count/paginação, combinado por AND com os filtros atuais:

| Valor | Regra |
| --- | --- |
| `OUTSTANDING` | Títulos não cancelados/renegociados, saldo devido > 0, incluindo parcialmente recebidos |
| `RECEIVED` | Títulos não cancelados/renegociados, saldo zero e dinheiro confirmado líquido de estornos > 0 |
| `ALL` ou omitido | Histórico completo, incluindo encerramento apenas por ajustes |

UI “A receber” envia `OUTSTANDING` explicitamente. API omitida permanece `ALL` por compatibilidade. `status` continua filtro do estado persistido, não do saldo atual. Enum inválido retorna `400` Problem Details/correlationId.

`ReceivableSummaryResponse` ganha `financialPosition`, também em `detail.receivable`:

```json
{
  "financialPosition": {
    "status": "PARTIALLY_RECEIVED",
    "withheldAmount": "10.00",
    "creditedAmount": "0.00",
    "netReceivableAmount": "90.00",
    "confirmedSettlementAmount": "40.00",
    "outstandingAmount": "50.00"
  }
}
```

Dinheiro é string canônica de duas casas na `currency` do título. Usa os fatos do saldo existente: retenção registrada, créditos líquidos de reversões e recebimentos/estornos confirmados assinados. Propostas pendentes não entram. Sem regravar status/valor original, sem soma de moedas ou predecessores/sucessores. Snapshot transacional protege count/conteúdo.

`financialPosition.status`: `OPEN`, `PARTIALLY_RECEIVED`, `RECEIVED`, `SETTLED_WITH_ADJUSTMENTS`, `CANCELLED`, `RENEGOTIATED`.

`RECEIVED` no status significa encerramento por dinheiro sem ajustes; `SETTLED_WITH_ADJUSTMENTS` significa saldo zero com retenção/crédito. Encerramento misto com dinheiro pode estar no filtro `RECEIVED`, mantendo esse rótulo e componentes separados. Ajuste sem dinheiro aparece apenas em `ALL`. Cancelados/renegociados são históricos, não cobrança do saldo nominal. Vencimento é dimensão distinta.

Frontend não soma saldos, filtra páginas ou calcula posição. Elegibilidade de criar baixa permanece no `/balance`, atualmente bloqueada.

## NF

GET lista/detalhe mantém `INVOICES.READ`. `InvoiceSummaryResponse`, inclusive em `detail.invoice`, ganha:

```json
{
  "status": "REGISTERED",
  "receiptPosition": {
    "available": false,
    "status": "UNAVAILABLE",
    "unavailableReason": "EXACT_INVOICE_SETTLEMENT_LINK_REQUIRED",
    "receivedAmount": null,
    "outstandingAmount": null,
    "calculatedAt": "2026-10-27T14:00:00Z"
  }
}
```

Instante ilustrativo: Clock oficial UTC, não data de pagamento. Neste recorte TODAS as posições por NF são indisponíveis: não existe vínculo exato manual NF/item/recebimento entregue por F05-32. Não inferir não recebida/recebida pela alocação, título, caixa contratual ou total documental; null nunca é zero fictício.

| unavailableReason | Condição |
| --- | --- |
| `INSUFFICIENT_PERMISSION` | Falta `RECEIVABLES.READ` adicional; não consulta recebimentos |
| `INVOICE_CANCELLED` | Usuário financeiro autorizado e NF cancelada |
| `INVOICE_NOT_REGISTERED` | Usuário financeiro autorizado e NF ainda não registrada |
| `EXACT_INVOICE_SETTLEMENT_LINK_REQUIRED` | Autorizado e NF registrada, sem vínculo comprovável |

Status documental permanece separado. Estados recebido/parcial/não recebido comprováveis continuam dependentes de F05-32 e conciliação/substituição, não são inventados nesta entrega.

## Limites e entrega

- Gerar tipos somente do OpenAPI executável exportado, sem DTO manual.
- Rotas privadas/no-store; `403` sem permissão da consulta e `404` seguro no detalhe.
- Sem migration, nova dependência, mudança de criação/aprovação ou operação financeira real.
- TODOS os novos recebimentos seguem bloqueados por `SETTLEMENT_INVOICE_LINK_REQUIRED`.
- Publicação autorizada só na API local 8081 depois dos testes, sem Flyway/seeds; sem deploy remoto/infra/push.

## Provas e publicação local

- Rodada final: 45 testes distintos em 9 suítes, zero falhas/erros/ignorados. Abrange consultas/isolamento/permissões, paginação/contagem, estornos, crédito/retenção, renegociação/202 e regressões da contenção de novos recebimentos. Uma fixture de crédito precisou de transação explícita na rodada inicial; corrigida e aprovada na rodada final.
- Checkstyle main/test sem violações; bootJar aprovado. Relatórios locais: `tmp/validation-receivable-position/build/test-results/test` e `build/reports/checkstyle` dentro desse diretório de validação. Não é execução de todos os testes do repositório.
- OpenAPI executável exportado pelo teste contratual: `postman/canteiro-io.openapi.json`, versão 3.1.0, 143 paths/245 schemas. SHA-256: `0AB14484BB2F572163AB84828328B5FDA9B23C31D8988DDD26AD7E068922D1DE`. Enum de financialView explicitamente publicado. Substitui o preliminar DB0D9543; não gerar tipos desse preliminar.
- JAR validado/publicado SHA-256: `D9F59692A283AEEDC06B591444EC49C1EBD61DAC05FD6D658810DE37C9E80455`. Comparação byte a byte das entradas contra o JAR anterior `3798D6B2...`: somente 21 classes autorizadas deste recorte; nenhuma dependência/configuração/migration removida ou modificada pelo pacote novo.
- Imagem local: `canteiroio-api:local-receivable-position-20261027`. Container `canteiro-api`; startup confirmado às `2026-10-27T23:20:25Z`, porta host 8081.
- `/v3/api-docs` local retornou `200 application/json`, OpenAPI 3.1, 142 paths/245 schemas. Os seis schemas novos/alterados de consulta são idênticos aos do export. A única diferença de paths é `/api/v1/webhooks/payments`: habilitado no teste contratual, desabilitado na instância local, sem alterar essa configuração nesta publicação.
- Flyway, seed do proprietário e seed de demonstração desabilitados. Schema real continua V89. Mesmo volume privado, rede e porta; container anterior preservado parado em `canteiro-api-before-receivable-position-20261027` para retorno controlado.
- Nenhum login ou operação financeira real foi executado para validar a publicação; fluxos autenticados foram testados com dados sintéticos em PostgreSQL temporário. A confirmação visual autenticada no frontend é uma verificação posterior, não prova backend já realizada.
- PostgreSQL emitiu aviso de versão de collation na consulta de verificação de schema; não alteramos collation/índices/banco para tratar esse aviso fora do escopo.

## Fechamento de integração do frontend

Integração registrada em 27/10/2026 no [relatório FRONT-F08-25/26](C:/Users/oqdel/OneDrive/Desktop/canteiro-front/docs/F08-25-26-POSICAO-RECEBIVEIS-E-NF.md), consultado pelo backend. FRONT-F08-25 está concluída; FRONT-F08-26 permanece parcial, com indisponibilidade integrada e sem declarar NF paga/parcial/não paga antes de F05-32.

O frontend confirmou cópia do export definitivo `0AB14484...` e regeneração dos tipos. Evidências reportadas no documento: 126 testes/8 arquivos reexecutados após o export, 31 testes da política/build/scanner, 34/34 E2E no artefato com API interceptada e capturas inspecionadas em quatro larguras. Lint sem novos erros, com oito avisos preexistentes. Lotes anteriores não são somados como testes distintos.

Também reportou leitura independente do OpenAPI público local (142 paths/245 schemas, enum/referências presentes) e `401 application/problem+json` no GET anônimo de recebíveis. Essas verificações não são login ou homologação autenticada contra dados reais. Não houve reexecução desses testes pelo backend neste registro, nova publicação, migration ou operação financeira real. A contenção continua ativa. O pedido extra de valor no cadastro do contrato foi expressamente retirado pelo usuário e não integra esta entrega.
