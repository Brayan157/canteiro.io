# F05-36 — Candidato empacotado e plano de retorno

Data: 28/10/2026. Estado: **publicado e verificado somente na API local de localhost:8081**, após autorização humana literal “Sim, atualizar somente a API local sem migrations”. Imagem/container atualizados exclusivamente com o JAR preservado abaixo; nenhum rebuild de código, migration ou seed. Plano de retorno disponível e não acionado.

## Artefato preservado

- JAR: [canteiro.io-f05-36.jar](../tmp/invoice-attachment-cancellation-candidate/canteiro.io-f05-36.jar).
- SHA-256: `FCEEA62E421945764C8B8DB92D82E1C1F83CC59284DD903195EC5908DD488597`.
- Origem: `tmp/validation-invoice-attachment-cancellation/build/libs/canteiro.io-0.0.1-SNAPSHOT.jar`; cópia preservada conferida pelo mesmo hash.
- `bootJar` executado com Java 21: `BUILD SUCCESSFUL` em 15s; compileJava/processResources UP-TO-DATE, reutilizando as classes dos 40 testes aprovados. Não foi executado novo lote de testes para empacotar.
- Branch observada: `fase-5-faturamento`, HEAD `fb0dc4ece6b7a41598feb6c8ff0cc997337ec52a`. Há mudanças locais anteriores no workspace; o HEAD sozinho não identifica este candidato. A identidade de entrega é o hash do JAR e sua comparação com o pacote ativo.
- OpenAPI candidato: `postman/canteiro-io.openapi.json`, SHA-256 `976E9070772D909E7F40D30C2AC4EF6C2B50DE376A0A9BAAA3A34FC4E85133E4`, 145 paths/247 schemas, gerado do contexto executável de `/v3/api-docs` no teste contratual.
- [Contrato funcional, diagnóstico, resultados dos 40 testes/5 suítes e Checkstyle](FRONTEND-INVOICE-UPLOAD-AND-CANCELLATION-SAFETY.md).

## Referência anterior conferida antes da publicação e preservada

Antes da troca, o container `canteiro-api` estava running, imagem `canteiroio-api:local-invoice-contract-index-20261027`. O hash de `/app/app.jar` foi conferido novamente imediatamente antes da substituição e é idêntico ao JAR local usado na comparação:

`89B0202433707CE862A34DDCE4A48C1CB5DE30045013D0991DDBAA4B8369AD36`.

Configuração operacional conferida, sem expor valores de credenciais:

- Porta: host 8081 → container 8080.
- Rede: `canteiroio_default`.
- Volume privado: `canteiroio-api-private-storage` → `/app/var/storage`.
- `SPRING_FLYWAY_ENABLED=false`.
- `BOOTSTRAP_PLATFORM_OWNER_ENABLED=false`.
- `BOOTSTRAP_TRUST_UNLOCK_DEMO_ENABLED=false`.

O container anterior iniciou em `2026-10-27T23:46:27.73143972Z`; foi parado e preservado como `canteiro-api-before-invoice-cancellation-safety-20261028`. A preparação do pacote não consultou o banco real; na publicação autorizada, a leitura da versão Flyway confirmou V89 antes e depois do startup, sem consulta a registros de negócio.

## Comparação do conteúdo do pacote

[Manifesto com SHA-256 por entrada alterada](../tmp/invoice-attachment-cancellation-candidate/package-comparison.json) e [script reproduzível de comparação](../tmp/invoice-attachment-cancellation-candidate/compare-package.ps1).

Comparação por conteúdo descomprimido de **todas** as entradas, incluindo classes, recursos, bibliotecas, manifestos e loader: 1.518 entradas no pacote ativo e 1.519 no candidato. Nenhuma removida; 13 alteradas e uma adicionada. Metadados externos do ZIP não são considerados diferenças de conteúdo.

| Grupo | Diferenças justificadas |
| --- | --- |
| ContractRepository, ContractJpaRepository, JpaContractRepository | 3 classes: bloqueio de validação sem incremento forçado de versão. |
| PayableRepository, PayableJpaRepository, JpaPayableRepository | 3 classes: bloquear a referência tributária durante a decisão de cancelamento. |
| ReceivableRepository, ReceivableJpaRepository, JpaReceivableRepository | 3 classes: validação transacional sem mutação de versão em propostas 202. |
| FinancialCancellationService | Orquestrar validação antes do pedido e repetir na aprovação/aplicação. |
| InvoiceCancellationSafetyGuard | Única classe adicionada: proteger recebimentos, tributos pagos e históricos não comprováveis. |
| JpaInvoiceAttachmentRepository | Flush antes da projeção imutável com timestamp obrigatório. |
| InvoiceController | Descrição OpenAPI dos motivos de bloqueio e limites do cancelamento. |
| PayableRepository.CreationGroup | Apenas LineNumberTable: linha 21 → 23 após inserir método na interface; nenhum comportamento alterado. |

As primeiras sete linhas representam 13 classes do escopo funcional; a última é uma classe interna regenerada. A diferença interna foi inspecionada com `javap -v -p` e `javap -p -c -s -constants`: bytecode, assinaturas, campos e descritores idênticos. As saídas sem depuração têm SHA-256 idêntico `2DFEDEF1642ADFA49BE2D366EFBF8E83E8996E209A7384F9F86946174E8BF9E5`; saídas preservadas junto ao manifesto. A comparação fixa também os hashes dessa exceção de depuração para exigir nova revisão se ela mudar.

**Nenhuma dependência, configuração, migration, recurso, loader ou classe alheia mudou no pacote.** O `ScopeApproved` do manifesto significa somente conformidade com essa lista de diferenças; não é autorização de deploy.

## Plano de publicação autorizado e executado

1. Obter autorização humana explícita para atualizar somente a API local em 8081. Manter separados os gates do frontend e qualquer operação financeira autenticada.
2. Conferir novamente hash do candidato, identidade/hash da API ativa, schema esperado V89 e ausência de colisão dos nomes reservados. Se houver mudança na referência ativa, interromper e refazer a comparação.
3. Criar a imagem exclusivamente com o JAR preservado e o runtime já verificado, sem recompilar a árvore suja nem usar um candidato anterior. Nome proposto: `canteiroio-api:local-invoice-cancellation-safety-20261028`. Conferir o JAR na imagem antes de ativar.
4. Preservar a configuração operacional atual, rede, porta, política de reinício e volume privado. Forçar Flyway e os dois seeds em false; não executar migrations/clean/repair, seeds, Compose que recrie dependências ou qualquer operação de negócio.
5. Preservar o **container que estava ativo**, com JAR `89B02024...`, sob nome `canteiro-api-before-invoice-cancellation-safety-20261028`. Esse backup agora existe e está parado. Os backups mais antigos não substituem automaticamente essa referência.
6. Substituir somente a API após checagem do candidato. Não apagar o container preservado, imagens anteriores, volume privado, banco ou dados.
7. Conferir readiness, hash exato do JAR ativo, flags false, rede/volume/porta e `GET /v3/api-docs` com 200, contrato dos oito metadados e descrições dos bloqueios. O ambiente local anterior omite o webhook de pagamento desabilitado, portanto não exigir igualdade bruta do número de paths com o teste que o habilita.
8. Não fazer upload real, login, cancelamento, baixa, seed de empresa ou outra operação financeira para essa verificação sem autorização própria. Testes funcionais autenticados desta entrega foram executados em PostgreSQL sintético descartável. A ativação e suas provas devem ser comunicadas antes de reconsiderar a UI de cancelamento.

## Resultado e evidências da publicação

- Autorização conferida diretamente na mensagem humana do chat de frontend antes de executar a troca. Não inferida da prontidão do pacote.
- Imagem: `canteiroio-api:local-invoice-cancellation-safety-20261028`; ID `sha256:005ba59d133cbdec33449875b21539f80c3311bed306f43754c469359f26699a`. Construída somente sobre a imagem anterior conferida e o JAR preservado; sem Gradle/recompilação dentro do build Docker.
- Container `canteiro-api` running; iniciado `2026-10-28T10:06:14.093515148Z`; evento `Started Application` às `2026-10-28T10:06:47.412Z` (07:06:47 em São Paulo).
- JAR dentro da imagem e dentro do container ativo: `FCEEA62E421945764C8B8DB92D82E1C1F83CC59284DD903195EC5908DD488597`, exatamente o artefato testado/aprovado.
- Schema V89 antes e após startup; Flyway e seeds owner/demo false. Ambiente idêntico ao container anterior, sem imprimir credenciais; mesma rede, porta 8081, volume privado e política de reinício.
- `/v3/api-docs`: 200 application/json, OpenAPI 3.1.0, 144 paths/247 schemas. Todos os caminhos compartilhados e todos os componentes são idênticos ao export aprovado `976E9070...`. Única ausência: `/api/v1/webhooks/payments`, já desabilitado no ambiente local e habilitado no teste contratual.
- Motivos da guarda F05-36 documentados na operação publicada; schema de anexo mantém os oito metadados obrigatórios, incluindo createdAt UTC.
- GET anônimo `/api/v1/company/invoices/contracts`: 401 application/problem+json, código UNAUTHENTICATED e correlationId. Sem login ou chamada autenticada financeira.
- Container de retorno `canteiro-api-before-invoice-cancellation-safety-20261028` preservado/parado, com imagem anterior conferida. Não foi necessário acionar retorno.
- Evidências técnicas: [runtime](../tmp/invoice-attachment-cancellation-candidate/publication-runtime.json), [contrato vivo e 401](../tmp/invoice-attachment-cancellation-candidate/live-contract-evidence.json), [linha de startup](../tmp/invoice-attachment-cancellation-candidate/startup.txt), [OpenAPI vivo](../tmp/invoice-attachment-cancellation-candidate/live-openapi.json). Esses arquivos não contêm variáveis de ambiente secretas ou registros financeiros.
- Sem migration, seed, upload real, cancelamento, baixa, liberação de F05-32, envio fiscal, Git push ou deploy remoto. Os 40 testes anteriores continuam sendo provas sintéticas; publicação não equivale a homologação autenticada com dados reais.

## Plano de retorno — não executado

Se o candidato não iniciar, apresentar hash/configuração divergente ou falhar nas verificações contratuais, interromper a nova API e restaurar o container preservado `canteiro-api-before-invoice-cancellation-safety-20261028` ao nome/porta de `canteiro-api`. Antes de iniciar, conferir que seu JAR é `89B02024...` e que Flyway/seeds permanecem desligados. Preservar o candidato falho para diagnóstico sem conteúdo sensível em logs.

Conferir novamente readiness e OpenAPI da versão anterior. Não remover volumes nem restaurar banco sobre dados atuais. Como o candidato não contém migration, o retorno é de aplicação; não desfaz automaticamente cancelamentos ou outras ações de negócio eventualmente aplicadas após uma publicação autorizada.

Limite conhecido: voltar ao pacote anterior também volta ao defeito de resposta do upload e à ausência da guarda F05-36. A UI de cancelamento deve continuar bloqueada, ou voltar a bloqueada se já tiver sido liberada por fluxo separado. O retorno não libera novos recebimentos: F05-32 permanece pendente e `SETTLEMENT_INVOICE_LINK_REQUIRED` continua obrigatório.
