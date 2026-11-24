# F05-22 — Leitura de notas fiscais para FRONT-F08-10

Navegação por contrato F05-35: [índice paginado e contexto exato](FRONTEND-INVOICE-CONTRACT-INDEX.md), somente INVOICES.READ, validado/publicado em 8081 em 27/10/2026. Os GETs de NF abaixo continuam compatíveis e aceitam o contractId exato selecionado.

Complemento F05-34 validado/publicado em 8081 em 27/10/2026: `receiptPosition` informa disponibilidade segura separada do status documental; situação recebido/não recebido permanece parcial enquanto F05-32 estiver pendente. [Contrato aditivo, provas e limites](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md).

Concluído e validado em 25/10/2026: consulta de NFs oficiais e itens, incluindo histórico cancelado. A instância local não foi publicada/reiniciada nesta entrega.

## Rotas

```http
GET /api/v1/company/invoices?page=0&size=20&sort=issuedOn,desc
GET /api/v1/company/invoices/{invoiceId}
Authorization: Bearer <accessToken>
```

Exigem `INVOICES.READ`, com tenant exclusivamente da sessão. Permissão de criar NF não concede leitura. Falta de autenticação retorna `401`; sem permissão, `403`; ID inexistente ou de outra empresa no detalhe, `404`. Filtros/paginação/UUID inválidos retornam `400`. Erros usam `application/problem+json` e `correlationId`.

## Lista e filtros

`PageResponseInvoiceSummaryResponse`: `items`, `page`, `size`, `totalItems`, `totalPages`.

Cada item (`InvoiceSummaryResponse`) contém `id`, `contractId`, `contractName`, `contractReference`, `contractIdentifier`, `number`, `series`, `issuedOn`, `status`, `currency`, `totalAmount`, `createdAt`, `updatedAt`. Referência/identificador do contrato e série podem ser nulos; os nomes/referências comerciais são os atuais do contrato, não snapshots fiscais.

| Parâmetro opcional | Regra |
|---|---|
| `number` | Trecho literal do número da NF, até 100 caracteres |
| `series` | Trecho literal da série, até 50 caracteres |
| `contractSearch` | Trecho do nome, referência ou `contractIdentifier` do contrato, até 200 caracteres; não exige digitação de UUID |
| `installmentReference` | Trecho da referência da parcela vinculada por alocação aos itens da NF, até 100 caracteres |
| `contractId` | ID interno opcional para navegação contextual; inexistente/fora do tenant retorna página vazia |
| `status` | Estado persistido exato: `DRAFT`, `REGISTERED`, `CANCELLED`; omitido inclui todos |
| `issuedFrom`, `issuedTo` | Limites inclusivos de emissão, `YYYY-MM-DD`; intervalo invertido é inválido |
| `page`, `size` | Padrão `0/20`; página não negativa, tamanho `1..100`; offset acima de `Integer.MAX_VALUE` é inválido |
| `sort` | `number`, `series`, `issuedOn`, `status`, `createdAt`; formato `campo,asc` ou `campo,desc`, repetível |

Filtros textuais são combinados com AND, sem distinção de maiúsculas/minúsculas. `%`, `_` e `!` são literais, não curingas; texto vazio equivale a omissão. Busca contratual combina seus três campos com OR. Referência de parcela usa existência de vínculo, sem duplicar NF com múltiplos itens. Ordenação padrão `issuedOn desc, id asc`, nulos ao final; qualquer ordenação permitida desempata por `id asc`. Não há busca por chave fiscal, documento ou campo técnico livre.

## Detalhe

`InvoiceDetailsResponse` contém `invoice` (o mesmo cabeçalho da lista) e `items` (`InvoiceReadItemResponse[]`).

```json
{
  "invoice": {
    "id": "00000000-0000-0000-0000-000000000001",
    "contractId": "00000000-0000-0000-0000-000000000002",
    "contractName": "Tubulação da fábrica",
    "contractReference": "OBRA-2026",
    "contractIdentifier": "0001/2027",
    "number": "NF-125",
    "series": null,
    "issuedOn": "2026-10-25",
    "status": "REGISTERED",
    "currency": "BRL",
    "totalAmount": "99.90",
    "createdAt": "2026-10-25T15:00:00Z",
    "updatedAt": "2026-10-25T15:00:00Z"
  },
  "items": [
    {
      "id": "00000000-0000-0000-0000-000000000003",
      "billingAllocationId": "00000000-0000-0000-0000-000000000004",
      "billingInstallmentId": "00000000-0000-0000-0000-000000000005",
      "contractServiceId": "00000000-0000-0000-0000-000000000006",
      "contractServiceName": "Montagem de tubulação",
      "measurementContractCompositionId": "00000000-0000-0000-0000-000000000007",
      "description": "Serviço registrado",
      "allocatedAmount": "99.90",
      "status": "REGISTERED",
      "createdAt": "2026-10-25T15:00:00Z",
      "updatedAt": "2026-10-25T15:00:00Z"
    }
  ]
}
```

Exemplo ilustrativo. Itens vêm em ordem estável por ID e preservam `REGISTERED`/`CANCELLED`. Serviço, nome, composição e descrição podem ser nulos conforme o registro oficial. Ajustes negativos contratuais legados mantêm o sinal e não têm serviço/composição. `measurementContractCompositionId` vem da alocação exata do item, sem agrupar ou inferir por título; duas origens do mesmo serviço permanecem separadas.

`INVOICES.READ` permite somente a referência técnica de origem nessa projeção; conteúdo de medição e recebíveis mantém sua autorização própria. IDs servem à navegação interna, não precisam ser exibidos como identificação humana. `totalAmount` e `allocatedAmount` são strings decimais canônicas com duas casas, sem símbolo ou separador de milhar, incluindo sinal quando aplicável. `currency` é herdada do contrato (`BRL`, `USD`, `EUR`), sem conversão ou soma entre moedas. O valor histórico persistido da NF não é saldo a receber nem dinheiro recebido.

GETs usam snapshot transacional somente leitura e não criam auditoria financeira, títulos, baixa, saldo, alocação, efeito fiscal ou alteração de status. NF cancelada permanece legível; uma criação `202` continua apenas proposta e não aparece como NF oficial.

## Limites deste recorte

- Não retorna chave de acesso fiscal, XML/PDF, conteúdo fiscal integral, URL/chave de storage, impostos, recebíveis ou baixas.
- Listagem/download de anexos e consulta histórica dos tributos têm contratos próprios em [F05-23](FRONTEND-INVOICE-ATTACHMENTS.md) e [F05-24](FRONTEND-INVOICE-TAXES.md). O POST de imposto existente não deve ser apresentado como consulta.
- `FRONT-F08-11` (lista de recebíveis) e `FRONT-F08-15` (histórico de baixas) não são atendidos por estas rotas.
- Contratos de criação/cancelamento de NF permanecem os existentes. Não criar tipos manuais; regenerar a partir do OpenAPI exportado.

## Evidências e export

**14 testes em 5 suítes, sem falhas, erros ou ignorados**, e Checkstyle main/test aprovados. A validação cobre limites/literais/datas, paginação e desempate, `401` sem token ou com token inválido, `403` sem INVOICES.READ, `404` de outro tenant, moedas BRL/USD/EUR, histórico cancelado com itens positivos/negativos, proposta `202` ausente dos registros oficiais e GET sem alterações de NF/itens/alocações/auditoria. O cenário completo de duas medições no mesmo serviço confirmou a origem exata por item sem conceder leitura de conteúdo da medição.

O tratamento de autenticação foi padronizado no entry point de segurança: `401` mantém o desafio Bearer e devolve Problem Details genérico com `correlationId`, sem token ou detalhe de exceção no corpo. As regras de autorização não mudaram. A regressão de login/refresh/cookie também passou.

Export oficial em [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado por `GET /v3/api-docs` da aplicação de teste com PostgreSQL isolado e gateway desabilitado: **OpenAPI 3.1.0, 134 paths, 226 schemas**. O teste contratual geral validou separadamente o gateway habilitado. Schemas novos: `PageResponseInvoiceSummaryResponse`, `InvoiceSummaryResponse`, `InvoiceDetailsResponse` e `InvoiceReadItemResponse`. Respostas 200 declaram `application/json`, segurança Bearer e erros documentados.

SHA-256 do export entregue em F05-22 (o export corrente, com anexos e tributos, é acompanhado em [F05-24](FRONTEND-INVOICE-TAXES.md)):

```text
08B92CE9F82D91315247E6A64FD03BCA22F7E7C92D2398FF8FBAE551E67D0B8A
```

Execução reproduzível, no backend com JDK 21 e Docker disponível para Testcontainers:

```powershell
$env:INVOICE_READ_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-invoice-read/gateway-enabled.openapi.json"
.\gradlew.bat test --tests com.renovar.canteiro.io.receivables.domain.InvoiceFilterTest --tests com.renovar.canteiro.io.receivables.InvoiceReadApiIntegrationTest --tests com.renovar.canteiro.io.OpenApiContractIntegrationTest --tests '*MeasurementContinuousApiIntegrationTest.invoiceReadPreservesTwoExactMeasurementOriginsWithoutRequiringMeasurementContentPermission' --tests '*AuthenticationCookieApiIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-invoice-read/build
```

Evidências locais preservadas em `tmp/validation-invoice-read/build/test-results/test` e `tmp/validation-invoice-read/build/reports/checkstyle`, ignoradas pelo Git. Nenhuma migration, operação em banco real, API local iniciada/reiniciada, alteração de infraestrutura, deploy ou push integra esta entrega. A disponibilidade dessas rotas em `localhost:8081` depende de publicação posterior autorizada; o export entregue permite gerar os tipos antecipadamente.
