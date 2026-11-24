# Situação de serviços, encerramento de contratos e desconto

**Contrato vigente do complemento de feedback:** OpenAPI `F42E4998...`, JAR `D704704F...`, API local verificada em05/11/2026 às12:54:46SãoPaulo. Preserva as entregas anteriores; [evidências mais recentes](F03-19-CLOSURE-RECONCILIATION-FEEDBACK-LOCAL-ACTIVATION-2026-11-05.md).

05/11/2026. Implementação validada em 131 testes de oito suítes, com Checkstyle e bootJar aprovados. OpenAPI executável exportado e API local publicada/verificada em `localhost:8081` às 09:25:11 de 05/11/2026 (São Paulo). Não integrar por tipo manual nem tratar esta descrição como substituto do OpenAPI executável. A proposta anterior de recuperar desconto no serviço foi descartada após a correção do contexto pelo usuário; nenhum desconto real foi aplicado por agentes.

## Decisões confirmadas

- Serviço somente pode concluir quando sua posição oficial estiver quitada, por dinheiro confirmado e retenção de imposto comprovada pelos itens exatos da NF. Crédito ou histórico sem identidade suficiente exige conciliação. NF emitida, título inteiro recebido ou dinheiro de outro serviço não provam quitação deste serviço.
- O alvo é o valor comercial líquido oficial, considerando o desconto de cabeçalho distribuído pelo backend e as composições aceitas. Não comparar o bruto do serviço com caixa líquido nem ratear no frontend. Líquido zero sem compromissos pode concluir sem dinheiro.
- DRAFT e ACTIVE são pendentes. Para concluir o contrato, todos os serviços precisam ser COMPLETED ou CANCELLED, com posições compatíveis. COMPLETED e FINALIZED do contrato usam a mesma guarda. Contrato vazio pode concluir/cancelar quando não há compromissos.
- Cancelar contrato exige todos os serviços CANCELLED e ausência de compromissos financeiros vigentes. Cancelar serviço não quita dívida, cancela NF nem estorna dinheiro: compromissos precisam ser resolvidos pelo fluxo próprio, sem cascata.
- Contrato encerrado/cancelado precisa ser explicitamente reaberto antes de criar ou reativar serviços ou acrescentar composições de medição. A composição já exige destino ACTIVE; não reabrir automaticamente um serviço.
- Descontos de serviço e cabeçalho são preservados em alterações operacionais. Cobrança com saldo oficial aberto bloqueia novo desconto, mesmo sem NF. O contrato não pode ficar abaixo do dinheiro confirmado líquido de estornos; faturamento, capacidade de origem e descontos comprometidos continuam sendo limites adicionais mais restritivos.

## Operações de situação

`PUT /api/v1/company/contracts/{contractId}/status`

```json
{ "status": "COMPLETED", "justification": "Encerramento conferido" }
```

Aceita também `completedOn` opcional. Omitido/null preserva a data vigente no momento da aplicação, inclusive após aprovação; não inventa ou limpa uma data. Nenhum nome, preço, moeda ou outro campo do perfil é necessário.

`PUT /api/v1/company/contracts/{contractId}/services/{serviceId}/status`

```json
{ "status": "COMPLETED", "justification": "Serviço conferido" }
```

Não recebe nome, quantidade, preço ou desconto. O servidor lê esses valores sob lock. Em 202, a proposta contém somente a intenção de status; a aprovação não sobrescreve valores financeiros ou perfil alterados legitimamente depois.

Conclusão/reabertura usa alçada UPDATE do módulo CONTRACTS ou SERVICES. Transição para CANCELLED usa CANCEL, inclusive quando vier do PUT geral legado.202 exige outro aprovador autorizado, não altera status/dinheiro/data oficial e revalida ao aplicar. Pedidos antigos UPDATE que tentem efetuar cancelamento sem o fluxo CANCEL são bloqueados. As rotas gerais continuam protegidas pelas mesmas regras e não são atalhos.

Respostas:200 aplicação direta;202 somente proposta;400 validação;403 sem alçada;404 cadeia/tenant inválidos;409 regra ou concorrência. Erros usam Problem Details com correlationId e reason controlado quando aplicável.

## Elegibilidade oficial

`GET /api/v1/company/contracts/{contractId}/closure-eligibility?page=0&size=20&serviceId=<opcional>`

Exige CONTRACTS.READ + SERVICES.READ, tenant da sessão. O resumo sempre considera TODOS os serviços antes de filtrar/paginar. serviceId consulta exatamente um serviço do mesmo contrato, com 404 quando incompatível. Página padrão, page>=0,size1..100 e ordem fixa name/id crescente.

Resposta: contractId/status/serviceCount/nonTerminalServiceCount, completion/cancellation e services paginados. Cada serviço contém id/name/status e suas decisões completion/cancellation. Cada decisão contém:

```json
{ "eligible": true, "reason": null, "authorizationMode": "REQUEST_APPROVAL" }
```

DIRECT e REQUEST_APPROVAL são modos distintos: eligible=true não significa aplicação direta. Sem a alçada correspondente, eligible=false/reasonCLOSURE_NOT_AUTHORIZED/mode=null. Em especial, cancelamento não usa permissão de edição.

Decisões financeiras exigem BILLING.READ + RECEIVABLES.READ + INVOICES.READ. Sem esses READs, eligible=null/reasonCLOSURE_FINANCIAL_READ_REQUIRED quando a decisão depender do financeiro; bloqueios operacionais conhecidos ainda podem ser mostrados. Não expõe dinheiro, IDs financeiros, payloads de auditoria ou outros dados privados. Cache-Control no-store. Não cria reserva, proposta ou auditoria de mutação; PUT/aprovação continuam sendo a autoridade final.

## Motivos controlados

| Motivo | Interpretação |
| --- | --- |
| CONTRACT_HAS_NON_TERMINAL_SERVICES | Há serviço DRAFT ou ACTIVE |
| CONTRACT_SERVICES_NOT_CANCELLED | Cancelar exige todos CANCELLED |
| CONTRACT_HAS_UNSETTLED_SERVICES | Há impedimento financeiro de serviço sem motivo específico de conciliação |
| CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED | Há origem/atribuição financeira ou crédito que exige conciliação; não significa automaticamente dinheiro faltante |
| CONTRACT_HAS_FINANCIAL_COMMITMENTS | Existem compromissos que impedem cancelamento/encerramento sem serviços concluídos |
| SERVICE_NOT_FULLY_SETTLED | Cobertura ou quitação do líquido do serviço ainda incompleta |
| SERVICE_FINANCIAL_RECONCILIATION_REQUIRED | Origem/capacidade/histórico não comprovável; não inferir recebimento |
| SERVICE_CREDIT_REQUIRES_RECONCILIATION | Crédito não equivale a dinheiro recebido |
| SERVICE_HAS_FINANCIAL_COMMITMENTS | Cancelamento/valor zero incompatível com compromissos vigentes |
| CONTRACT_REOPEN_REQUIRED | Reabrir explicitamente antes de criar/reativar/acrescentar composição |
| SERVICE_REOPEN_REQUIRED | Serviço cancelado precisa de reabertura explícita antes de concluir |
| CANCELLATION_REQUIRES_CANCEL_AUTHORIZATION | Pedido antigo UPDATE não pode efetuar cancelamento |
| CONTRACT_HAS_OPEN_CHARGES | Há título com saldo oficial positivo, mesmo sem NF |
| CONTRACT_NET_BELOW_RECEIVED_AMOUNT | O novo líquido seria inferior ao dinheiro confirmado líquido de estornos |

ALREADY_COMPLETED/ALREADY_CANCELLED e motivos de alçada/leitura orientam as ações da tela; não alteram status. A nova guarda não reescreve situações históricas automaticamente após estorno ou ajuste: a posição volta a ser reavaliada e qualquer reabertura é explícita.

## Desconto

O bloqueio usa a posição financeira atual do título, não installmentStatusOPEN; títulos cancelados/renegociados não são classificados como abertos apenas por rótulo. Aplicação de desconto em contrato/serviço, criação com desconto e redução comercial pelo perfil respeitam as guardas, antes de 202 e na aprovação. A reversão permanece operação explícita com restrições históricas de suporte ao desconto; não é quitação nem liberação fictícia.

O frontend exibe os motivos oficiais e não soma valores para decidir elegibilidade. Resposta inconclusiva pede leitura do estado antes de repetir; as rotas de situação não possuem operationId. Nenhum fluxo autoriza alteração direta do banco, login fabricado ou compensação de NF/recebimento.

## Entrega

Validação: 131 testes de oito suítes, sem falhas/erros/skips, incluindo DIRECT,202/aprovação revalidada, alçada CANCEL, duas empresas, concorrência, quitação por origem, retenção, crédito, legado, descontos e composição de medição. Checkstyle main/test sem erros; bootJar gerado. Bancos de testes temporários; nenhuma operação real.

OpenAPI: `postman/canteiro-io.openapi.json`, SHA256 `71C62D9911C22DEC2B1C55280BA28257F322FD79CA78846C91793D9A60B36294`. Pacote candidato `0F247DFF16B878C16BAD8B3F7547C7BD1E90A3AB849901062AE50815D759C95A`; bibliotecas/configuração/migrations idênticas ao pacote anterior5E4601A1. Referência ativa conferida em 05/11; imagem local anterior preservada para retorno. Ativação local confirmada: `/v3/api-docs`200 application/json,150 paths/263 schemas; export151 paths/263 schemas. Componentes e caminhos compartilhados idênticos, exceto o webhook desabilitado ausente no runtime. As três rotas novas retornam401 ProblemDetails/correlationId sem autenticação. Schema 91 inalterado, Flyway e seedsfalse, mesmo ambiente/volume/rede/porta e container anterior compatível preservado. [Pacote, testes e provas](F03-19-CONTRACT-LIFECYCLE-LOCAL-ACTIVATION-2026-11-05.md). O frontend recebeu o contrato congelado e a comprovação de ativação; confirmou a integração local,75 testes distintos de integração de tela/API,31 de política e dois E2E em quatro larguras. O lint geral do frontend permanece pendente por incluir uma dependência em diretório temporário; detalhes nas evidências.

## Complemento de explicação de bloqueio — 05/11/2026

Correção validada em21 testes distintos e23 execuções:18 na rodada inicial, depois cinco direcionados ao complemento do resumo. Checkstyle main/test e bootJar aprovados. Publicada/verificada em localhost:8081 em05/11/2026 às12:54:46SãoPaulo. Um serviço com status histórico COMPLETED não deve ocultar um motivo financeiro impeditivo já calculado e autorizado. A projeção preserva o motivo de conciliação ou quitação pendente; SERVICE_ALREADY_COMPLETED é fallback quando não há impedimento financeiro disponível. eligible continua false. Sem os READs financeiros, não resolver/divulgar o motivo privado; o status cadastral conhecido pode continuar indicando já concluído.

O resumo do contrato distingue CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED de CONTRACT_HAS_UNSETTLED_SERVICES. SERVICE_FINANCIAL_RECONCILIATION_REQUIRED e SERVICE_CREDIT_REQUIRES_RECONCILIATION têm prioridade sobre os demais impedimentos financeiros de serviços. O primeiro informa que há origem/atribuição não comprovada ou crédito que exige conferência e não significa automaticamente falta de dinheiro. A prioridade de conciliação deve considerar todos os serviços antes da paginação/filtro. Nenhuma mudança na guarda de mutação, fórmula, status ou dado oficial.

Recebimento atribuído historicamente a uma parcela/alocação não equivale automaticamente a atribuição comprovada a um InvoiceItem. Uma eventual conciliação histórica precisa de proposta rastreável e autorização própria; não inferir pela igualdade de valor, data ou nome, não repetir dinheiro já recebido nem criar estorno fictício. Esse complemento entrega somente feedback legível e testes com dados sintéticos, sem iniciar workflow financeiro de conciliação. Diagnósticos de registros reais não são versionados nos documentos.

Contrato executável deste complemento: `postman/canteiro-io.openapi.json`, SHA256 `F42E4998680B2BAF346D9FDA343904600BFF9A2C5C89ABFBD636EDACA3969423`,151paths/263schemas. Somente descrições de ClosureDecisionResponse e ContractClosureEligibilityResponse alteradas; nenhuma rota ou propriedade nova. Pacote candidato `D704704FA1C836580DB0F2573FF254FFB3A504A57EC008B2B54FB8E21EFF3A24`: quatro classes de contratos, sem adições/remoções, com F05-20/bibliotecas/configuração/migrations idênticas à referência6477462F. Nenhum dado do diagnóstico real foi incluído no contrato ou nos documentos.

Ativação confirmada: `/v3/api-docs`200application/json,150paths/263schemas, componentes e caminhos compartilhados idênticos ao export F42E4998 (151/263), com apenas o webhook desabilitado ausente. Schema91 preservado, Flyway/seedsfalse; rollback6477462F disponível. [Provas da melhoria de feedback](F03-19-CLOSURE-RECONCILIATION-FEEDBACK-LOCAL-ACTIVATION-2026-11-05.md). A atualização explica o impedimento; não concilia histórico nem libera encerramento automaticamente.

Frontend confirmou integração final do feedback:26 testes/dois arquivos, build511/`index-BtZEfJi_`, scanner/lint da aplicação e cenário E2E em quatro larguras aprovados. A prova do navegador foi recuperada dos resultados/capturas existentes após interrupção, sem repetir bateria. Mensagem específica junto à situação do serviço e encerramento ainda bloqueado; nenhum dado real alterado. Lint global por dependências em tmp continua pendente, separado da integração. [Detalhes](F03-19-CLOSURE-RECONCILIATION-FEEDBACK-LOCAL-ACTIVATION-2026-11-05.md).

### Confirmação de destino no legado

NF vinculada à cobrança/alocação e recebimento atribuído à mesma alocação são fatos distintos da atribuição recebimento→InvoiceItem. Mesmo quando a cadeia oficial de IDs apresenta um único item candidato, isso sustenta uma proposta de conciliação, não uma escolha histórica automática. Uma eventual proposta deve identificar destinos e ambiguidades para confirmação humana; executar o reparo exige autorização específica, revalidação transacional e auditoria, sem repetir caixa ou forçar situação. Nenhuma operação de conciliação foi implementada por essa análise; não publicar rota/tipo fictício nem usar POST de novo recebimento como substituto. Detalhes reais de propostas permanecem fora dos documentos versionados.

Ferramenta pontual de manutenção F05-38 preparada/testada fora da API, com prévia READ ONLY e execução pontual posteriormente autorizada/aplicada em05/11/2026 às17:02:06SãoPaulo: [protocolo e provas](HISTORICAL-RECEIPT-LINK-MAINTENANCE.md). Não adiciona rota/tipo ou força encerramento; confirmação dos destinos foi separada da aprovação posterior da execução. Dados financeiros originais permaneceram inalterados e o predicado de histórico sem atribuição foi resolvido. Frontend deve reconsultar a elegibilidade atual e usar o encerramento normal autenticado, sem presumir liberação por cache ou por esta manutenção.

Após a conciliação pontual autorizada, frontend confirmou em sessão legítima que a elegibilidade oficial permite conclusão DIRECT e habilita Confirmar situação quando COMPLETED é selecionado no formulário local. Fechou o diálogo sem confirmar: nenhum PUT/status real foi alterado. O contrato continua exigindo a confirmação normal do usuário; não repetir a conciliação por esse resultado. [Prova](HISTORICAL-RECEIPT-LINK-MAINTENANCE.md).
