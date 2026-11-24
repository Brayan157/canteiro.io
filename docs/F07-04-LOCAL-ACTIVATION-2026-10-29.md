# Controle de faturamento PDF — ativação somente local em29/10/2026

Autorização humana verificada diretamente na conversa frontend: **“Sim, atualizar somente a API local sem migrations”**, para o pacote351C5B5A... do relatório. Executada somente a troca da API8081, sem migration/seed, login, upload, geração de relatório com dados reais ou operação financeira real. Em seguida, o usuário autorizou que pedidos de implementação/correção incluam a atualização local necessária; decisão registrada no AGENTS backend14.1, sem ampliar esta execução.

## Resultado

- Startup às **13:28:27 America/Sao_Paulo**, `2026-10-29T16:28:27.176Z`; container `canteiro-api` iniciado às16:28:01UTC.
- Imagem `canteiroio-api:local-contract-billing-pdf-20261029`, ID `sha256:a641a9417e440a2be80ea127cc246318d3947c3077787ab9a5564fa77542d0de`, criada exclusivamente por cópia do JAR preservado sobre a baseB971 verificada. Sem recompilar a árvore compartilhada.
- JAR ativo SHA-256 `351C5B5A59C4EF10190901E03EDD62479FDDB543CB4276343862CBE60CE0594C`.
- Schema permanece90/checksum1503018190;90 registros Flyway,0falhas,max90. Flyway automático, seed owner e seed demo continuamfalse. Nenhuma migration/runner/SQL de mutação executado.
- Ambiente privado idêntico ao anterior; rede `canteiroio_default`, porta8081→8080, volume privado `canteiroio-api-private-storage` em `/app/var/storage` e restart policy preservados.
- `/v3/api-docs`: **200 application/json**,3.1.0,147paths/257schemas. Todos os caminhos compartilhados/componentes estruturalmente idênticos ao export aprovado `B0A3B9ACCAADDC3176525C46A39684DBB2D0820D947A5775F07FEE56788B37B5`,148paths/257schemas. Única ausência prevista: `/api/v1/webhooks/payments` local desabilitado. Sem diferenças inesperadas. Nova rota PDF presente com resposta200application/pdf/binary.
- GET anônimo de `/api/v1/company/billing-control/contracts/00000000-0000-0000-0000-000000000001/report/pdf`: **401 application/problem+json**, UNAUTHENTICATED e correlationId. GET opções de recebimento, POST recebimento com corpo vazio e GET índice de contratos deNF também401. Sem autenticação ou operação financeira.
- Container anterior B971/F05-37 compatível com ledgerV90 preservado e parado: `canteiro-api-before-contract-billing-pdf-20261029`. Retorno conforme [plano revisado](F07-04-CANDIDATE-AND-ROLLBACK.md), sem apagar auditoria, restaurar banco ou usar versão anterior ao ledger.

## Provas e integração

Evidências filtradas em `tmp/contract-billing-pdf-candidate/publication-runtime.json`, `live-contract-evidence.json`, `live-openapi.json` e `startup.txt`. Testes39distintos/4suítes, Checkstyle e QA de10páginas sintéticas são as validações anteriores do mesmo pacote, não uma nova rodada ou homologação autenticada real.

Frontend pode usar o [contrato PDF](FRONTEND-CONTRACT-BILLING-PDF.md) já integrado no detalhe do controle de faturamento: quatro permissões existentes, Blob autenticado, filename do header, nenhuma soma local ou rota de medição. A publicação preserva a distinção entre NFs, cobranças, saldo a emitir, saldo alocado semNF, descontos e recebimentos. Nenhum ajuste de configuração ou deploy remoto é autorizado por este registro.
