# F05-23 — Histórico e download de anexos de NF

Concluído e validado em 25/10/2026 para o recorte de anexos de FRONT-F08-10. Sem publicação ou reinício da instância local.

Correção aditiva F05-36 publicada/verificada somente em 8081 em 28/10/2026: resposta imediata do upload copia os timestamps somente após flush, preservando o contrato obrigatório de oito metadados. JAR ativo FCEEA62E... e OpenAPI correspondente ao export 976E9070... conferidos; sem migration ou upload real. Não reenviar arquivo real automaticamente se uma resposta anterior foi inconclusiva. [Diagnóstico e evidências atuais](FRONTEND-INVOICE-UPLOAD-AND-CANCELLATION-SAFETY.md).

## Rotas e permissões

```http
GET /api/v1/company/invoices/{invoiceId}/attachments?page=0&size=20&sort=createdAt,desc
GET /api/v1/company/invoices/{invoiceId}/attachments/{attachmentId}/download
Authorization: Bearer <accessToken>
```

Lista exige `INVOICES.READ`. Download exige **ambas** `INVOICES.READ` e `INVOICES.EXPORT`. Tenant é exclusivamente o da sessão. A regra de novos uploads está descrita abaixo.

NF cancelada mantém metadados e arquivo disponíveis a quem possuir as permissões. Lista não altera auditoria. Download bem-sucedido registra somente auditoria técnica `INVOICES/EXPORT`, entidade `InvoiceAttachment`, com ator, instante UTC, referência da NF, formato e origem `invoice_attachment_download`. Não grava arquivo, nome, hash, storage ou conteúdo fiscal no evento novo. Não altera NF, itens, alocações, títulos, saldos ou recebimentos, nem cria proposta de aprovação.

## Novo upload de PDF

`POST /api/v1/company/invoices/{invoiceId}/attachments` exige alçada efetiva direta de `INVOICES.CREATE`. Envie multipart com `attachmentType=PDF` e `file` com extensão `.pdf`, MIME `application/pdf` e até 10 MiB. Um PDF válido é gravado com auditoria e retorna `200` com os metadados existentes.

Cada NF pode receber um único PDF. A transação bloqueia a linha da NF antes de verificar a existência do PDF e gravar o novo anexo. Qualquer PDF anterior ocupa essa vaga, inclusive quando há mais de um PDF histórico. XML legado continua disponível para lista, download e evidência fiscal quando autorizada. Uma NF que só tem XML legado ainda pode receber seu primeiro PDF. A prévia XML segue na rota própria.

| Status | `code` | `reason` |
| --- | --- | --- |
| `409` | `BUSINESS_RULE_VIOLATION` | `INVOICE_PDF_ALREADY_ATTACHED` |
| `415` | `VALIDATION_FAILED` | `INVOICE_ATTACHMENT_PDF_REQUIRED` para XML ou mídia/extensão incompatível |

Os erros retornam Problem Details com `correlationId`. PDF malformado com MIME e extensão corretos retorna `400`. O frontend pode ocultar o upload ao encontrar um PDF na lista e tratar `409` caso outro envio seja confirmado em paralelo.

O [OpenAPI completo](../postman/canteiro-io.openapi.json) contém 159 paths e 287 schemas. SHA-256 `3C0BB1821DB5ADEA24D8E92AD1F6193F9C857C3CD37E2498CCC2E849D842062E`. A comparação identificou mudanças somente no POST de anexos. Os 17 testes finais passaram, incluindo concorrência, histórico, prévia XML e evidência fiscal em PostgreSQL descartável. O Checkstyle também passou.

Em 12/11/2026, a regra foi ativada em `localhost:8081`. O OpenAPI da instância tem 154 paths e 279 schemas. Use o export completo para gerar os tipos do frontend. Os campos e as respostas do POST conferem entre os dois contratos, com `operationId` preservado em cada ambiente. O banco já estava na V94 e seu histórico Flyway permaneceu idêntico. O container de retorno é `canteiro-api-before-invoice-single-pdf-20261108`.

## Metadados paginados

Retorno `PageResponseInvoiceAttachmentResponse`: `items`, `page`, `size`, `totalItems`, `totalPages`. O item reutiliza `InvoiceAttachmentResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "invoiceId": "00000000-0000-0000-0000-000000000002",
  "attachmentType": "PDF",
  "originalFilename": "Nota 125.pdf",
  "contentType": "application/pdf",
  "contentSize": 2048,
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "createdAt": "2026-10-25T15:00:00Z"
}
```

Valores ilustrativos. Todos os oito campos são não nulos; `attachmentType` é `PDF` ou `XML`. `contentSize` é tamanho inteiro em bytes (não dinheiro). `sha256` é o checksum do arquivo salvo. `originalFilename` preserva o nome permitido, retirando caminhos e caracteres inseguros de eventual metadado legado somente na projeção; o registro armazenado não é regravado. Não usar esse campo para renomear o download: o header é a autoridade.

Não há bytes, chave de acesso fiscal, conteúdo XML/PDF, autor detalhado, chave/path/URL de storage nem link público na lista. A lista não carrega o arquivo do storage. NF existente sem anexos retorna página vazia; NF inexistente/fora do tenant retorna `404`, inclusive na lista.

| Parâmetro | Contrato |
|---|---|
| `page` | Padrão `0`, não negativo |
| `size` | Padrão `20`, entre `1` e `100` |
| `sort` | `createdAt`, `attachmentType`, `originalFilename`, `contentSize`; formato `campo,asc` ou `campo,desc`, repetível |

Ordem padrão `createdAt desc, id asc`. Todas as ordenações desempatarão por `id asc`; campos de ordenação não são nulos. A ordenação de nome usa o metadado persistido, cuja projeção pode ser sanitizada para legado. Offset acima de `Integer.MAX_VALUE` e campos/direções livres são inválidos. Não existe filtro por chave técnica ou campo de tenant.

Lista retorna `200 application/json` e `Cache-Control: no-store`.

## Download autenticado

Retorna `200` com os **bytes exatos do arquivo existente**; não reemite nem reconstrói documento fiscal. Reutiliza `StorageProvider.load` com arquivo limitado a 10 MiB, conforme o upload atual; este contrato entrega bytes limitados, não é o gerador de relatórios streaming.

- `Content-Type`: `application/pdf` para PDF e `application/xml` para XML; XML originalmente armazenado como `text/xml` é baixado como `application/xml`.
- `Content-Disposition: attachment; filename=...`: nome derivado do nome original, sem caminhos/controles, em ASCII minúsculo com separadores seguros, até 120 caracteres de base; extensão `.pdf`/`.xml` vem do tipo oficial. Nome vazio/inválido/reservado usa `anexo-nf.pdf` ou `anexo-nf.xml`.
- `Content-Length`: tamanho exato em bytes.
- `Cache-Control: no-store` e `X-Content-Type-Options: nosniff`.
- Sem `Location`, redirecionamento ou URL pública/temporária. Não há contrato de Range, ETag ou cache condicional; não assumir `206`/`304`.

Usar o fluxo de download autenticado Blob do frontend, preservando o `Content-Disposition`. Metadados, recebimento dos bytes e marcação financeira são operações diferentes: baixar não confirma pagamento.

## Erros

Todos usam `application/problem+json` com `code` e `correlationId`, sem conteúdo fiscal ou erro bruto do storage.

| Status | Situação |
|---|---|
| `400` | UUID, paginação ou ordenação inválidos |
| `401` | Ausência de autenticação/token inválido |
| `403` | Falta da permissão efetiva exigida para a operação |
| `404` | NF/anexo ausente, de outro tenant, ou anexo que não pertence à NF/contrato do caminho |
| `503` | Somente download: storage indisponível ou arquivo incompatível com tamanho/hash oficial, inclusive metadado acima do limite de 10 MiB; mensagem genérica |

Autorização e cadeia são validadas antes de acessar storage. Falha de download não gera evento de exportação bem-sucedida nem modifica o domínio financeiro. O frontend deve tratar erro Problem Details mesmo quando esperava Blob.

## Escopo e validação

**21 testes em 5 suítes, sem falhas, erros ou ignorados**, e Checkstyle main/test sem erros na validação final. Incluem regressão de upload e leitura de NF, permissões separadas de lista/download, ausência/token inválido, duas empresas, NF incorreta para o mesmo anexo, UUID/paginação/sort inválidos, ordem/páginas estáveis, bytes PDF/XML e headers, sanitização de nome/caminho/controles, NF cancelada, ausência de efeito financeiro e auditoria segura de exportação. Storage indisponível, tamanho ou hash divergente retornaram erro seguro sem auditoria de sucesso. Foram usados somente documentos sintéticos e storage substituído em teste; PostgreSQL foi isolado por Testcontainers.

Export oficial em [postman/canteiro-io.openapi.json](../postman/canteiro-io.openapi.json), gerado de `GET /v3/api-docs` da aplicação de teste, sem gateway: **OpenAPI 3.1.0, 135 paths, 227 schemas**. O contrato com gateway habilitado foi validado/exportado separadamente em `tmp/validation-invoice-attachments/gateway-enabled.openapi.json`. O DTO de upload foi reutilizado; o schema paginado acrescentado é `PageResponseInvoiceAttachmentResponse`. O schema de sucesso do download é binário nos dois MIME types; erros continuam Problem Details.

SHA-256 do export entregue em F05-23 (o export corrente, incluindo consulta de tributos, é acompanhado em [F05-24](FRONTEND-INVOICE-TAXES.md)):

```text
2EE00681E20FE0019806D40AC23B7A3DA953824B06B4BE812D47D4B1788C0304
```

Execução reproduzível com JDK 21 e Docker disponível para os testes isolados:

```powershell
$env:INVOICE_READ_OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
$env:OPENAPI_EXPORT_PATH = "$PWD/tmp/validation-invoice-attachments/gateway-enabled.openapi.json"
.\gradlew.bat test --tests '*InvoiceAttachmentApiIntegrationTest' --tests '*InvoiceAttachmentFilenameTest' --tests '*InvoiceAttachmentUploadServiceTest' --tests '*InvoiceReadApiIntegrationTest' --tests '*OpenApiContractIntegrationTest' checkstyleMain checkstyleTest --no-daemon --console=plain -PbuildOutputDir=tmp/validation-invoice-attachments/build
```

Evidências XML locais em `tmp/validation-invoice-attachments/build/test-results/test` e `tmp/validation-invoice-attachments/build/reports/checkstyle`, ignoradas pelo Git. O export permite gerar os tipos e integrar; a disponibilidade em `localhost:8081` depende de publicação posterior autorizada. Esta entrega não confirma E2E contra a instância antiga.

Consulta de tributos históricos tem recorte próprio em [F05-24](FRONTEND-INVOICE-TAXES.md). A validação acima registra a entrega histórica de leitura e download. A regra atual de upload é F05-44.
