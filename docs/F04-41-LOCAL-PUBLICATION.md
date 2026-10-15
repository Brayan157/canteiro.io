# F04-41 — Publicação local de moedas e relatórios

Publicação inicial: 14/10/2026, America/Sao_Paulo. **F04-41 concluída em 15/10/2026**, após validação final do frontend em sessão real autenticada.

Atualizações posteriores em 15/10/2026, com autorizações humanas separadas: [CORS de download](F04-42-DOWNLOAD-CORS-AND-BRANDING.md) e [storage privado local](F08-23-LOCAL-PRIVATE-STORAGE.md) publicados/verificados, sem mudar o hash do OpenAPI abaixo. Frontend confirmou contrato/tipos sincronizados, moedas e PDF/XLSX reais com filename oficial no navegador. Este registro conserva as evidências da primeira publicação e acrescenta o encerramento integrado; a imagem final está registrada na F08-23.

## Autorização e escopo

Autorização humana confirmada na conversa “Fase 7 — Medições e documentos”: resposta “Pode fazer” ao pedido explícito de publicar a API local e aplicar V84. Não houve deploy externo, reset, exclusão de dados, escolha de destinos/composições ou lançamento comercial manual.

O código das regras/relatórios não foi alterado nesta publicação. O build usa a branch `fase-5-faturamento`, com as alterações locais validadas anteriormente; não foi criado commit, merge ou push.

## Execução

- Preflight: banco na V83, nenhuma coluna currency em contract/measurement e somente V84 pendente no código. Backup PostgreSQL custom criado e listado com sucesso em `/tmp/canteiro-before-v84-20261015.dump`, dentro de `canteiro-postgres`, com permissões restritas. É uma cópia preventiva interna, não um backup externo nem ensaio de restore.
- Build: `docker compose build api`, concluído com `BUILD SUCCESSFUL in 2m 29s` e empacotamento final da imagem.
- Troca: `docker compose up -d --no-deps --no-build api`. Somente `canteiro-api` foi recriado; PostgreSQL, Mailpit e volumes permaneceram intactos. O diretório padrão de storage não existia no container anterior: não havia arquivos ali para copiar. Esta entrega não adiciona storage persistente nem recupera anexos ausentes.
- Imagem publicada: `sha256:2b2f28a8d969faf0fa3cfdf6e0044ec3e2a91a937e1c1ea74a6d0c2c3d876758`.
- Container: `59fc409702c32f918611b49737f345b48e76ababd424ee61293bb33e07c43796`, em execução, zero reinícios na verificação final, porta 8081→8080.
- Flyway validou 84 migrations e aplicou **somente V84**, com sucesso, em `2026-10-15T00:49:33Z` (14/10 às 21:49 local). Checksum Flyway: `-228426622`.
- Startup finalizado em `2026-10-15T00:49:51Z`; versão Java/Spring da aplicação `0.0.1-SNAPSHOT`. O identificador preciso desta publicação é a imagem acima.

## Preservação e contrato vivo

As quatro linhas de contract e cinco de measurement receberam BRL. Os hashes ordenados das linhas completas, removendo apenas currency nas duas raízes, permaneceram iguais antes/depois. As 13 versões e 34 itens também mantiveram exatamente seus hashes completos. Não houve conversão, reavaliação monetária nem alteração dos itens históricos.

Verificação HTTP final:

| Recurso | Resultado |
|---|---|
| GET /v3/api-docs | 200 application/json |
| GET /api/v1/catalog | 200 application/json |
| GET /api/v1/company/currencies sem token | 401; repetido três vezes com correlação controlada |

O JSON vivo validou o catálogo BRL/USD/EUR, currency nos 18 DTOs de topo/criação/edição verificados, dinheiro string/decimal nas quatro requisições financeiras, bearerAuth, Problem Details e as seis rotas de relatórios PDF/XLSX/prévia.

Export atual: `postman/canteiro-io.openapi.json`, obtido diretamente de `http://localhost:8081/v3/api-docs`, sem editar schemas manualmente.

- OpenAPI: **3.1.0**; **123 paths**, **193 schemas**.
- SHA-256: **7166C8DED235CA31E6C3B517C5DDF1F7C68C0A034F72692F1781D21B9B75D878**.
- O antigo hash `4C19DC...` identificava o export do teste, não este contrato publicado. O teste habilita Asaas e inclui `/api/v1/webhooks/payments`; a instância local mantém o adapter desabilitado, por isso possui um path a menos. Não habilitar gateway apenas para igualar contagens.

## Validação integrada do frontend — encerramento em 15/10/2026

Evidências comunicadas pela conversa **Fase 7 — Medições e documentos**, responsável pela validação no Chrome e pelos testes do frontend. O backend registra esse resultado atribuído ao frontend; não realizou nova leitura/renderização dos arquivos comerciais ou nova execução dos testes nessa atualização documental.

| Download autenticado | Resultado informado pelo frontend |
|---|---|
| PDF acumulado | servicos-fabrica-de-acucar.pdf, 3.411 bytes, presente em Downloads |
| XLSX acumulado | servicos-fabrica-de-acucar.xlsx, 5.949 bytes, presente em Downloads |

Os dois nomes foram obtidos de Content-Disposition após o CORS, sem fallback fabricado. O frontend inspecionou o XLSX por leitura/render/XML: dois serviços, cinco atividades, dez colunas visíveis, títulos centralizados e cifras. Não editou nem reexportou o relatório e não alterou dados comerciais.

Depois da recriação final de storage, uma nova consulta autenticada do acumulado preservou as cinco atividades aceitas e os serviços Linha de Vinhaça/Linha de cobre. O catálogo real BRL/USD/EUR e o seletor USD foram conferidos no Chrome sem salvar entidade. O artefato OpenAPI do frontend foi sincronizado pelo hash vivo acima e os tipos foram regenerados.

O frontend também informou testes focados, lint/build e jornadas E2E aprovadas: moeda com 409→reenvio→202, PDF/responsividade e numeração. A jornada de numeração passou isoladamente após timeouts diagnosticados; isso não é uma declaração de aprovação de uma suíte ampla que foi interrompida. Não somar essas execuções aos 192 testes anteriores do backend como se fossem uma única suíte.

Com isso, publicação, sincronização do contrato, tipos, moedas e downloads reais estão validados e a **F04-41 está concluída**. Nesta atualização foram alterados apenas documentação/status, sem implementação, restart, token fabricado ou mutação comercial.

## Pendências separadas e limites

A emitente permanece sem logo real cadastrada: o fallback para Empresa Gustavo é correto. Storage já foi preparado, mas o envio da imagem pertence ao usuário; nenhuma autorização recebida permitiu escolher/enviar uma logo. O frontend registra F06-26 concluída e F07-47 parcial exclusivamente pela marca real/interface de cadastro. Essa pendência não reabre a publicação/integração validada da F04-41 nem autoriza upload por conta própria.

Na checagem anônima, a resposta externa foi 401, mas os logs registraram `IllegalStateException: A company tenant context is required` em SubscriptionAccessFilter, antes da autorização final. A resposta não concedeu acesso. A correção da ordem/ausência de contexto e de Problem Details, com regressão em servidor HTTP real, fica registrada em F09-08; nenhuma regra de autenticação, tenant ou dunning foi relaxada nesta entrega.

O Compose também emitiu aviso de interpolação de uma configuração de segredo do `.env`. Seu conteúdo não deve ser reproduzido em documentos ou mensagens. Sanear a configuração e avaliar a rotação da chave de sandbox exposta pelo aviso em F09-01. Nenhum segredo foi alterado nesta publicação.

O PostgreSQL manteve aviso preexistente de versão de collation; a migration foi aplicada com sucesso. Não foi executado reparo de collation, reindexação nem alteração estrutural fora da V84.
