# Imports do Postman — Canteiro.io

Importe estes arquivos no Postman:

1. `canteiro-io.bootstrap.postman_collection.json` — cenário guiado completo: catálogo, onboarding, funcionário solicitante, aceite do cliente e medição.
2. `canteiro-io.local.postman_environment.json` — environment local com URL, credenciais de teste, tokens e IDs encadeados.
3. `canteiro-io.postman_collection.json` — coleção completa, organizada por controller/tag.

O arquivo `canteiro-io.openapi.json` também pode ser importado diretamente caso seja necessário recriar a coleção a partir do contrato OpenAPI.

Selecione o environment **Canteiro.io Local** antes de executar as requisições. Na coleção guiada, execute as pastas na ordem: os scripts salvam automaticamente os IDs e trocam o token entre plataforma, proprietário e funcionário.

Os dois endpoints de ativação exigem o token recebido por e-mail. Copie o token da caixa configurada (no Docker local, Mailpit em `http://localhost:8025`) para `companyOwnerActivationToken` e `employeeActivationToken` antes de executar as respectivas requisições. Esses tokens nunca são retornados pela API.

Para repetir o cenário com novos documentos/e-mails, mantenha `resetRunId` como `true` e execute a primeira requisição. Ela gera uma nova identificação de execução; depois o próprio script muda essa variável para `false`.

Para regenerar a coleção depois de exportar um OpenAPI atualizado:

```powershell
node scripts/generate-postman.mjs
node scripts/generate-postman-bootstrap-scenario.mjs
```
