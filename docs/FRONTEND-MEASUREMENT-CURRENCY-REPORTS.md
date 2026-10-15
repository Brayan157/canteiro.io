# Moeda e relatórios oficiais — integração do frontend

Atualizado em 15/10/2026. Código implementado, regressão aprovada e **publicação local autorizada concluída em localhost:8081, com V84 aplicada pelo Flyway**. Frontend confirmou OpenAPI vivo sincronizado, tipos regenerados, moedas e PDF/XLSX autenticados com filename oficial; F04-41 concluída. Não adicionar contratos manuais. Evidências e limites: [publicação F04-41](F04-41-LOCAL-PUBLICATION.md).

## Moeda

Base autenticada `/api/v1/company`, tenant exclusivamente da sessão.

- `GET /currencies`: lista `{ code, symbol, displayName, decimalPlaces, defaultCurrency }`. Códigos: `BRL`, `USD`, `EUR`; duas casas para todos. BRL é o padrão.
- `POST /contracts`: `currency` opcional, padrão BRL. Resposta `contract.currency`.
- `PUT /contracts/{contractId}`: omitir moeda ou reenviar a original. Outra moeda retorna `409 Problem Details`, inclusive em rascunho.
- `POST /measurements`: avulsa aceita `currency`, padrão BRL; vinculada deriva `contract.currency` quando omitida, ou valida igualdade. Incompatível retorna `409`.
- `PUT /measurements/{measurementId}/link`: exige mesma moeda do contrato. Nunca converter automaticamente, alterar a moeda ou copiar valores aceitos.

Moeda é imutável desde a criação. Não há câmbio nem endpoint para trocar a unidade de um registro existente. Quando permitido, cancele logicamente um rascunho incorreto e crie outro. A V84 atribui BRL aos registros antigos sem modificar números ou histórico.

`currency` é retornado no contrato, medição, histórico de versões, composição direta, resumo financeiro, controle/lista de faturamento, parcela, título, NF, imposto, baixa, saldo, crédito e renegociação. Itens/serviços e títulos descendentes herdam essa unidade; não têm seletor de moeda independente. Uma resposta `202` não inventa entidade oficial, valores ou composição; a proposta persiste o código resolvido e a aprovação revalida o contexto.

Dinheiro continua **string decimal canônica**, por exemplo `"amount": "8000.00"`, com `"currency": "USD"`. Não enviar `US$`, separador de milhar ou vírgula no JSON. Símbolos são apenas apresentação. Não converter para Number para somar, calcular saldo, desconto ou formatação monetária.

Exemplo de criação vinculada:

```json
{ "title": "Serviços Fábrica de Açúcar", "workId": "uuid", "contractId": "uuid" }
```

O código retornado é o do contrato. Listagens de contratos/faturamento mantêm moeda por linha; não somar BRL+USD+EUR. Consolidações futuras devem separar unidades, sem câmbio implícito. APIs de resultado consolidado de obra ainda pertencem às fases futuras do backlog.

## Downloads e prévias

Rotas existentes, sem mudança do contrato HTTP:

| GET após `/measurements/{measurementId}` | Conteúdo |
|---|---|
| `/accumulated/pdf` | PDF acumulado attachment |
| `/accumulated/spreadsheet` | XLSX acumulado attachment, não importável |
| `/accumulated/spreadsheet/preview` | PDF acumulado inline |
| `/versions/{versionId}/pdf` | PDF somente da versão |
| `/versions/{versionId}/spreadsheet` | XLSX da versão, com metadados para importar a DRAFT atual |
| `/versions/{versionId}/spreadsheet/preview` | PDF inline da versão |

`MEASUREMENTS.EXPORT`, isolamento de tenant, auditoria, `Cache-Control: no-store`, nome pelo título e streaming permanecem. `403` sem permissão e `404` fora do tenant; conflitos de estado/origem usam `409` quando aplicáveis. Não há URL pública. O frontend baixa o Blob e preserva Content-Disposition; não gera ou corrige relatório oficial localmente.

Complemento de 15/10/2026 **publicado e verificado**: CORS expõe `Content-Disposition` junto de `X-Correlation-Id`, somente nas origens autorizadas. O navegador pode ler o filename enviado pelo backend; não contornar com nome fabricado. A emitente do exemplo Empresa Gustavo não possui logo cadastrada; o fallback é correto. O [storage privado local](F08-23-LOCAL-PRIVATE-STORAGE.md) foi preparado com autorização separada e testado após recriação; agora o próprio usuário autorizado pode cadastrar a logo. [Diagnóstico e validação CORS](F04-42-DOWNLOAD-CORS-AND-BRANDING.md).

Dez colunas visíveis: **Item · Situação · Atividade/descrição · Data · Und · m ou m² · kg/m ou kg/m² · Peso (kg) · Preço (símbolo) · Valor (símbolo)**. Título centralizado, logo privada da emitente com proporção/transparência e fallback de nome; serviços em faixas centralizadas, atividades e subtotais oficiais. Enums técnicos e Fórmula ficam fora da apresentação.

Item preserva referência como `5.1`; aceitos são protegidos e pendentes identificados por texto e cor. Und é quantidade operacional nos tipos não UNIT e não muda fórmula/peso/valor. Campos não aplicáveis ficam em traço, sem peso inventado. PDF suporta descrições longas e repete cabeçalho, serviço e referência nas continuações. Cada dinheiro recebe cifra pt-BR, inclusive preços, subtotais, descontos e o resumo aceito/recebido/falta receber.

Os totais vêm do backend: aceitos e variação pendente ficam separados, sem somar snapshots corretivos. `remainingToReceive` não é apenas o faturado em aberto. Indisponível continua null/rotulado, nunca zero fictício.

Complemento exclusivo PDF **publicado e verificado em 15/10/2026**: [identificação textual da empresa emitente](FRONTEND-MEASUREMENT-PDF-EMITTER.md), com nome e CNPJ cadastrado mesmo quando houver logo. Abrange acumulado, histórico e prévias PDF; não altera XLSX, moedas, totais, payloads ou tipos do frontend. O guia registra a imagem publicada, testes, renderizações sintéticas e preservação dos dados/arquivo privado.

## Reimportação do XLSX por versão

O layout novo tem dez colunas visíveis e os campos estáveis ocultos (incluindo Serviço, Tipo, Item ID, Serviço contínuo ID e valores decimais). A folha `_measurement_metadata` é muito oculta e contém medição, versão e moeda. Não remover nem reconstruir metadados/IDs no frontend. O vínculo contínuo de cada item salvo é mantido no servidor pela sua identidade, não pelo título do serviço. Os geradores por versão carregam vínculos em lotes de até 250 itens: serviços de títulos iguais e IDs diferentes permanecem separados, sem carregar todos os vínculos na memória.

O parser reconhece os campos de apresentação e mantém precisão decimal; fórmulas e totais da planilha não são usados como valores oficiais. Cabeçalhos de serviço, subtotais e linhas de continuação não viram itens. A identidade da medição/versão e moeda precisa corresponder ao alvo; divergência retorna `409`. Modelos antigos sem esses metadados permanecem aceitos apenas para medição BRL. Reimportar só a DRAFT atual, por prévia e confirmação já existentes; `202` não altera itens, ordem, moeda ou totais oficiais.

O ID contínuo oculto preserva a referência documental; não é autorização para trocar vínculo/destino por edição de célula. Vínculos já oficiais são mantidos pelo Item ID no servidor. Para atribuir uma atividade nova a um serviço contínuo ou mudar seu destino, use as operações explícitas já existentes no guia de medição contínua; não deduza o vínculo pelo título nem copie um destino financeiro na planilha.

## Verificação e publicação

Regressão de 14/10/2026: **192 testes em 54 suítes, zero falhas/erros/ignorados**, com Checkstyle main/test aprovado. Evidências XML preservadas em `tmp/validation-currency-full`. Inclui:

- BRL/USD/EUR na criação, composição, parcela, NF, imposto, recebimento e estorno;
- moeda derivada e explícita, bloqueio de incompatibilidade/troca, aprovação de contrato e medição sem alteração em 202, autorização e dois tenants;
- upgrade real 83→84 em PostgreSQL descartável, preservando valores e linhas históricas e provando constraints/triggers;
- logos privada/ausente/inválida, isolamento da emitente, proporção e tipos mistos com peças operacionais;
- identidade distinta apesar de títulos iguais, revisão vazia/correções, totais financeiros e round-trip do XLSX, inclusive dinheiro acima da precisão inteira de ponto flutuante.

Inspeção visual: páginas completas dos PDFs BRL/USD/EUR (duas por moeda), nove páginas do relatório longo, título/nome de emitente longos, XLSX acumulado e por versão. Conferidos logo/fallback, centralização, continuação, unidades, ausência de enums visíveis, subtotais e cifras em todo dinheiro. As amostras são sintéticas, sem gravação comercial real.

Após o ajuste final exclusivamente visual de alinhamento numérico no XLSX, os **nove testes dos quatro geradores/identidades** foram repetidos sem falhas e as planilhas acumulada/por versão foram renderizadas e conferidas novamente. Isso não é uma contagem de testes únicos adicionais aos 192 da regressão.

O export inicial do `OpenApiContractIntegrationTest` tinha **OpenAPI 3.1.0, 124 paths, 193 schemas**, SHA-256 `4C19DC6650DC7C82F1650A054EDFF971491C51DEB7BB0AEB05DAFD3A65D36892`. O teste valida também bearerAuth, Problem Details e dinheiro em texto nas requisições de alocação/NF/baixa/imposto e nas respostas financeiras.

Após autorização humana, o build foi publicado em `http://localhost:8081` e o Flyway aplicou somente a V84 em 14/10/2026 às 21:49 (America/Sao_Paulo). `postman/canteiro-io.openapi.json` agora contém a resposta **da instância publicada**, com **OpenAPI 3.1.0, 123 paths, 193 schemas**, SHA-256 `7166C8DED235CA31E6C3B517C5DDF1F7C68C0A034F72692F1781D21B9B75D878`. O path adicional do teste é `/api/v1/webhooks/payments`, condicionado ao Asaas habilitado; ele permanece desabilitado no ambiente local. Os contratos de moedas/relatórios estão presentes.

PostgreSQL, Mailpit e volumes existentes não foram recriados; os registros anteriores permaneceram numericamente e historicamente intactos. O frontend confirmou em 15/10/2026 a sincronização de `canteiro-front/openapi/canteiro.openapi.json`, regeneração dos tipos, catálogo BRL/USD/EUR, seletor USD sem salvar entidade e downloads autenticados com nomes oficiais. O PDF tem 3.411 bytes e o XLSX 5.949 bytes; a conferência do frontend preservou dois serviços/cinco atividades, dez colunas, centralização e cifras, inclusive após a recriação de storage. F04-41 concluída; não editar tipos gerados manualmente. A logo real permanece aguardando cadastro pelo usuário/interface do frontend, separadamente da integração validada. Consulte o [registro da publicação](F04-41-LOCAL-PUBLICATION.md) para atribuição das evidências, testes e avisos de segurança.
