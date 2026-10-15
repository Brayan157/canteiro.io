# F08-23 — Storage privado persistente no ambiente local

Data: 15/10/2026. **Implementado, publicado e verificado**, depois da publicação CORS F04-42. Não há migration nova, alteração de DTO/rota, arquivo do frontend, segredo, gateway, Company ou lançamento comercial.

## Autorização e preservação

Autorização humana separada confirmada na conversa do frontend: “Pode configurar o armazenamento local”, para preparar storage privado gravável/persistente, preservando banco/anexos e sem escolher/enviar logo. A autorização CORS foi concluída primeiro; a preparação do storage não foi incluída silenciosamente nela.

Inventário anterior à mudança:

- O diretório padrão da API não existia e não havia arquivos para copiar. Não foi montado o diretório de fixtures/testes do workspace.
- O volume novo `canteiroio-api-private-storage` não existia. O volume PostgreSQL existente `canteiroio_postgres-data` foi preservado.
- As cinco Company tinham referência de logo ausente. Havia zero MeasurementDocument e InvoiceAttachment; os cinco registros de referência de foto de funcionário foram preservados.
- Backup PostgreSQL custom criado/listado com sucesso em `/tmp/canteiro-before-storage-20261015.dump`, dentro de canteiro-postgres, com umask 077. É cópia preventiva interna, não backup externo nem prova de restore. Os arquivos de configuração anteriores foram copiados para `tmp/storage-publication`, sem copiar .env ou valores de segredos.
- A configuração completa de PostgreSQL/Mailpit foi comparada por hash antes/depois da edição. Nenhum campo desses serviços mudou; no ambiente da API, a única adição foi LOCAL_STORAGE_BASE_PATH.

## Configuração aplicada

| Componente | Configuração |
|---|---|
| Volume novo | canteiroio-api-private-storage |
| Serviço que o monta | api, em /app/var/storage, leitura/escrita |
| Variável da API | LOCAL_STORAGE_BASE_PATH=/app/var/storage |
| Usuário de runtime | canteiro, UID 100; não root |
| Diretório privado | dono 100:101, modo 0700 |
| HTTP | Sem rota estática ou URL pública para o volume; downloads existentes mantêm autenticação/tenant/permissão |

Dockerfile prepara somente o diretório privado com install -d -o canteiro -g canteiro -m 0700. O USER canteiro e o ENTRYPOINT Java permanecem. O Compose adiciona somente variável/mount/volume de storage da API, preservando alterações locais anteriores, inclusive a chave de dados de funcionário já configurada.

A imagem de storage reutiliza o JAR do build CORS: **SHA-256 DA21573B4F94518926DE6CDE5C349C6C2C48704198A8B70BB5F5C94AFD2E69BD, byte a byte idêntico**. Não entraram outras alterações de Java, recurso, migration ou biblioteca. As mudanças foram verificadas antes de recriar somente a API.

Imagem final: `sha256:7789a68c896cdcb8594002eecde4eb917ca1ebfb33eef4dc980b4809055ea769`.

## Prova real de escrita/leitura e persistência

1. API recriada com o volume novo: container `96b11c47a679f4ec60e874988a46d843e21d534ef6735706844efce374b206ab`, pronto às `2026-10-15T12:32:35Z` (09:32 local). Verificado diretório gravável pelo usuário não root, dono/modo 100:101/0700.
2. Criado exclusivamente o arquivo técnico `.f08-23-probe-7beb7372eb5749d58d86635275ff2702.txt` na raiz do storage, pelo mesmo usuário da API, com umask 077, dono/modo 100:101/0600. Conteúdo técnico, sem dados pessoais, imagem ou associação com entidade real. UID 65534 não conseguiu atravessar o diretório.
3. Conteúdo lido e hash calculado: `b3131ece6a027be0f64d462cb282d16e6535710b71fecc4d8b28209f65d7de2f`.
4. Executada recriação explícita somente de api, sem build/dependências e sem remover volumes. Novo container `7528f3a8dea3bbc75756974a66986dbdf2fdae06982fb1d12ce47bf9299e5b85`, pronto às `2026-10-15T12:33:50Z` (09:33 local).
5. O mesmo arquivo foi lido no novo container: **hash, dono e modo idênticos**. Só então foi removido, por caminho absoluto exato validado e conferência de dono, exclusivamente esse arquivo técnico. Sua ausência foi confirmada; nenhum anexo ou outro arquivo foi removido.

PostgreSQL e Mailpit mantiveram seus IDs e início desde 14/10/2026. O banco continuou com 84 migrations, V84/checksum -228426622. Hashes antes/depois de Company, Contract, Measurement, MeasurementVersion, MeasurementItem, documentos de medição, anexos de NF e referências de foto permaneceram iguais. Nenhuma Company foi alterada e nenhuma imagem cadastrada.

Verificação HTTP final na instância localhost:8081:

- catálogo público e /v3/api-docs: 200;
- origem autorizada: Access-Control-Expose-Headers contém X-Correlation-Id e Content-Disposition;
- preflight de download autenticado: 200;
- origem não autorizada: 403; rota de empresa sem token: 401;
- API não root, zero reinícios automáticos e mount do volume correto.

OpenAPI vivo inalterado: **7166C8DED235CA31E6C3B517C5DDF1F7C68C0A034F72692F1781D21B9B75D878**, 123 paths/193 schemas/OpenAPI 3.1. Os dez testes CORS/preview e Checkstyle da F04-42 continuam aplicáveis ao JAR idêntico; não foi anunciada nova execução dessa suíte nesta alteração de infraestrutura. A prova nova foi de filesystem/volume e HTTP real.

## Cadastro da logo pelo usuário

Agora o usuário com COMPANY.UPDATE_DIRECT pode enviar a sua própria logo pelo fluxo existente:

```http
POST /api/v1/company/logo
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

Parte `file`, PNG/JPEG/WEBP, até 2 MiB. O backend resolve a Company pela sessão e retorna logoUrl autenticada. Depois do cadastro, reabrir/baixar PDF/XLSX: o relatório carrega a marca internamente; sem logo, o fallback do nome continua correto. Não foi escolhida nem enviada uma imagem em nome do usuário.

## Limites operacionais

É persistência **local privada**, não object storage de produção, backup externo, criptografia adicional ou política completa de retenção/privacidade. Esses controles continuam nas fases correspondentes. Não remover o volume nem usar operações de teardown com exclusão de volumes para um restart comum. Uma mudança futura de UID/imagem-base exige conferir a propriedade existente, sem correção destrutiva de dados. Produção continua exigindo o adapter/política de storage apropriados.
