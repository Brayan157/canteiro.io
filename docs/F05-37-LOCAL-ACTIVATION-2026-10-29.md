# F05-37 — Publicação somente local em29/10/2026

Autorização humana conferida diretamente na conversa do frontend: **“Sim, atualizar somente a API local sem migrations”**, para o pacote final da proposta de preenchimento. Executada somente a troca da API8081, sem migration, seed, login, upload ou operação financeira real. Não autoriza deploy remoto ou outras alterações.

## Provas

- Startup às **09:19:55 America/Sao_Paulo**, `2026-10-29T12:19:55.501Z`. Container `canteiro-api`; imagem `canteiroio-api:local-total-invoice-settlement-proposal-20261029`, ID `sha256:e2637a2958f9f6ffd47604e2655411c5e2bbfd26d540bffc6c8e614762f8beb4`.
- JAR ativo SHA-256 `B971C83CB82DBDA6BACBF1485DECCC5220F38D8B8D115FE96432FB359F09CF37`; imagem criada exclusivamente por cópia do artefato aprovado sobre a base ativa conferida, sem recompilar fontes.
- Schema permanece90, checksum V90 `1503018190`;90 registros Flyway, nenhum com falha e máximo90. Nenhum runner Flyway ou SQL de mutação executado.
- Flyway automático, seed owner e seed demo continuamfalse. Ambiente privado idêntico ao container anterior, rede `canteiroio_default`, porta8081→8080, volume privado `canteiroio-api-private-storage` em `/app/var/storage` e restart policy preservados.
- OpenAPI vivo **200 application/json**,3.1.0,146paths/257schemas. Todos os caminhos compartilhados/componentes estruturalmente idênticos ao export aprovado `7BF8E2214BE0046868B431A48F60B5021857DC0E61CAA88F2EDBD8852F07C3E8`,147paths/257schemas; única ausência prevista é o webhook local desabilitado `/api/v1/webhooks/payments`. Sem caminho extra ou diferença inesperada; note.maxLength1000. Inclui `totalSettlementProposal` e os2schemas novos.
- Probes anônimos do GET opções, POST recebimento com corpo vazio e GET índice de contratos de NF: **401 application/problem+json**, UNAUTHENTICATED e correlationId presente. Não comprovam execução financeira autenticada real; essa execução não foi feita.
- Versão anterior compatível0E25... preservada e parada em `canteiro-api-before-total-invoice-proposal-20261029`. O retorno segue o [plano revisado](F05-37-CANDIDATE-AND-ROLLBACK.md): usar F05-32 compatível com ledgerV90, nunca F05-36/versão anterior ao ledger para estornos, sem restaurar/apagar dados.

Provas locais: `tmp/invoice-total-proposal-candidate/publication-runtime.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. Hash/escopo dos pacotes e resultados dos22testes/2suítes/Checkstyle permanecem os anteriormente aprovados; não foram reexecutados nem contabilizados como uma nova bateria nesta publicação.

## Frontend e limites

O frontend pode usar o botão explícito de preencher total com [a proposta oficial](FRONTEND-TOTAL-INVOICE-SETTLEMENT-PROPOSAL.md), quando disponível. Conservar conferência/edição e confirmação explícita; ausência/ambiguidade continua manual. Parciais manuais, retenção sem proporção/FIFO inventados, limites oficiais revalidados no POST/aprovação,202 sem reserva e genérico bloqueado permanecem.

Integração frontend reportada antes desta publicação:49testes/2arquivos,15E2E no artefato com API interceptada e lint/tipagem/build/scanner aprovados. São provas reportadas pela conversa frontend, não execução real backend. Comunicar este resultado para a conferência pública final; não requer nova implementação ou mudança da configuração localtrue já autorizada.
