# F07-04 — Ativação local da correção do relatório de faturamento

**29/10/2026, 14:30:04 em America/Sao_Paulo (17:30:04 UTC). Concluída somente na API local localhost:8081.**

## Autorização e alcance

Autorização humana confirmada diretamente em “Fase 8 — Faturamento, notas fiscais…”, chat `01a140fd-d248-7433-bf7e-dd3c8defaa0c`, mensagem `01a188b2-4e02-7882-8001-8dffdad23c6e`: **“então manda ele fazer”**. Contexto anterior mostrava explicitamente que o PDF corrigido dependia de V91 no banco local e que depois o backend atualizaria aAPI8081. Aplicados somente aV91 e o JAR revisado, com backup/preservação e sem seeds ou lançamentos reais. Não autoriza produção ou operações financeiras adicionais.

[Plano e candidato revisados](F07-04-REVISION-CANDIDATE-AND-MIGRATION-PLAN.md). Baseline novamente conferido por leitura às14:27:08SãoPaulo: JAR351C5B5A..., imagem a641a941..., schema90/checksum1503018190. Hash do candidato386004E6... conferido antes e depois da publicação. Não houve recompilação da aplicação nem alteração de dependências: a imagem deriva da anterior e copia somente o JAR aprovado.

## Migração e backup

Flyway isolado, sem iniciar Spring/schedulers/seeds: preflight confirmou90migrations aplicadas, checksums válidos e **somente V91 pendente**. API pausada durante backup/migration para impedir novasNFs no intervalo.

- Backup custom PostgreSQL fora do OneDrive: `C:/Users/oqdel/AppData/Local/Canteiro/Backups/F07-04-V91-20261029/canteiro-before-v91.dump`.
- 617358bytes, SHA256 `2575A823B4A040C2106B3D17B38843480841223B1AB3C980C19AF1191E50D373`.
- ACL conferida: acesso permitido somente ao usuário atual. Cópia igual ao arquivo do container pelo SHA256; listagem e leitura completa do arquivo via pg_restore verificadas. **Não foi executada uma restauração de teste.** Não publicar/versionar/distribuir o backup.
- V91 aplicada uma vez,90→91; checksumFlyway **-1539369050**, validação posterior bem-sucedida, zero migrations com falha e nenhuma pendente.
- Contagens de todas as tabelas operacionais preexistentes permaneceram iguais durante a janela. Sem backfill, alteração de dados antigos ou exclusão física. Tabela nova `invoice_financial_snapshot` vazia e trigger de imutabilidade presente.

A migration veio do JAR revisado. Não foram editadas/reparadas migrations aplicadas, nem executados clean/reset/restore.

## API e contrato

- JAR ativo: **386004E6DD464719D71789CC181804AF117837F166B0B1E863BECA1DE7FBC14F**.
- Imagem: `canteiroio-api:local-billing-pdf-revision-20261029`.
- Image ID: `sha256:a55290a3d070a3cff5cd1028a38611fb628caa9add73aa5985e0e39c772d56c0`.
- Container: `9bc280c45ca3f577a4d4cac8894de23688555d39eb8750d4a73d40e4403b7d9b`.
- Inicialização confirmada em18.271segundos; processo iniciou17:29:45UTC, pronto17:30:04UTC.
- Ambiente idêntico ao anterior; rede `canteiroio_default`, volume `canteiroio-api-private-storage` em `/app/var/storage`, porta8081→8080 e restart `unless-stopped` preservados.
- `SPRING_FLYWAY_ENABLED=false`, `BOOTSTRAP_PLATFORM_OWNER_ENABLED=false`, `BOOTSTRAP_TRUST_UNLOCK_DEMO_ENABLED=false` no serviço. Flyway somente no runner controlado.
- `GET /v3/api-docs`: **200 application/json**, OpenAPI3.1.0,147paths/257schemas. Todos os caminhos compartilhados/componentes idênticos ao exportE284...148paths/257schemas. Única ausência esperada: webhook de pagamentos desabilitado localmente.
- Export oficial: `postman/canteiro-io.openapi.json`, SHA256 **E284551152BB5653E2EF542ECE3B38AD49A1BE10BD21CC013E1D8D84DE789944**. Descrição da rotaPDF nova conferida; schemas/HTTPpreservados.
- `GET /api/v1/company/billing-control/contracts/00000000-0000-0000-0000-000000000001/report/pdf` sem autenticação: **401 application/problem+json**, `UNAUTHENTICATED`, correlationId presente.

Não houve login, exportação autenticada de cliente real, NF, baixa, estorno, upload ou outro lançamento financeiro executado pelo agente. A conferência autenticada do relatório real fica com usuário/frontend. A referência de código continua47testes distintos/5suítes aprovados emrodadas, Checkstyle e quatro páginas sintéticas renderizadas/conferidas; não foi reexecutada toda a suíte durante a ativação.

## Preservação e recuperação

Container anterior351C5B5A preservado **parado** como `canteiro-api-before-billing-pdf-revision-20261029`; restart alterado para **no** para impedir que volte automaticamente como escritor antigo. JAR/imagem e banco anterior aoV91 preservados; nenhum deles foi removido/restaurado.

Não iniciar o pacote antigo para novasNFs: ele não captura o snapshot exigido apósV91. Em falha, preservar banco/snapshots e suspender escritas até correção; eventual uso somente para consulta requer bloqueio efetivo de escrita e decisão própria. Não apagar tabela/histórico, reparar migration ou restaurar dados por cima automaticamente.

## Evidências e frontend

Diretório `tmp/contract-billing-pdf-revision-candidate`: `preactivation-baseline.json`, `flyway-preflight.log`, `flyway-migrate.log`, `recovery-evidence.json`, `publication-start.json`, `publication-runtime.json`, `startup.txt`, `live-openapi.json`, `live-contract-evidence.json`, comparação do pacote e validação.

Frontend avisado no chat original após verificação: pode baixar novamente pela mesma rota. Já havia conferido as quatro páginas sintéticas e atualizado o export/tipos; não há novo consumo ou rebuild de frontend necessário. [Guia atual](FRONTEND-CONTRACT-BILLING-PDF.md).
