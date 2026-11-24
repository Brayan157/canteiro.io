# F05-32 — Ativação somente local em 29/10/2026

Autorização humana confirmada diretamente na conversa do frontend: **“Sim, aplicar V90 e ativar localmente”**, em resposta à pergunta que delimitava V90 aditiva, API8081, gate do frontend, seeds desligados e nenhuma operação financeira real. Não autoriza publicação remota nem outras migrations.

## Resultado verificado

- Somente V90 aplicada pelo Flyway do pacote aprovado, com target90, validação de checksums, sem baseline automático, clean ou repair. V1–V89 válidas; exatamente uma migration pendente/aplicada. Checksum Flyway V90: `1503018190`.
- Contagem de registros de todas as tabelas públicas anteriores, exceto histórico técnico Flyway, igual antes/depois da migration. As duas novas tabelas estão vazias. Sem backfill ou atribuição de recebimentos históricos.
- API iniciou em **29/10/2026 às 08:19:32, America/Sao_Paulo** (`2026-10-29T11:19:32.907Z`). Imagem `canteiroio-api:local-manual-invoice-settlements-20261029`, ID `sha256:701376c965ca028e21fe41a97785d1767df0e00c657c4a50b77c1955ee92d48c`.
- JAR ativo SHA-256 `0E25A12A80175D955C17383E1859A8CFB1754A51700B7CD77864275537D576ED`. Imagem montada exclusivamente por cópia desse JAR sobre a base anterior, sem recompilar o workspace.
- V90 extraída do pacote: SHA-256 `5787A8ACEC8571307F268A73BA1A94669287B254C0D69EAAA14C4585B941D2BB`.
- Ambiente idêntico ao container anterior; Flyway automático, seed owner e seed demo continuam desligados. Porta8081→8080, rede `canteiroio_default`, volume privado `canteiroio-api-private-storage` em `/app/var/storage` e restart policy preservados.
- `/v3/api-docs`: **200 application/json**, OpenAPI3.1.0, 146paths/255schemas. Todos os caminhos compartilhados e componentes são estruturalmente idênticos ao export aprovado de147paths. Única ausência prevista: webhook de gateway local desabilitado `/api/v1/webhooks/payments`. Sem caminhos extras ou diferenças inesperadas. `note.maxLength=1000`.
- Export consumível pelo frontend permanece `postman/canteiro-io.openapi.json`, SHA-256 `27037B8762036EA7F6B695D5D6A966F92BB73C602F31DBB8055DD8764833B607`; não foi editado para remover o webhook.
- Probes anônimos de GET opções, POST novo recebimento com corpo vazio e GET índice de contratos de NF: **401 application/problem+json**, `UNAUTHENTICATED`, `correlationId` presente. Sem login ou operação autenticada.

## Integração

Frontend pode habilitar **somente sua configuração local** autorizada, usando [o contrato manual](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md). Novo fluxo usa exclusivamente `/invoice-settlements`; POST legado `/settlements` e seu indicador continuam bloqueados. Mantêm-se aprovação202 sem alteração financeira, valores manuais por origem exata, idempotência, limites oficiais e indisponibilidade honesta de histórico sem prova.

Fechamento coordenado em29/10: a conversa do frontend confirmou habilitação **somente em `.env.local`** de `VITE_ENABLE_INVOICE_SETTLEMENTS=true`, após receber estas provas. Relatório frontend consultado em `canteiro-front/docs/F08-21-RECEBIMENTO-MANUAL-POR-NF.md` registra conferência independente por GET do OpenAPI200/146paths/255schemas, componentes/caminhos compartilhados idênticos ao export final e opções anônimas401. O carregador oficial Vite e GET do módulo servido emlocalhost5173 confirmaram gate ativo e destino API8081. Configuração compartilhada/de exemplo mantém fallbackfalse. Essas são evidências reportadas pelo frontend e verificadas no seu relatório, não reexecuções backend nem deploy remoto.

Sem homologação autenticada real e sem registro/estorno financeiro, upload ou envio de e-mail executado por esta ativação. Os89 testes distintos/11suítes e19 reexecuções finais/3suítes são as validações anteriores do mesmo pacote, não uma nova rodada contra dados reais. O frontend reporta152 testes distintos/10arquivos nas rodadas identificadas,14E2E distintos e smoke final1/1 já contabilizado entre esses14; não somar reexecuções nem apresentar os mocks como operações reais.

## Recuperação e evidências

Container anterior preservado e parado: `canteiro-api-before-manual-invoice-settlements-20261029`, JAR FCEEA62E..., imagem F05-36. Cópia de recuperação privada e ignorada pelo Git, com ACL restrita ao usuário atual; arquivo não é entregue ao frontend. Arquivo de backup possui592165bytes e listagem pg_restore validada; **restauração não foi ensaiada nesta ativação**. Não houve restauração nem remoção de dados.

O retorno tem os limites do [plano V90](F05-32-CANDIDATE-AND-MIGRATION-PLAN.md): após novos vínculos gravados, não iniciar o build antigo para executar mutações financeiras/estornos. Preservar ledger e operações; nunca limpar tabelas ou restaurar backup sobre operações atuais automaticamente.

Provas técnicas locais em `tmp/invoice-manual-settlement-candidate/`: `flyway-preflight.log`, `flyway-migrate.log`, `recovery-evidence.json`, `startup.txt`, `publication-runtime.json`, `live-openapi.json` e `live-contract-evidence.json`. Runner isolado não inicia Spring, jobs ou seeds; usa somente bibliotecas e migrations extraídas do JAR aprovado.
