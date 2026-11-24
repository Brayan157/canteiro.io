# Histórico de recebimentos e estornos (F05-26 / FRONT-F08-15)

26/10/2026. Backend somente; código, testes, Checkstyle e OpenAPI executável/export validados. Este recorte lê lançamentos existentes, não cria comando financeiro nem altera a API em execução.

## Endpoint e segurança

```text
GET /api/v1/company/receivables/{receivableId}/settlements?page=0&size=20&sort=createdAt,desc
Authorization: Bearer <accessToken>
```

Exige **RECEIVABLES.READ**, sem exigir `BILLING.READ`. Tenant exclusivamente da sessão. O título é escolhido na listagem [F05-25](FRONTEND-RECEIVABLE-READ.md); nenhum UUID precisa ser digitado. O servidor valida a cadeia do título, contrato, obra, cliente, parcela e tenant, e restringe cada lançamento e seus vínculos original/estorno àquele recebível/contrato/empresa.

Mesmo contrato ou `ContractService` compartilhado nunca amplia o conjunto. Baixas de um título renegociado não são copiadas para seus descendentes nem misturadas com eles. As atribuições a alocações/composições permanecem no ledger original: a consulta não as recalcula, reatribui, expande ou associa por nome, valor ou data.

## Parâmetros oficiais

| Parâmetro | Contrato |
|---|---|
| `receivableId` | UUID do título selecionado, somente no tenant. Ausente/estrangeiro/incompatível responde 404. |
| `entryType` | Opcional: `SETTLEMENT` ou `REVERSAL`; omitido inclui ambos. |
| `status` | Opcional: situação persistida `CONFIRMED` ou `CANCELLED`; omitido inclui ambas. Não existe `REVERSED` neste enum. |
| `paymentMethod` | Opcional: `CASH`, `PIX`, `BANK_TRANSFER`, `CHECK`, `CREDIT_CARD`, `DEBIT_CARD`, `OTHER`. Somente enum, não dados de pagamento. |
| `receivedFrom`, `receivedTo` | Datas de negócio inclusivas, `yyyy-MM-dd`. Intervalo invertido é inválido. Estorno usa sua própria data de estorno. |
| `page` | Desde 0; padrão 0. |
| `size` | 1..100; padrão 20. Offset maior que `Integer.MAX_VALUE` é inválido. |
| `sort` | Somente `receivedOn`, `createdAt`, `entryType`, `paymentMethod`, `status`; `campo,asc`/`campo,desc`, repetível. |

Filtros combinados por AND. Não existe pesquisa de texto livre/observação, dado fiscal/bancário, documento ou chave Pix. Não existe filtro por saldo ou soma. Padrão `createdAt desc, id asc`, nulos ao final; qualquer sort mantém desempate `id asc`. Página e contagem usam os mesmos filtros/cadeia em snapshot transacional de leitura.

## Resposta 200

`PageResponseReceivableSettlementHistoryResponse` contém `items`, `page`, `size`, `totalItems`, `totalPages`. Título válido sem lançamentos retorna página vazia, não 404.

Exemplo ilustrativo de par original/estorno; placeholders de UUID devem ser substituídos pelos IDs oficiais da resposta:

```json
{
  "items": [
    {
      "id": "uuid-do-estorno",
      "receivableId": "uuid-do-titulo",
      "receivableReference": "REC-01",
      "contractId": "uuid-do-contrato",
      "displayLabel": "Estorno de recebimento",
      "receivedOn": "2026-10-26",
      "amount": "-25.00",
      "entryType": "REVERSAL",
      "reversalOfSettlementId": "uuid-do-recebimento-original",
      "reversedBySettlementId": null,
      "paymentMethod": "PIX",
      "status": "CONFIRMED",
      "currency": "BRL",
      "createdAt": "2026-10-26T15:30:00.123456Z",
      "updatedAt": "2026-10-26T15:30:00.123456Z"
    },
    {
      "id": "uuid-do-recebimento-original",
      "receivableId": "uuid-do-titulo",
      "receivableReference": "REC-01",
      "contractId": "uuid-do-contrato",
      "displayLabel": "Recebimento",
      "receivedOn": "2026-10-01",
      "amount": "25.00",
      "entryType": "SETTLEMENT",
      "reversalOfSettlementId": null,
      "reversedBySettlementId": "uuid-do-estorno",
      "paymentMethod": "PIX",
      "status": "CONFIRMED",
      "currency": "BRL",
      "createdAt": "2026-10-01T14:00:00.123456Z",
      "updatedAt": "2026-10-01T14:00:00.123456Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 2,
  "totalPages": 1
}
```

DTO `ReceivableSettlementHistoryResponse`: 15 propriedades, sendo 12 obrigatórias. Somente `receivableReference`, `reversalOfSettlementId` e `reversedBySettlementId` são opcionais/nulas. `currency` é BRL/USD/EUR, herdada do contrato, nunca moeda editável da baixa. `amount` é o valor **assinado persistido**, string decimal canônica de duas casas, `string/decimal` no OpenAPI: recebimento positivo, estorno negativo. Não converter para absoluto, não calcular líquido/total e não somar moedas distintas.

`displayLabel` é texto controlado pelo backend, sem payload livre: “Recebimento” ou “Estorno de recebimento”. Para seleção legível, mostrar esse rótulo com data de negócio, método e `createdAt` UTC completo quando precisar distinguir lançamentos semelhantes. `createdAt` é instante real de registro, não número comercial, nem a data do recebimento; não truncar para inferir identidade. Pode haver valores/datas/métodos iguais: manter linhas distintas e usar **somente `id` como chave opaca de seleção**, sem deduplicar, inventar referências ou pedir UUID manual. Referência do título pode ser nula; não fabricar numeração oficial.

### Semântica dos vínculos e histórico

- `reversalOfSettlementId` é a referência exata do estorno ao original no mesmo título/contrato/tenant; nula para recebimento original.
- `reversedBySettlementId` é o ID exato do estorno já registrado para o original; nulo quando não há estorno ou quando a própria linha é estorno. O vínculo é retornado mesmo se a outra linha estiver fora dos filtros/página.
- Um original estornado pode continuar com `status=CONFIRMED`. Não regravar status, inventar `REVERSED`, esconder o original ou anular o sinal do estorno.
- `CONFIRMED/CANCELLED` descreve o lançamento persistido. Títulos cancelados/renegociados continuam permitindo consulta histórica, sem liberar comandos incompatíveis com o estado atual.
- Um `reversedBySettlementId` nulo não concede permissão nem garante que o estorno será aceito: alçada, saldo, histórico e demais regras continuam no POST existente. FRONT-F08-14 permanece integração separada.

Para uso futuro do comando existente, selecionar o `id` do original retornado e o mesmo `receivableId` do contexto:

```text
POST /api/v1/company/receivables/{receivableId}/settlements/{settlementId}/reversal
```

Nenhum comando foi criado/alterado nesta entrega. Não procurar correspondência por nome/valor/data, por serviço compartilhado ou pelo ID de uma proposta `202`.

## Privacidade, saldo e erros

Não retorna `note`, motivo livre, conta, Pix, documento fiscal, anexo, conteúdo de medição, payload/auditoria ou lista de atribuições. Método `PIX` não expõe chave Pix. Sem URL pública ou conteúdo de storage. GET não registra baixa, estorno, auditoria financeira, versão de lock ou atualização de atraso; não aplica proposta e não corrige passado. Uma resposta `202` dos comandos existentes não aparece como lançamento oficial nem como vínculo de estorno.

**Saldo e situação financeira atual continuam exclusivamente no `GET /api/v1/company/receivables/{receivableId}/balance`.** O histórico é paginado e não permite apurar totais no frontend. Atualizar o saldo consultando essa rota após uma mutação efetivamente aplicada, nunca somando linhas da página.

- `200 application/json`: página completa ou vazia; `Cache-Control` contém `no-store` (header completo do Spring Security pode conter outras diretivas restritivas).
- `400 application/problem+json`: UUID, enum, data, intervalo, página ou sort inválido.
- `401 application/problem+json`: sem autenticação válida.
- `403 application/problem+json`: sem permissão/contexto autorizado.
- `404 application/problem+json`: título ausente, estrangeiro ou cadeia inválida, sem revelar qual entidade falhou.

Problem Details inclui `correlationId`. GET não tem `201`/`202`. Sem mudança de schema: reusa índices/FKs existentes; não criar migration vazia.

## Validação e export

**32 testes em 7 suítes aprovados, sem falhas, erros ou ignorados; Checkstyle main/test sem erros, BUILD SUCCESSFUL e `git diff --check` limpo.** Dez testes novos de integração cobrem original/estorno por comandos oficiais, sinais e vínculos bidirecionais, original fora do filtro/página, permissões sem BILLING.READ, `401/403/404`, duas empresas, títulos distintos no mesmo contrato/serviço, constraints de origem, cancelados/renegociados e descendentes, vazio, filtros/enums/datas/paginação/sort inválidos, duplicatas legítimas com IDs distintos, três moedas, precisão além do limite inteiro de JavaScript e propostas `202` sem lançamento/vínculo fictício. Snapshots confirmam ausência de mutações no título, baixa, atribuição imutável, alocação, parcela, auditoria e solicitações. Unitários cobrem filtro, autorização anterior à consulta, tenant/cadeia e limite de offset; regressão da F05-25, saldo e comando de baixa preservada. O ensaio inicial identificou data inválida na baixa do título novo de renegociação no cenário sintético; o teste passou a usar sua data de emissão, sem alterar regra de domínio.

Export oficial: [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado por `GET /v3/api-docs` da aplicação com PostgreSQL/Testcontainers isolado e gateway desabilitado; JSON nunca editado manualmente. **OpenAPI 3.1.0, 137 paths e 234 schemas**, com novo GET no path de settlements já existente. Schemas adicionados: `ReceivableSettlementHistoryResponse` e `PageResponseReceivableSettlementHistoryResponse`; enums oficiais reutilizados, 12 campos obrigatórios/3 opcionais nulos, decimal com sinal, segurança Bearer global e Problem Details. POST anterior preservado com `201/202`. Contrato com gateway habilitado validado/exportado separadamente em `tmp/validation-receivable-history/gateway-enabled.openapi.json`.

SHA-256 do export entregue:

```text
53F7EAC14CD2F644BCD35035A6E6670D18839FC6364AE8C01B75912E83F91FC9
```

Execução reproduzível com JDK 21 e Docker disponível somente para Testcontainers:

```powershell
$env:RECEIVABLE_HISTORY_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-receivable-history/gateway-enabled.openapi.json"
.\gradlew.bat test --tests '*ReceivableSettlementHistoryApiIntegrationTest' --tests '*ReadReceivableSettlementsUseCaseTest' --tests '*ReceivableSettlementFilterTest' --tests '*ReceivableReadApiIntegrationTest' --tests '*ReceivableBalanceTest' --tests '*ReceivableSettlementServiceTest' --tests '*OpenApiContractIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-receivable-history/build
```

Provas locais ignoradas pelo Git em `tmp/validation-receivable-history/build/test-results/test` e `tmp/validation-receivable-history/build/reports/checkstyle`. As provas/hash F05-25 no seu guia identificam o snapshot anterior daquele recorte; o export completo corrente é o descrito aqui.

Nenhum deploy, início/reinício da API/Docker, infraestrutura, push, migration/banco real ou operação financeira real integra esta entrega. Gerar tipos do export permite integrar antecipadamente; a disponibilidade da rota em `localhost:8081` depende de publicação posterior autorizada, sem afirmar E2E contra a instância antiga.
