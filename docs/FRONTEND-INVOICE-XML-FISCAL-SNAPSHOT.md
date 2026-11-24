# Evidência fiscal da NFS-e nacional

O recorte F05-42 preserva fatos fiscais extraídos de um XML privado já anexado a uma NF registrada. A confirmação guarda o vínculo com o anexo, a NF e o contrato. Cobrança, serviço e composição são rastreados pelos `InvoiceItem` e suas alocações existentes. Os valores documentais permanecem no nível da NF.

## Integração

| Operação | Contrato |
| --- | --- |
| `GET /api/v1/company/invoices/{invoiceId}/xml-fiscal-snapshot` | Requer `INVOICES.READ`. Responde `200` com a evidência e `Cache-Control: no-store`, inclusive se a NF foi cancelada. Responde `404` para NF de outro tenant, inexistente ou sem evidência. |
| `PUT /api/v1/company/invoices/{invoiceId}/xml-fiscal-snapshot` | Enviar `{ "attachmentId": "UUID" }` de um XML legado já vinculado. Requer `INVOICES.READ` e alçada efetiva direta de criação de NF. Primeira confirmação `201`, reenvio do mesmo anexo `200`, anexo diferente após confirmação `409`. |

O fluxo de registro da NF continua com `201` direto e `202` quando exigir aprovação. A confirmação usa o XML privado já existente e verifica tamanho e hash. A rota de anexos aceita apenas novos PDFs. A entrada de novos fatos fiscais depende de um fluxo próprio ainda planejado. Nenhuma soma ou extração do XML é exigida do frontend.

Respostas de erro usam Problem Details. `400` indica payload ou XML inválido para o perfil. `403` indica falta de permissão ou alçada. `404` inclui anexo de outra NF ou empresa. `409` indica NF cancelada na primeira confirmação, incompatibilidade fiscal, identidade duplicada ou evidência já vinculada a outro anexo. `503` indica conteúdo indisponível ou falha de integridade no storage.

## Campos preservados

- NF, contrato, anexo, moeda, perfil da fonte, autor e data UTC da confirmação.
- Emissão e série cadastradas na NF separadas do processamento do XML e da numeração, série, emissão e competência da DPS.
- Bruto, descontos, dedução declarada quando presente, retido total, líquido, valor comercial compatível e percentual informativo do Simples em campo separado.
- ISSQN, PIS, COFINS, CP, IRRF e CSLL quando identificados. Para cada componente, a estrutura distingue base, alíquota, valor, indicador e situação de retenção. O responsável declarado pelo ISSQN aparece quando o código identifica tomador ou intermediário.
- Códigos de serviço nacional/municipal e NBS, presença de assinatura, indicador de substituição, motivo e impressão digital da referência substituída, quando disponíveis.
- Cada valor extraído inclui `value` e `sourcePath`. Campos ausentes representam informação desconhecida. Valores financeiros usam strings decimais.

A chave fiscal é confrontada com a NF quando já cadastrada. O banco usa uma impressão digital para impedir duplicidade por empresa, enquanto o XML original guarda a chave completa. A resposta omite a chave, documentos das partes, texto livre do XML e caminho de storage. O download do anexo continua sujeito à autorização própria.

O perfil suportado é NFS-e nacional 1.01. Estruturas incompatíveis, inclusive IBS/CBS fora do perfil de reconciliação, exigem conferência. A evidência confirma a correspondência documental verificada pela API. Validação fiscal externa, assinatura e XSD permanecem fora desse fluxo. [Documentação de produção da NFS-e](https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual).

## Efeito financeiro e publicação

O snapshot é imutável e separado de retenção oficial, obrigação tributária, crédito, despesa, baixa e recebimento. A confirmação jamais cria esses lançamentos. Relatórios futuros podem usar a evidência para conferência e os lançamentos oficiais para calcular resultado. O fluxo atômico completo de cadastro da NF, anexação e decisões tributárias continua pendente.

O teste de integração passou em PostgreSQL descartável com a V94. Ele cobre vínculo, permissão, isolamento, divergência, confirmação, reenvio idêntico, imutabilidade e ausência de efeitos financeiros. O Checkstyle e o teste contratual completo também passaram.

O [export OpenAPI](../postman/canteiro-io.openapi.json) foi gerado por `/v3/api-docs` em teste. É OpenAPI 3.1.0, com 157 paths e 283 schemas. Seu SHA-256 é `8CA2AF496AC8D3673DB945CE6482946E3A3C6FD901150D9E12EB25AE1BA0BF0D`. As operações e schemas anteriores passaram pela comparação com o export anterior.

A instância local isolada de F05-39/40/41 ainda não oferece essas rotas. A publicação exige a V94 no banco persistente e autorização específica.
