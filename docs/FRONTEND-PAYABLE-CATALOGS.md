# F06-01 — Fornecedores e categorias financeiras

Data: 16/10/2026. Implementação, testes e contrato concluídos, ainda sem publicação na instância local. Este recorte prepara os cadastros da Fase 6; não cria gastos, títulos, pagamentos, resultados ou integração bancária.

## Rotas

| Método | Fornecedores | Categorias |
|---|---|---|
| GET paginado | `/api/v1/company/suppliers` | `/api/v1/company/financial-categories` |
| GET detalhe | `/api/v1/company/suppliers/{id}` | `/api/v1/company/financial-categories/{id}` |
| POST | `/api/v1/company/suppliers` | `/api/v1/company/financial-categories` |
| PUT | `/api/v1/company/suppliers/{id}` | `/api/v1/company/financial-categories/{id}` |
| DELETE lógico | `/api/v1/company/suppliers/{id}?expectedVersion=0` | `/api/v1/company/financial-categories/{id}?expectedVersion=0` |

Todas exigem autenticação e tenant derivado da sessão, nunca `companyId` no body. Fornecedores usam permissões `PAYABLES`; categorias usam `COSTS_EXPENSES`. GET exige READ. POST, PUT e DELETE usam respectivamente alçadas CREATE, UPDATE e CANCEL existentes. Solicitação+APPROVE do mesmo módulo/operação também concede aplicação direta efetiva.

## Fornecedor

POST:

```json
{ "name": "Fornecedor de materiais", "document": "AB-1234", "justification": "Cadastro para futura despesa" }
```

Nome obrigatório, até 255 caracteres. Documento opcional, até 80 caracteres de entrada; letras/dígitos e separadores usuais (`.`, `/`, hífen, espaço). A API remove separadores, converte letras para maiúsculas e guarda até 50 caracteres. Aceita identificador estrangeiro; não realiza validação de dígitos verificadores CPF/CNPJ. Documento não é chave única: duplicados são permitidos e não unem fornecedores nem empresas. Não há dados bancários, Pix ou anexos neste recorte.

PUT substitui nome/documento e exige `expectedVersion` (inteiro não negativo). Documento omitido ou nulo remove a identificação opcional; envie o valor atual se quiser preservá-lo. Não trocar ID nem criar outro fornecedor para fazer edição.

`SupplierResponse`: `id`, `name`, `document` opcional/nulo, `active`, `version`, `createdAt`, `updatedAt`. Datas em UTC. Após desativação, o detalhe continua disponível no tenant, preservando histórico; edição e nova desativação recebem `409`.

## Categoria

POST:

```json
{ "code": "EPI", "name": "Equipamentos de proteção", "type": "WORK_COST", "description": "Classificação administrativa" }
```

Código obrigatório de até 50 caracteres: letras ASCII, dígitos, `_` ou `-`, começando por letra/dígito. Normalizado para maiúsculas e único por empresa, inclusive após desativação. Nome obrigatório de até 255 caracteres; descrição opcional até 1.000. Tipos:

- `WORK_COST`: custo de obra;
- `WORK_EXPENSE`: despesa de obra;
- `COMPANY_EXPENSE`: despesa da empresa;
- `TAX`: imposto, que pode depois estar associado a escopo WORK ou COMPANY.

Código e classificação são imutáveis. PUT aceita somente `expectedVersion`, nome, descrição e justificativa opcional; não envia código/tipo para reclassificar registros históricos. Para outra classificação, crie outra categoria. Esta classificação não substitui `Payable.financialScope`, não seleciona obra/contrato e não altera lucro ou fluxo de caixa.

`FinancialCategoryResponse`: `id`, `code`, `name`, `description` opcional/nula, `type`, `active`, `version`, `createdAt`, `updatedAt`. Nenhuma categoria padrão é criada automaticamente para as empresas neste recorte.

## Listagens

Retornam `PageResponse<SupplierResponse>` / `PageResponse<FinancialCategoryResponse>`, com `items` e paginação padrão.

- `page >= 0`, padrão 0; `size` entre 1 e 100, padrão 20.
- `sort`: somente `name`, `createdAt`, `updatedAt`, com `asc` ou `desc`. Padrão `name,asc`; desempate por ID ascendente.
- `name`: trecho literal, sem distinguir maiúsculas/minúsculas; `%` e `_` não são curingas.
- `active`: padrão true; false lista inativos. O detalhe inclui inativos, sem filtro adicional.
- Fornecedor: `document` opcional, exato após normalização.
- Categoria: `type` opcional, um dos quatro tipos oficiais.

## Aplicação direta, aprovação e concorrência

- POST direto: `201`, com `supplier`/`category`, `mode=DIRECT` e `changeRequestId=null`.
- PUT direto: `200`, com entidade oficial e versão atualizada.
- DELETE direto: `204`, sem corpo. Apenas `active=false`; banco impede DELETE físico.
- Quando depender de aprovação: `202`, com `changeRequestId`, `mode=REQUEST_APPROVAL` e entidade oficial nula. Nada aparece/muda/desaparece na listagem até aprovação.
- Aprovar usa o fluxo `/api/v1/company/change-requests/{id}/approve`; retorna `appliedEntity` com ID/nome oficial. Autoaprovação continua proibida.
- PUT/DELETE e aprovação validam versão otimista; desatualização gera `409`, sem sobrescrever o dado vigente. Código de categoria duplicado também gera `409`, inclusive em concorrência ou na aprovação de proposta antes válida.
- Erros `400/401/403/404/409`: `application/problem+json` com `correlationId`. Estrangeiros ao tenant retornam `404` sem revelar existência.

Mutações diretas/aprovadas geram auditoria com nome legível como snapshot. Eventos não incluem documento completo do fornecedor nem descrição livre da categoria; guardam presença/alteração e metadados seguros. O payload da proposta é privado ao fluxo de aprovação existente.

## Estado e entrega

Migration aditiva `V86__add_suppliers_and_financial_categories.sql`, sem modificar V85 ou as anteriores. Este recorte F06-01 não incluía lançamento de Payable. A implementação posterior F06-02 a F06-06, com WORK/COMPANY, grupo financeiro e vínculos opcionais a fornecedor/categoria, está no [guia de títulos](FRONTEND-PAYABLES.md).

Validação final: **35 testes em 14 suítes**, zero falhas, erros ou ignorados; Checkstyle main/test sem erros. Inclui os dez testes próprios (dois de domínio e oito de API), Flyway até V86, OpenAPI, governança/auditoria/suporte e sete cenários de faturamento líquido. O conflito concorrente de código recebe `409`, sem duplicar registros. Foram corrigidas fixtures antigas de auditoria (usuários precisam estar ativos) e de aprovação (contrato real na cadeia cliente → obra → contrato), sem enfraquecer regras de produção.

OpenAPI gerado de `/v3/api-docs` da aplicação de integração: `postman/canteiro-io.openapi.json`, **3.1.0, 131 paths e 215 schemas**, gateway desabilitado como na configuração local. O teste geral também validou o perfil com gateway habilitado, em artefato separado. Respostas explícitas `application/json`, Problem Details e união objeto/nulo nas respostas `202`; não fabricar tipos no frontend.

SHA-256 do snapshot de export da F06-01 (o arquivo atual evoluiu; consultar o [guia de títulos F06-02](FRONTEND-PAYABLES.md) para hash/contrato posteriores):

```text
2A073C447824FA500D8538D874D8C0B72377AF5C4A09210968B668EAB8C6FDE3
```

Evidências locais ignoradas pelo Git: `tmp/validation-payable-catalogs/final`. PostgreSQL/Testcontainers foi usado somente para testes isolados; não foram executados deploy, reinício solicitado da API, push, aplicação manual de migrations ao banco real, criação de dados reais ou alteração do frontend. O uso das novas rotas na instância antiga depende de publicação autorizada, incluindo as migrations V85/V86 ainda não publicadas por este agente.


Atualização F06-20/21 (06/11/2026): [contatos e instruções privadas de pagamento](FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md), com [export/pacote/testes atuais e ativação pendente](F06-20-21-VALIDATION-2026-11-06.md). Os hashes acima são snapshots das entregas anteriores; o arquivo OpenAPI atual inclui o contrato novo. Não confundir instrução de pagamento com baixa ou com publicação na API local.
