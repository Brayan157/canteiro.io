# F05-35 — Índice de contratos com notas fiscais

Estado em 27/10/2026: concluído e publicado somente na API local 8081 às 20:46:56 (America/Sao_Paulo), com 30 testes/5 suítes, Checkstyle e bootJar aprovados. Autorização nova e específica, sem migrations, seeds, liberação de recebimentos ou operações financeiras reais. A autorização anterior F05-33/34 não foi reutilizada.

## Fluxo de navegação

1. Abrir `GET /api/v1/company/invoices/contracts` para listar contratos representados por NFs oficiais.
2. Selecionar um contrato pelo `contractId` interno e conservar esse contexto na URL.
3. Recarregar o cabeçalho por `GET /api/v1/company/invoices/contracts/{contractId}`, independentemente de filtros de NF ou de `history.state`.
4. Listar as NFs por `GET /api/v1/company/invoices?contractId={contractId}` já existente. Filtros de número/série/status/emissão continuam restritos ao contrato escolhido.

Nunca usar `contractSearch`, título, identificador comercial ou a página atualmente carregada como identidade de grupo. Nomes e identificadores podem repetir. Não criar campo digitável de UUID; o ID vem da seleção/navegação interna. Não buscar o contrato completo nem exigir CONTRACTS.READ/BILLING.READ para reparar o cabeçalho.

## Índice paginado

`GET /api/v1/company/invoices/contracts?search=usina&page=0&size=20&sort=contractName,asc`

Requer somente `INVOICES.READ`; tenant exclusivamente da sessão. Uma linha por contrato que possui ao menos uma NF oficial persistida do mesmo tenant, inclusive somente cancelada. Não inclui contratos sem NF nem propostas de criação `202`. Não filtra por status ativo do contrato para apagar histórico.

Busca/EXISTS/count/paginação são oficiais no banco, não agrupamento local de uma página de NFs. Count e conteúdo compartilham snapshot transacional.

| Parâmetro | Contrato |
| --- | --- |
| `search` | Opcional, trecho literal do nome, referência ou identificador comercial; trim, máximo 200, case-insensitive; branco/omitido sem filtro; `%`, `_` e `!` são literais |
| `page` | Padrão 0, não negativo; offset acima de Integer.MAX_VALUE rejeitado |
| `size` | Padrão 20, entre 1 e 100 |
| `sort` | Somente `contractName`, `contractReference`, `contractIdentifier`; `campo,asc`/`campo,desc`, repetível; padrão nome asc, nulos ao final e desempate contractId asc |

Resposta: `PageResponseInvoiceContractResponse` com `items`, `page`, `size`, `totalItems`, `totalPages`. Cada `InvoiceContractResponse` contém exatamente:

```json
{
  "contractId": "uuid-interno-do-contrato",
  "contractName": "Contrato Usina",
  "contractReference": "CTR-REF",
  "contractIdentifier": "0001/2027"
}
```

`contractId` é UUID real na API; o texto do exemplo é ilustrativo. Referência e identificador podem ser null e continuam presentes. São rótulos comerciais atuais, não snapshots fiscais. Não há moeda, valores, saldo, recebimentos, quantidade de NFs, chave fiscal, dados de cliente ou storage. `totalItems` conta grupos de contratos, não NFs ou dinheiro.

## Contexto exato

`GET /api/v1/company/invoices/contracts/{contractId}` retorna o mesmo `InvoiceContractResponse`, sem paginação. Requer somente `INVOICES.READ` e usa o mesmo critério de existência de NF oficial no tenant.

Retorna `404` seguro para contrato inexistente, estrangeiro ou sem NF oficial. Continua resolvendo quando o filtro atual de NFs retorna zero registros e quando há apenas notas canceladas. Não depende de nome salvo no browser, da página do índice ou do filtro de busca anterior.

## Segurança e compatibilidade

- `200` em consulta autorizada; `400` em UUID/filtro/página/sort inválidos, `401` sem autenticação e `403` sem INVOICES.READ. Contexto inclui `404` descrito acima. Erros usam Problem Details/correlationId.
- Consultas privadas/no-store, sem auditoria de mutação, reserva ou mudança financeira. `companyId` não é parâmetro de tenant.
- Nenhuma nova permissão e nenhum acesso ao módulo completo de contrato por consequência da consulta de NF.
- Nenhuma mudança de criação/cancelamento de cobrança, NF ou contrato, retenção, ledger ou aprovação. Novos recebimentos continuam bloqueados e F05-32 pendente. Situação financeira de NF permanece a indisponibilidade honesta do F05-34.
- Remoção da coluna visual de moeda em contas a receber é trabalho exclusivo do frontend; os códigos de moeda continuam no contrato da API.
- Gerar tipos exclusivamente pelo export executável final; disponibilidade no código/export não significa endpoint publicado na instância 8081.

## Validação e export

- Rodada final: 30 testes distintos em 5 suítes, sem falhas/erros/ignorados. Inclui 12 testes de consulta de NF, quatro unitários do novo caso de uso, teste OpenAPI executável e regressões de recebíveis/disponibilidade de NF. Uma duplicidade de número de NF na fixture da primeira rodada foi corrigida sem alterar a constraint de produção.
- Provas cobrem nomes/identificadores repetidos, busca literal/limites, tenant, INVOICES.READ sem CONTRACTS.READ/BILLING.READ, agrupamento antes de count/paginação, histórico cancelado, contexto exato com filtros de NF vazios, proposta 202 sem grupo oficial e imutabilidade dos dados consultados. Não é execução de todos os testes do repositório.
- Checkstyle main/test sem violações; bootJar aprovado. Relatórios em `tmp/validation-invoice-contract-index/build`.
- Export oficial: `postman/canteiro-io.openapi.json`, OpenAPI 3.1.0 com 145 paths/247 schemas. SHA-256 `A300B8C971A63140554E9063E83BAB9C81C365F041B97FA4A5806CA67FE14C08`. Gerado do endpoint executável no teste contratual; não editado manualmente. Substitui o snapshot anterior `0AB14484...` no arquivo corrente, preservando os contratos de F05-33/34.
- JAR candidato aprovado: SHA-256 `89B0202433707CE862A34DDCE4A48C1CB5DE30045013D0991DDBAA4B8369AD36`. Comparado com o JAR anterior D9F59692...: somente seis classes autorizadas mudaram; nenhuma dependência/configuração/migration foi adicionada ou alterada no pacote.
- Publicação local: imagem `canteiroio-api:local-invoice-contract-index-20261027`, container `canteiro-api`, startup `2026-10-27T23:46:56Z`. Hash do JAR dentro do container confere com o candidato aprovado.
- `/v3/api-docs` retornou `200 application/json`, OpenAPI 3.1 com 144 paths/247 schemas; ambas as novas operações e os dois schemas são idênticos ao export. Diferença de um path: `/api/v1/webhooks/payments`, habilitado no teste e já desabilitado localmente; nenhuma alteração desse gateway nesta entrega.
- GET anônimo do novo índice retornou `401 application/problem+json`. Sem login ou leitura autenticada de dados reais; jornadas autorizadas foram verificadas com dados sintéticos em PostgreSQL temporário. A prova pública não substitui homologação autenticada real.
- Flyway e seeds owner/demo desabilitados; schema real V89 e mesmo volume privado preservados. Container anterior parado em `canteiro-api-before-invoice-contract-index-20261027`, recuperável para retorno controlado. Sem deploy remoto, push ou mudança de domínio financeiro.
- Aviso preexistente de collation reapareceu na leitura de verificação de schema; nenhuma correção de banco/índices/infra foi feita fora do escopo.

## Fechamento frontend

Integração FRONT-F08-27/28/29 concluída segundo o [relatório frontend consultado](C:/Users/oqdel/OneDrive/Desktop/canteiro-front/docs/F08-27-29-NF-POR-CONTRATO-E-CANCELAMENTO.md), em 27/10/2026: export A300B8C9... consumido e tipos gerados oficialmente; índice de contratos, contexto restaurável e notas por contractId exato, sem permissão adicional, agrupamento local ou fallback global em 404. A descoberta do cancelamento de cobrança usa o comando/permissões existentes, sem POST ao abrir; a coluna de moeda foi retirada apenas da lista visual de recebíveis, mantendo os símbolos.

Provas reportadas no documento, não reexecutadas pelo backend neste registro: 114 testes distintos/8 arquivos, 31 E2E distintos de artefato com API interceptada, quatro larguras/capturas inspecionadas, lint/tipagem/build/scanner e 31 testes de política aprovados. Lotes repetidos não são somados como casos distintos. Também houve conferência pública independente do OpenAPI local (144 paths/247 schemas, novas operações idênticas) e 401 no índice anônimo.

Não é homologação autenticada com dados reais. Sem login, operação financeira real, migration, deploy frontend/remoto ou liberação de recebimentos; F05-32 e a comprovação paga/parcial/não paga por NF continuam pendentes. Nenhuma implementação, teste ou publicação adicional foi executada para este fechamento documental.
