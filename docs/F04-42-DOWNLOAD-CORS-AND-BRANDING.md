# F04-42 — Filename do download e diagnóstico da marca

Data: 15/10/2026. Ajuste de código implementado, validado e **publicado na API local após autorização humana**. Não há mudança de schema, migration, rota, DTO, token real ou arquivo do frontend.

## Content-Disposition no navegador

A API já produz Content-Disposition com filename derivado do título. A configuração CORS anterior expunha somente X-Correlation-Id, impedindo a leitura de Content-Disposition pelo JavaScript de outra origem. O fallback local do frontend não significava que o header estivesse ausente da resposta HTTP.

O ajuste em JwtSecurityConfiguration mantém a lista configurada de origens, credenciais, métodos e headers de entrada. Expõe explicitamente apenas `X-Correlation-Id` e `Content-Disposition`; não usa wildcard e não concede acesso a uma origem arbitrária. Autenticação, tenant, permissões e auditoria de exportação permanecem obrigatórios.

Os testes focados usam servidor Tomcat HTTP real em porta aleatória e PostgreSQL Testcontainers descartável. Cobrem as seis rotas de PDF/XLSX/prévia, duas origens explicitamente configuradas no teste, filename/extensão, cache no-store, preflight com Authorization, rejeição de origem/método/header não autorizados, permissão e outro tenant. Os tokens são emitidos somente para fixtures do banco descartável; nenhum token ou registro da instância de uso foi alterado.

Validação final: **10 testes, zero falhas/erros/ignorados**, Checkstyle main/test aprovado, `BUILD SUCCESSFUL in 1m 54s`. São seis testes novos de HTTP real e quatro testes existentes de prévia/branding. A primeira execução identificou somente erro na preparação da nova fixture (ID preenchido interpretado como edição); a fixture foi corrigida para criação com ID gerado e transação própria, e a execução final passou sem mudança adicional no código de produção. Evidências XML e Checkstyle preservadas em `tmp/validation-download-cors`.

```powershell
./gradlew.bat test --tests com.renovar.canteiro.io.identity.DownloadCorsHttpIntegrationTest --tests com.renovar.canteiro.io.measurements.MeasurementSpreadsheetPreviewApiIntegrationTest checkstyleMain checkstyleTest --no-daemon --continue
```

## Publicação CORS autorizada

O humano confirmou “Pode publicar a correção local” na conversa do frontend. Antes da troca, foram comparadas as 1.371 entradas de cada JAR: **somente JwtSecurityConfiguration.class mudou**, sem outra mudança de aplicação, recurso, migration ou biblioteca. A configuração Compose planejada também foi comparada ao ambiente do container sem exibir valores de segredos.

Imagem publicada: `sha256:d9394eaca6800e754548dde46adc347659d290be4a6160e80923c292ea1b5690`. Container CORS: `1944fd9d6659e526b4f1a6517d23ed0bbe8cb5caae58c5f2cb61559138396462`, iniciado em `2026-10-15T12:27:10Z`, aplicação pronta às `12:27:26Z` (09:27 local). Somente API recriada; PostgreSQL/Mailpit mantiveram IDs e início anteriores. Banco permaneceu na V84, com 84 migrations e checksum `-228426622`; hashes das quatro tabelas comerciais/medições verificadas ficaram idênticos antes/depois. Nenhum volume, storage ou logo foi alterado na publicação CORS.

Validação real em localhost:8081: GET /api/v1/catalog com Origin `http://localhost:5173` retornou 200, Access-Control-Allow-Origin exato e **Access-Control-Expose-Headers: X-Correlation-Id, Content-Disposition**. Preflight da rota PDF acumulada, com Authorization e X-Correlation-Id, retornou 200. Origem não autorizada recebeu 403 sem permissão CORS; GET /company/currencies sem token continuou 401. Os testes anteriores de HTTP real cobrem o filename dos seis downloads autenticados. Em 15/10/2026, o frontend confirmou no navegador PDF/XLSX reais em Downloads com nomes vindos de Content-Disposition, sem fallback fabricado; [evidências da F04-41](F04-41-LOCAL-PUBLICATION.md).

GET /v3/api-docs retornou 200 e o hash vivo permaneceu **7166C8DED235CA31E6C3B517C5DDF1F7C68C0A034F72692F1781D21B9B75D878**, sem necessidade de mudar tipos. Pacotes/comparador/export da verificação estão em `tmp/cors-publication`. O build candidato concluiu em 2m10s. Não houve fabricação de token, gravação comercial ou alteração de arquivos do frontend.

## Logo da emitente — somente leitura

Para a medição indicada pelo usuário, a cadeia medição→Company foi consultada sem alteração de dados. A emitente é Empresa Gustavo (Gustavo Empresa LDTA) e **não possui referência de logo cadastrada**. Não foi encontrada uma referência privada apontando para arquivo ausente: a referência está vazia. Portanto o fallback para o nome está correto; não há evidência para afirmar que esta empresa perdeu uma logo no restart.

Na consulta anterior à F08-23, o diretório padrão `/app/var/storage` não existia, `/app` não era gravável pelo usuário da aplicação e não havia configuração alternativa nem mount persistente. Esse diagnóstico levou a uma autorização humana separada; o pré-requisito foi resolvido depois, conforme [storage privado local](F08-23-LOCAL-PRIVATE-STORAGE.md).

Sequência de storage/logo (separada da publicação CORS):

1. **Concluído com autorização separada:** storage privado gravável/persistente, usuário não root, diretório 0700, prova de leitura/escrita/persistência após recriação com artefato técnico isolado removido. Nenhum anexo ou dado comercial foi alterado; detalhes na F08-23.
2. **Ação do usuário, não executada pelo agente:** cadastrar **sua própria logo** pela rota já existente `POST /api/v1/company/logo`, multipart `file`, PNG/JPEG/WEBP até 2 MiB, exigindo `COMPANY.UPDATE_DIRECT`.
3. Conferir o download autenticado e gerar novamente PDF/XLSX; a marca é carregada pelo backend a partir da Company da sessão, não enviada na requisição do relatório.

Na empresa real, não foi criada, escolhida, substituída, enviada ou apagada imagem. Não foram expostas chaves internas, URL pública, documento da empresa ou dados pessoais. A consulta real permaneceu de leitura; fixtures de banco/branding e tokens dos testes são restritos ao ambiente descartável/de teste.
