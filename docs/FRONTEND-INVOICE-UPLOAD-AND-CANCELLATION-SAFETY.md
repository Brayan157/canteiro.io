# Anexos e cancelamento documental de NF — correção local

Data: 28/10/2026.

Estado: implementado, com 40 testes aprovados em cinco suítes, e publicado somente na API local de localhost:8081 em 28/10/2026 após nova autorização humana. Sem migration, seed, operação financeira real ou uso dos arquivos fiscais do usuário.

JAR publicado e conferido dentro do container: SHA-256 `FCEEA62E421945764C8B8DB92D82E1C1F83CC59284DD903195EC5908DD488597`. Somente classes do recorte e uma diferença de depuração conferida; recursos/configurações/dependências/migrations iguais. [Artefato preservado, manifesto, provas de publicação e plano de retorno](F05-36-CANDIDATE-AND-ROLLBACK.md).

## Upload: manter o contrato dos oito metadados

`POST /api/v1/company/invoices/{invoiceId}/attachments` continua respondendo `200` com `id`, `invoiceId`, `attachmentType`, `originalFilename`, `contentType`, `contentSize`, `sha256` e `createdAt` UTC obrigatório. Permissões, tenant, storage privado e validação por conteúdo são preservados.

A reprodução sintética anterior demonstrou que `save` podia devolver a projeção imutável antes da geração de `createdAt`; o registro acabava persistido com data, mas a resposta já tinha copiado null. O candidato usa `saveAndFlush` antes de montar essa projeção. Não altera o schema para aceitar null nem afrouxa a validação do frontend.

Diagnóstico da referência `web-144e73f4-f8c8-46fa-a431-d86a0dc520d9`: consulta anterior somente de metadados identificou um evento de upload e um anexo persistido, com metadados completos e cadeia de tenant/contrato válida. Isso não é captura da resposta HTTP histórica. O defeito da resposta foi demonstrado separadamente com XML sintético e storage simulado. Não reenviar automaticamente o arquivo real: em resultado inconclusivo, oferecer a consulta oficial do histórico de anexos e exigir ação humana para eventual nova tentativa.

## Cancelamento: operação interna, com bloqueios conservadores

Rota existente: `POST /api/v1/company/invoices/{invoiceId}/cancellation`.

Request: `{ "reason": "Motivo obrigatório", "justification": "Opcional" }`.

- `200`: cancelamento documental interno aplicado atomicamente, com itens/efeitos tributários permitidos e auditoria.
- `202`: apenas pedido de aprovação; preserva valores, status, versões e datas dos registros oficiais até aplicação autorizada.
- `400`, `403`, `404`, `409`: Problem Details; erros incluem `correlationId`.
- Autorização existente de cancelamento de INVOICES: direta efetiva ou solicitação; aprovação por outro usuário autorizado. Tenant exclusivamente da sessão.

Bloqueios `409 BUSINESS_RULE_VIOLATION`, com `reason` controlado:

| reason | Significado para a interface |
| --- | --- |
| `INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS` | Existe dinheiro recebido ainda vigente nos títulos vinculados; não cancelar a NF nem propor estorno fictício. |
| `INVOICE_CANCELLATION_HAS_TAX_PAYMENTS` | Obrigação tributária vinculada está paga/parcialmente paga; não cancelar automaticamente o pagamento. |
| `INVOICE_CANCELLATION_REQUIRES_REVIEW` | Origem incompleta/incoerente, renegociação/crédito, estado histórico sem comprovação, estorno inconsistente ou situação não elegível exige conciliação formal. |

Antes da proposta e novamente na aprovação/aplicação, o servidor valida NF → item → alocação → parcela → recebível, sem inferir atribuição de recebimento pela quantia. Um título compartilhado por várias NFs com recebimento vigente bloqueia conservadoramente o cancelamento de cada NF relacionada; isso não afirma que cada uma recebeu o valor completo. Histórico sem movimento e origem íntegra pode seguir; estornos históricos só são considerados se ligados exatamente às baixas e sem saldo recebido vigente. A operação não cria estorno.

Os bloqueios transacionais de contrato, NF, recebíveis e contas a pagar tributárias permanecem até o commit. Validação de proposta usa bloqueios sem incremento forçado de versão. A aplicação revalida após o bloqueio para observar concorrência, inclusive quando pagamento/recebimento surge após o pedido.

Concorrência também pode devolver o `409` padronizado de versão alterada, antes da guarda financeira, sem `reason` específico. Nesse caso, reconsultar o estado oficial; não exigir os três motivos em todo 409 nem repetir automaticamente o comando. Nenhum dos caminhos concorrentes pode aplicar o cancelamento usando uma posição anterior ao recebimento.

Cancelar NF não cancela parcela, alocação, cobrança, título ou dívida. Reavalia somente os efeitos tributários permitidos e mantém o histórico; não há exclusão física, cancelamento fiscal externo ou endpoint de substituição atômica. Não orientar estorno de recebimento verdadeiro para trocar documento.

## Limites e integração

Uma NF continua pertencendo a exatamente um contrato. Pode referenciar várias alocações compatíveis desse contrato; uma alocação pode ter emissão parcial em várias NFs respeitando sua capacidade documental. Não foi criado rateio novo de retenção ou pagamento.

F05-32 continua pendente: não existe novo ledger manual NF/item/alocação/origem nesta correção. Todos os novos recebimentos legados continuam bloqueados por `SETTLEMENT_INVOICE_LINK_REQUIRED`; consulta e estorno históricos seguem suas autorizações existentes. Não derivar posição recebida da NF a partir do contrato/título.

A guarda foi ativada e sua publicação comprovada em localhost:8081, com o hash exato do pacote testado e contrato vivo correspondente. O frontend pode avaliar a entrega segundo sua própria autorização/revisão de integração; a comprovação backend não substitui testes da interface. Não executar cancelamento, upload ou baixa real para validar a tela sem autorização própria. Uma restauração do pacote anterior deve voltar a bloquear a UI de cancelamento, pois remove esta guarda.

## Evidências

Validação de 28/10/2026, diretório `tmp/validation-invoice-attachment-cancellation/build`:

| Suíte | Testes | Resultado |
| --- | ---: | --- |
| InvoiceAttachmentApiIntegrationTest | 10 | Aprovados; PDF/XML sintéticos, oito metadados, createdAt UTC igual ao persistido, autorização/tenant e histórico/download. |
| InvoiceTaxReadApiIntegrationTest | 25 | Aprovados; inclui criação/consulta de tributos e 11 cenários de cancelamento, parcial/total, título compartilhado, aprovação e concorrência PostgreSQL. |
| FinancialCancellationServiceTest | 3 | Aprovados; orquestração de cancelamento e estorno histórico. |
| ReceivableSettlementCreationPolicyTest | 1 | Aprovado; bloqueio integral das novas baixas preservado. |
| OpenApiContractIntegrationTest | 1 | Aprovado; geração executável, rotas, metadados obrigatórios e motivos de bloqueio. |

Nas primeiras rodadas, o teste concorrente exigia somente o motivo de recebimento e falhou ao receber outro 409. A verificação final exige exatamente o motivo de recebimento ou a mensagem contratual de versão alterada, ausência de cancelamento/auditoria/pedido parcial, e uma nova chamada que identifica o recebimento após o commit. Não trata qualquer 409 como sucesso.

Comando reprodutível (Java 21, Docker disponível; apenas PostgreSQL descartável de Testcontainers):

```powershell
$env:OPENAPI_EXPORT_PATH = "$PWD/postman/canteiro-io.openapi.json"
./gradlew.bat -PbuildOutputDir=tmp/validation-invoice-attachment-cancellation/build test --tests com.renovar.canteiro.io.receivables.InvoiceAttachmentApiIntegrationTest --tests com.renovar.canteiro.io.receivables.InvoiceTaxReadApiIntegrationTest --tests com.renovar.canteiro.io.receivables.application.FinancialCancellationServiceTest --tests com.renovar.canteiro.io.receivables.application.ReceivableSettlementCreationPolicyTest --tests com.renovar.canteiro.io.OpenApiContractIntegrationTest checkstyleMain checkstyleTest --no-daemon
```

OpenAPI exportado do contexto executável de `/v3/api-docs`: `postman/canteiro-io.openapi.json`, versão 3.1.0, 145 paths e 247 schemas. SHA-256: `976E9070772D909E7F40D30C2AC4EF6C2B50DE376A0A9BAAA3A34FC4E85133E4`. Após publicação, `/v3/api-docs` local respondeu 200 application/json com 144 paths/247 schemas; todos os caminhos compartilhados e todos os componentes são idênticos ao export. A única ausência é `/api/v1/webhooks/payments`, habilitado no teste e já desabilitado localmente. Não foi editado manualmente nem trocado o gateway.

`checkstyleMain` e `checkstyleTest` aprovados; execução final `BUILD SUCCESSFUL` em 2m31s. Diferenças rastreadas deste recorte também passaram em `git diff --check`. Nenhum teste interceptado de frontend substitui os testes transacionais de backend ou a verificação da instância publicada.

Publicação local: imagem `canteiroio-api:local-invoice-cancellation-safety-20261028`, container `canteiro-api`, startup bem-sucedido `2026-10-28T10:06:47.412Z` (07:06:47 em São Paulo). Schema V89 conferido antes e após startup; Flyway/seeds owner/demo false, ambiente idêntico ao backup, mesma rede/porta/volume privado e política de reinício. Consulta anônima ao índice de contratos de NF respondeu 401 application/problem+json, código UNAUTHENTICATED e correlationId. Container anterior `canteiro-api-before-invoice-cancellation-safety-20261028` preservado/parado; retorno não foi necessário. Sem login, upload real, cancelamento, baixa, migration, Git push ou deploy remoto.
