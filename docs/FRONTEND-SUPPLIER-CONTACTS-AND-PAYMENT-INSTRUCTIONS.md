# Contatos do fornecedor e instruções para pagamento

Contrato backend para FRONT-F09-17/18. Implementação e testes concluídos em 06/11/2026; esta versão ainda não está publicada em localhost:8081. Não interpretar este guia como autorização para operar banco persistente ou registrar pagamentos.

## Contato principal do fornecedor

As rotas existentes de fornecedor, sob `/api/v1/company/suppliers`, recebem/devolvem três campos opcionais: `contactName` (120 caracteres), `contactPhone` (40) e `contactEmail` (254, e-mail válido). Não há lista de contatos nem vínculo com funcionário. Não preencher automaticamente dados de pagamento a partir do fornecedor.

POST mantém 201 direto/202 por aprovação. PUT mantém 200/202 e `expectedVersion` do fornecedor. É substituição completa: carregar o detalhe e reenviar os contatos atuais ao editar outro campo; null/omitido limpa o campo. DELETE continua desativação lógica e preserva os contatos históricos. Permissões PAYABLES já existentes; consultas não autorizam alteração. Auditoria registra indicadores, sem copiar telefone/e-mail/nome do contato.

## Instrução do título oficial

Base: `/api/v1/company/payables/{payableId}/payment-instructions`.

| Método/rota | Resultado |
|---|---|
| GET base | 200 somente metadados, com PAYABLES.READ. Título existente sem instrução: `exists=false`, `version=0`. |
| PUT base | 200 com metadados oficiais e `mode=DIRECT`, ou 202 somente `changeRequestId` e `mode=REQUEST_APPROVAL`. |
| POST base/reveal | 200 com `instruction` (metadados) e `details` completos. Leitura explícita, auditada, sem cache. |
| POST base/boleto | Multipart: `file`, `expectedVersion`, `justification` opcional. 200/202. |
| GET base/boleto | PDF privado autenticado, `attachment; filename="boleto.pdf"`, sem cache, sem URL pública. |

As rotas de escrita e leitura completa exigem **PAYABLES.READ + PAYABLES.VIEW_PAYMENT_DETAILS**. Escrita exige adicionalmente alçada UPDATE (UPDATE_DIRECT ou REQUEST_UPDATE; REQUEST_UPDATE+APPROVE é direta). Aprovação exige APPROVE, READ e VIEW_PAYMENT_DETAILS, além de proibir autoaprovação. A nova permissão é adicionada ao catálogo e aos papéis `Company Administrator`; outros papéis precisam de concessão explícita pelo administrador.

`expectedVersion` é a versão **da instrução**, independente de `Payable.version`. Use 0 na primeira gravação. PUT sempre substitui todo o conteúdo; não fazer PUT usando metadados ou formulário vazio. Revelar explicitamente antes de editar; se a permissão faltar, não oferecer edição. Nova instrução não herda PDF anterior: preserva-o apenas no histórico, para evitar associação silenciosa de boleto de outro favorecido. Anexar PDF mantém o conteúdo e cria outra revisão da instrução.

Exemplo de PUT (dados sintéticos):

```json
{
  "expectedVersion": 0,
  "details": {
    "type": "BANK_TRANSFER",
    "bankName": "Banco informado",
    "branch": "0001",
    "accountNumber": "12345",
    "accountCheckDigit": "6",
    "accountType": "CHECKING",
    "holderName": "Favorecido informado"
  },
  "justification": "Referência para pagamento manual"
}
```

Metadados: `exists`, `id` opcional, `version`, `type` opcional, `hasPdf`, `updatedAt` UTC opcional. Não contêm chave Pix, conta, linha, hash, storageKey ou dados do favorecido. O detalhe do Payable e suas listagens também não ganham esses segredos. O GET de metadados não comprova que a instrução tem referência utilizável.

## Campos condicionais de details

| Tipo | Campos permitidos/regras |
|---|---|
| BOLETO | `digitableLine`: 47 ou 48 dígitos, **ou** `barcode`: 44 dígitos. Ambos são opcionais para permitir PDF-only; não enviar os dois. Não há emissão nem validação bancária dos dígitos verificadores/beneficiário. |
| PIX | `pixKeyType` + `pixKey` **ou** `pixCopyPaste`, nunca ambos. Tipos CPF (11 dígitos), CNPJ (14), EMAIL, PHONE (E.164), RANDOM (UUID). Chave até 254, copia-e-cola até 1024 caracteres. Não resolve titular, não gera QR/Pix/cobrança e não confirma validade bancária do texto recebido. |
| BANK_TRANSFER | `bankCode` (8) ou `bankName` (120), `branch` (20), `accountNumber` (30), `accountType` (CHECKING/SAVINGS/PAYMENT), `holderName` (160). `branchCheckDigit` e `accountCheckDigit` opcionais (5 cada). |

Campos de outro tipo são rejeitados (400). Campos opcionais podem ser null. `justification` tem limite de 1000. Os dados são fornecidos e conferidos pelo usuário; o sistema não infere favorecido nem executa pagamento.

### Fluxo boleto somente PDF

1. Criar o título e aguardar aplicação oficial (201, ou aprovação da proposta).
2. PUT da instrução com `details={"type":"BOLETO"}` e versão esperada; aguardar aplicação oficial se houver 202.
3. POST multipart do PDF com a versão oficial da instrução. Até o PDF oficial ser anexado, `hasPdf=false`; sem linha/barras, ainda não existe referência utilizável para pagar.

PDF: extensão `.pdf`, MIME `application/pdf`, até 10 MiB, conteúdo legível com ao menos uma página e sem criptografia de documento. A estrutura é validada com PDFBox; não há verificação bancária, assinatura ou antimalware nesta funcionalidade. O conteúdo é cifrado no storage privado; nome original não é persistido/exposto. O streaming autenticado usa nome fixo seguro. O limite multipart do ambiente pode ser menor (inclui overhead do formulário).

### Inspeção da proposta pendente

POST `/reveal?changeRequestId=...` e GET `/boleto?changeRequestId=...` inspecionam exclusivamente uma proposta PENDING do mesmo título/tenant. Além de READ+VIEW_PAYMENT_DETAILS, somente o solicitante ou usuário com APPROVE pode consultá-la. A leitura é auditada. Proposta estrangeira/de outro título/decidida retorna 404. Nenhum upload pendente deve aparecer como documento oficial antes da aprovação; propostas rejeitadas e seus artefatos permanecem privados, preservados para histórico.

## Finanças, concorrência e segurança

- Criação de título e instrução são operações separadas; não simular uma transação entre elas. Cada título de um lote recebe sua própria instrução, sem herança automática. Falha na instrução não apaga nem quita o título criado.
- Gravação/anexo permitidos em OPEN, OVERDUE e PARTIALLY_PAID. PAID, CANCELLED, RENEGOTIATED e DRAFT bloqueiam gravação (409). Leitura histórica continua permissionada.
- A instrução não muda valor, vencimento, fornecedor, escopo WORK/COMPANY, moeda, versão ou status do título. Não cria baixa, recorrência ou notificação. O meio efetivo da futura baixa será contrato separado.
- Revisões são append-only, com locks do título, controle de versão e constraint única. 202 não altera instrução, título ou PDF oficial; aprovação revalida versão e situação. Conflito retorna 409. Não existe DELETE físico.
- JSON e PDF são cifrados com chave independente de JWT/RH, AES-GCM e contexto do tenant/título; propostas armazenam pacote protegido. Auditoria registra ação/ator/data/tenant/título/revisão e indicadores, sem conteúdo, chave, conta, código, payload ou nome do arquivo.
- Respostas completas e downloads usam Cache-Control no-store. Frontend não deve persistir em localStorage/IndexedDB, logs, telemetry ou cache de queries persistente; descartar conteúdo/Blob ao fechar. Não há URL pública. As rotas não reutilizam endpoints ou cadastros de RH.
- 400, 401, 403, 404 e 409 seguem Problem Details com correlationId; não informar tenant no payload. Company vem exclusivamente da sessão.

## Dependências de publicação

Nova migration V92, chave `PAYABLE_PAYMENT_DATA_ENCRYPTION_KEY` independente (mínimo 32 caracteres) e build correspondente. Sem chave válida as operações protegidas falham fechadas, sem gravar/revelar conteúdo; não se usa a chave JWT ou de RH como fallback. Manter a chave em cofre/variável e preservar versões para futura rotação; perder a chave impede ler histórico.

Criar/testar V92 em PostgreSQL temporário não autoriza sua aplicação no banco persistente. A API local antiga não deve ser substituída pelo build novo antes de autorização específica, preparação da chave e conferência de compatibilidade. OpenAPI final exportado da aplicação de teste por `/v3/api-docs`, em `postman/canteiro-io.openapi.json`: 3.1.0, 154 paths/268 schemas, SHA-256 `152963366B736AC2039320BC8E3E40E9ACA0E8DA9C879A04333987F92F285183`. [Pacote, testes e plano de ativação pendente](F06-20-21-VALIDATION-2026-11-06.md). Validar instância local separadamente após publicação autorizada.


Evolução do export em06/11: o hash15296336 acima é o snapshot da entrega F06-20/21. O arquivo compartilhado atual passou a incluir F05-39/40/41: [contrato e hash vigentes](FRONTEND-INVOICE-XML-AND-COVERAGE.md). Isso não aplica V92 nem publica os recursos de fornecedores/instruções na instância local.
