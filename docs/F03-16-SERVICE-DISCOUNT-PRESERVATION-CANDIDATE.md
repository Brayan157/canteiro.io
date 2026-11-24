# F03-16 — preservação do desconto, candidato e retorno

30/10/2026. Candidato testado, conferido pelo frontend e publicado somente em8081 às00:47:58SãoPaulo. [Ativação verificada](F03-16-SERVICE-DISCOUNT-PRESERVATION-LOCAL-ACTIVATION-2026-10-30.md). Plano anterior preservado abaixo. [Contrato](FRONTEND-SERVICE-UPDATE-DISCOUNT-PRESERVATION.md).

Reprodução anterior preservada em `tmp/validation-service-discount-preservation/reproduced-before-fix.xml`: os2testes de DIRECT/aprovação falharam porque discountType esperado PERCENTAGE/FIXED voltou null. Após a correção,16testes distintos/4suítes aprovados em1m33s:7API de obra/contrato (4novos),4domínioContractService,4calculador e1contratoOpenAPI. Checkstyle main/test e bootJar aprovados. Não contar as2reproduções como testes distintos adicionais nem afirmar execução de toda a suíte.

Cobertura: concluir preserva desconto do serviço, preço, quantidade e desconto de cabeçalho, tanto com omissão como null;202 não altera oficial; aprovação usa desconto mais recente e não snapshotantigo; eco igual aceito; intenção divergente409controlado; par incompleto400;403sempermissão e404outrotenant preservam cadastro. Os testes antigos de cálculo, reversão e indicador de desconto também passaram. Nenhuma operação no banco real.

## Pacote

- JAR `tmp/service-discount-preservation-candidate/canteiro.io-service-discount-preservation.jar` SHA256 **5E4601A1C460030EA91C57D89DEC6B67A61F0C975D1CCFF24DE541436EF7F06C**.
- Comparado ao ativo E97C650CBC8F23B5DE8F62CE7438845D02D43574A38B18D9DC628AED2E8691BE: somente3classes alteradas, ContractServiceManagementService, ContractController eUpsertContractServiceRequest. Nenhuma adição/remoção; bibliotecas, configuração, migrations e demais classes idênticas.
- Export executável `postman/canteiro-io.openapi.json` SHA256 **4BFEA6EBE8C8BCF304C53B579A76C83E734960203C3CA1E0397779AB362B1CE2**,148paths/257schemas. Somente descrições do Upsert e doPUT foram alteradas; mesmoscampos,tipo,statusHTTP. O409 possui reasoncontrolado SERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION quando tentativa explícita altera desconto pelo perfil.
- Manifesto e resultados: `tmp/service-discount-preservation-candidate/package-scope.json`, `tmp/validation-service-discount-preservation/build/test-results/test`.

## Atualização e retorno local

Após conferência do frontend, atualização local autorizada por AGENTS14.1, sem nova pergunta de permissão. Baseline conferido E97C650C, imagem `sha256:2b2a6da320718ed1e17fd7fc236b5b89c39ad011892cc199a2326327ba723eec`, schema91/checksum-1539369050. Script confere novamente antes de substituir, copia somente JAR conferido e preserva ambiente privado, storage, rede,8081,restartpolicy;Flyway/seedsfalse. Container anterior fica parado como `canteiro-api-before-service-discount-preservation-20261030`, JAR compatível também salvo em `tmp/service-discount-preservation-candidate/rollback-e97c650c.jar`.

Verificar hash/startup,OpenAPI200/contrato compartilhado idêntico,401ProblemDetails+correlationId e schema91 inalterado. Em falha, restaurar container E97C compatível, sem restaurar/apagar banco. Sem migration, seed, reajuste real, reprocessamento de auditoria ou reconstrução de desconto perdido. A correção evita perdas futuras e não recupera automaticamente valores já apagados.
