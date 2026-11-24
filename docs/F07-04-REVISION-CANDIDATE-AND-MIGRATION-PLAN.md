# F07-04 — Candidato corrigido e plano de migration/publicação local

29/10/2026. **Implementação, testes, contrato, QA e publicação local concluídos.** Autorização humana confirmada posteriormente: “então manda ele fazer”; V91 aplicada e pacote ativo às14:30:04SãoPaulo. [Registro da execução e provas](F07-04-REVISION-LOCAL-ACTIVATION-2026-10-29.md). O texto abaixo preserva o plano revisado antes da execução. Não é autorização de produção, envio externo ou operação financeira real.

## Resultado revisável

- [PDF com logo sintética](../output/pdf/billing-control-synthetic-logo.pdf), duas páginas, SHA256 `0993A46A08AF36BE37CAA0A4EA9B87DCFD02C19C1C84359DD440CDF94BEA7148`.
- [PDF com fallback de nome](../output/pdf/billing-control-synthetic-fallback.pdf), duas páginas, SHA256 `770BFF2ECCF719AFAAE0BA125095DBDE704B2EE94847D1B485F4C0465B8FD7C2`.
- [Guia do frontend](FRONTEND-CONTRACT-BILLING-PDF.md). Serviços com descrição/valor/NFs/saldo, resumo contratual/caixa, histórico por NF e cobranças abertas com ou sem nota. Amostras inteiramente fictícias, com valores reconciliados; nenhum conteúdo dos modelos privados foi distribuído.

## Pacote

JAR: `tmp/contract-billing-pdf-revision-candidate/canteiro.io-f07-04-revision.jar`.
SHA256: `386004E6DD464719D71789CC181804AF117837F166B0B1E863BECA1DE7FBC14F`.

Comparado ao pacote publicado `351C5B5A59C4EF10190901E03EDD62479FDDB543CB4276343862CBE60CE0594C`: 1559 → 1565 entradas; **17 alteradas, 6 adicionadas, nenhuma removida e nenhuma inesperada**. Alterações limitadas ao relatório, captura do snapshot e exposição da constante SQL de elegibilidade já existente para reutilização, sem mudar seu predicado. Bibliotecas, configuração, loader e migrations V1–V90 idênticos. Manifesto: `tmp/contract-billing-pdf-revision-candidate/package-comparison.json`.

OpenAPI executável 3.1.0: `postman/canteiro-io.openapi.json`, 148 paths/257 schemas, SHA256 `E284551152BB5653E2EF542ECE3B38AD49A1BE10BD21CC013E1D8D84DE789944`. Somente a descrição da operação PDF mudou em relação ao export anterior; DTOs, respostas, permissões base e paths preservados. Contrato vivo ainda é o anterior porque a API não foi substituída.

## Validação

**47 testes distintos atuais / 5 suítes aprovados em rodadas identificadas**, sem somar reexecuções: ManualInvoiceSettlementApiIntegrationTest33, PdfBoxContractBillingPdfGeneratorTest7, InvoiceRegistrationServiceTest5, FlywayMigrationIntegrationTest1, OpenApiContractIntegrationTest1. Não é uma rodada única de47 nem toda a suíte do repositório.

Rodada1:46 testes,44 passaram e2 falharam; rodada2:2 testes,1 passou e1 falhou; rodada3:8 passaram; rodada4:1 passou. As falhas foram corrigidas no teste de injeção de falha: proxy transacional impedia o stubbing; depois, a expectativa409 foi corrigida para500 de falha técnica de persistência. Rollback é verificado depois da inserção do snapshot, garantindo ausência de NF/itens/snapshot parcial e posterior aprovação bem-sucedida. Não foi alterado o mapeamento de erros da aplicação para satisfazer o teste.

Cobertura: registro direto/aprovação202 sem efeito, rollback integral, imutabilidade UPDATE/DELETE, ausência de backfill, NF retroativa identificada pelo registro, snapshot inalterado após caixa/cancelamento, caixa líquido de estornos, retenção separada, cobrança semNF, duas permissões financeiras adicionais, quatro permissões base/401/403/404/tenant, distribuição por serviço com arredondamento, compromisso bruto25/desconto5/líquido20/NFparcial10, origem documental inconsistente409, concorrência de leitura, múltiplas referências de cobrança, lotes >100serviços/21NFs, descrição longa/continuação, precisão monetária máxima, logo/fallback e falha de renderização sem auditoria de sucesso. Checkstyle main/test aprovados. Quatro páginas sintéticas finais renderizadas e conferidas, sem cortes/sobreposições.

Provas em `tmp/validation-contract-billing-pdf-revision/round1-results` a `round4-results`, `validation-summary.json`; cópia do resumo em `tmp/contract-billing-pdf-revision-candidate/validation-summary.json`. QA em `tmp/pdfs/billing-control-revision-qa`, manifesto `pdf-qa-manifest.json` no candidato.

## V91 — alcance e preservação

Arquivo: `src/main/resources/db/migration/V91__capture_invoice_registration_financial_snapshot.sql`.
SHA256 SQL: `F1F6F91ED08DAE42A0D6A3D4DD10D2655867069DAF88DA69D09EDE2AE540BE88`.

Cria somente `invoice_financial_snapshot`, função e trigger de imutabilidade. Uma linha porNF; FKs compostas garantem a cadeiaNF/tenant/contrato e moeda imutável do contrato; CHECK garante diferença líquido−caixa. Não atualiza ou exclui dados existentes e **não preenche snapshots antigos**. Sem índices/alterações nas tabelas operacionais existentes. Flyway validou/aplicou V1–V91 em PostgreSQL temporário Testcontainers, sem acesso ao banco persistente.

O novo código depende da tabela: **não publicar este JAR em schema90**. O requisito de guardar histórico em novasNFs também impede considerar o JAR antigo suficiente para novas escritas após a atualização.

## Referência ativa verificada por leitura

Em17:13:50UTC/14:13:50SãoPaulo: API emexecução, JAR351C5B5A..., imagem `canteiroio-api:local-contract-billing-pdf-20261029`, ID `sha256:a641a9417e440a2be80ea127cc246318d3947c3077787ab9a5564fa77542d0de`; schema90, última migration bem-sucedida/checksum1503018190. Prova `baseline-runtime.json` no candidato. Nenhum restart, migration real, seed, login, exportação real, baixa, estorno ou outra operação financeira foi executado nesta correção.

## Sequência após autorização

1. Conferir novamente hashes e referência ativa; interromper se houver divergência e revisar o pacote.
2. Preservar pacote/container anterior, configurações privadas e volume; criar backup verificado do banco antes da migration. Não imprimir segredos.
3. Pausar somente a API local durante a janela, sem parar PostgreSQL/storage. Isso evita novasNFs no intervalo entre schema e código que captura o histórico.
4. Aplicar somente V91 com Flyway controlado, sem seeds e sem executar operações de negócio. Validar históricoFlyway91, FKs/trigger e ausência de alteração em registros anteriores; sem backfill.
5. Substituir somente a API8081 pelo JAR validado, preservando ambiente, rede, volume e política de reinício; manter migration automática/seeds desabilitados no processo de serviço.
6. Conferir readiness, hash ativo, OpenAPI200 e descrição/contrato correspondente ao candidato, autenticação401/ProblemDetails na rotaPDF. Não criarNF/recebimento real como teste. Conferência autenticada do documento fica com o usuário/frontend.
7. Registrar evidências e comunicar o frontend. Atualizar backlog para concluído somente depois da disponibilização verificada.

## Recuperação

Não apagar a tabela/snapshots, editar migrations aplicadas, restaurar dados por cima ou regravar histórico. Se V91 falhar, manter aAPI parada e diagnosticar; o banco usa migration transacional. Se o novo serviço falhar após V91, preservar banco e snapshot, manter escritas indisponíveis até correção. O pacote anterior conhece o ledgerV90, mas **não captura o snapshotV91**: não liberá-lo para novasNFs como se mantivesse a exigência. Eventual uso somente para consulta exigiria bloqueio efetivo de escrita e decisão explícita, não um modo somente-leitura presumido. Não retornar a versões anteriores ao ledgerV90 para operações financeiras.

## Autorização necessária

[AGENTS.md, seção14.1](../AGENTS.md): “Criar/testar uma migration em banco temporário não autoriza aplicá-la no banco real.” A autorização local de restart permanece válida, porém não abrange V91 no banco persistente. Pedido concreto posteriormente autorizado e executado: **aplicar V91 no banco local e disponibilizar o pacote acima em localhost:8081**, com backup/preservação/verificações descritos, sem seeds ou lançamentos financeiros reais.
