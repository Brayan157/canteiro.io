# Conciliação pontual de vínculos históricos — preparação

Estado: ferramenta preparada e testada, prévia somente leitura concluída em05/11/2026 às16:28:11SãoPaulo. Execução pontual posteriormente autorizada expressamente e aplicada em05/11/2026 às17:02:06SãoPaulo; integridade conferida às17:02:18. Confirmação humana de destinos não é autorização de aplicação real. A API local permanece na referênciaD704704F/F42E4998; a conciliação pontual autorizada foi aplicada por processo separado, sem atualizar/reiniciar a API.

## Recorte

Ferramenta operacional isolada, sem rota pública, componente Spring registrado, scheduler, migração ou seed. Não inicia SpringApplication. Usa JDBC/PostgreSQL e transações existentes na stack; não cria outro módulo financeiro. Opera apenas um contrato FIXED/OPEN de uma empresa e um serviço COMPLETED sem composição, com duas baixas confirmadas, dois títulos originais, alocações SERVICE ativas e dois destinos NF/item explicitamente informados pelo operador autorizado.

Esse contexto de manutenção é o plano revisado e aprovado pelo responsável, não companyId livre de payload HTTP nem atuação de suporte por bypass. Não concede permissões a usuários de plataforma/suporte. Flags técnicas e acesso ao banco não substituem a aprovação humana exigida por AGENTS14.1.

Cada baixa tem uma atribuição antiga por alocação e um único item de NF na cadeia atual. A ferramenta valida os IDs fornecidos; não escolhe destino por nome, valor, data ou serviço. Exige valores e datas esperados exatos. Renegociação, retenção, crédito, composição, cancelamento, mais registros ou atribuição parcial/ambígua interrompem o procedimento; não há conversão automática de escopo.

## Protocolo

1. Preparar manifesto privado com operationId novo, companyId/contractId/serviceId/currency e exatamente duas entries. Cada entry contém settlementId, receivableId, installmentId, allocationId, invoiceId, invoiceItemId, expectedAmount em string decimal canônica, receivedOn, invoiceNumber e invoiceIssuedOn. Não incluir credenciais, dados bancários ou documento fiscal completo.
2. Rodar modo padrão de validação. Ele usa transação READ ONLY e devolve apenas hashes do plano/estado e contagem. Não insere vínculo, auditoria, dinheiro ou reserva.
3. Apresentar plano, escopo e pacote testado ao humano. Receber autorização de execução específica, distinta da confirmação de destinos. Nunca transformar confirmação anterior em referência de aprovação financeira.
4. Somente após essa autorização, aplicar com --apply, --approved-plan-sha256, --expected-state-sha256 e --approval-reference. Os hashes devem corresponder à prévia aprovada; qualquer divergência aborta. O comando valida aprovação antes de acessar banco e sempre exige seleção explícita de aplicação.
5. Revalidar sob locks na ordem contrato→NFs→parcelas→recebíveis→recebimentos, em transação SERIALIZABLE. Inserir somente os dois vínculos, conferir que dados financeiros originais continuam idênticos e registrar novo AuditEvent SYSTEM, com referência de aprovação e hashes. Não forjar ator humano nem retroagir a data do evento.
6. Validar constraints e confirmar tudo ou nada. Operação repetida com o mesmo plano/aprovação e vínculos exatos retorna ALREADY_APPLIED sem nova auditoria, baixa ou alteração. Histórico parcialmente associado/incompatível é recusado.
7. Conferir saldo/posições e elegibilidade pela aplicação. A ferramenta não conclui contrato ou serviço; o fluxo normal de autorização/aprovação continua obrigatório.

A aplicação preserva valores/datas das baixas, títulos, NFs, alocações, descontos e status. O ledger antigo por alocação não é duplicado. Auditoria registra somente os IDs da relação, fato de caixa inalterado e referência técnica da autorização, sem copiar valores financeiros ou dados livres. Não há exclusão física nem enfraquecimento de triggers.

## Entrada operacional

Classe principal: `com.renovar.canteiro.io.receivables.infrastructure.maintenance.HistoricalReceiptLinkMaintenanceCommand`.

Execução em JVM separada pelo PropertiesLauncher já incluído no bootJar. O comando não inicia API ou jobs e não carrega Flyway. Ambiente exige DATABASE_URL/DATABASE_USERNAME/DATABASE_PASSWORD (ou equivalentes SPRING_DATASOURCE_*; usuário/senha também aceitam POSTGRES_*). Credenciais vão somente pelo ambiente, nunca por argumentos/manifestos. URL PostgreSQL com credenciais embutidas é recusada.

Padrão: `--plan /caminho/privado/plan.json`. Modo real exige os quatro argumentos adicionais documentados acima; placeholders não são aprovação. Não execute a partir deste documento. O pacote exato e as evidências estão registrados abaixo; sua existência não autoriza execução real.

## Dados e disponibilidade

Manifestos e evidências com dados reais pertencem somente a tmp privado/ignorado ou armazenamento operacional controlado; nunca versionar valores, identificadores ou documentos do caso. Guias/OpenAPI do frontend não receberão rota/tipo fictício: não existe endpoint de conciliação entregue por este recorte. Nenhum build/reinício do servidor é necessário apenas para preparar essa ferramenta.

Após commit, os vínculos e o evento são imutáveis; não existe desfazer por DELETE ou restauração de container. Qualquer correção posterior exigirá outro procedimento auditado. Antes do commit, uma falha desfaz a transação inteira. A imagem operacional terá ENTRYPOINT fixo na classe de manutenção; não usar java -jar como substituto, pois o bootJar também contém a aplicação normal.

## Pacote preparado e verificação

JAR operacional: **9BA1AA10CD948605061D600B995BCDC3931DD87A39A69BC7EA3A18707997A1A7**, em `tmp/historical-receipt-linking-candidate/canteiro.io-historical-receipt-links.jar`. Imagem local `canteiroio-maintenance:historical-receipt-links-20261105`, ID `sha256:b60a4c2d412e20ad03d8b69a07a95c830c9d83df4c8eb180d706931619fab126`, com ENTRYPOINT fixo no comando e HEALTHCHECK NONE. O contexto de build exclui manifestos privados e permite somente Dockerfile/JAR; credenciais não são incluídas na imagem.

Delta integral contraD704704F: sete classes adicionadas (plano/aprovação/entrada, executor/resultado e comando/opções), zero alterações/remoções de classes existentes. Bibliotecas, configuração e migrations byte a byte idênticas. Export OpenAPI gerado permaneceu byte a byte igual aoF42E4998680B2BAF346D9FDA343904600BFF9A2C5C89ABFBD636EDACA3969423,151paths/263schemas. Nenhuma alteração em contratos HTTP, publicação/reinício da API ou migration real.

Dez testes distintos aprovados em rodadas: nove cenários de manutenção e o OpenApiContractIntegrationTest. Cobrem validação sem mutação, ator SYSTEM e auditoria imutável, dinheiro/datas/status originais preservados, elegibilidade canônica antes/depois em fixture, repetição, concorrência, isolamento entre empresas, plano/estado/referência divergentes, histórico parcialmente associado, retenção/renegociação incompatíveis e rollback integral quando a auditoria falha. CLI recusa aplicação sem argumentos de aprovação antes de acessar manifesto ou banco. Não foi executada toda a suíte do projeto.

As falhas iniciais eram da fixture: ausência de transação na atribuição antiga; corrigida essa preparação. Uma rodada posterior tentou alterar valor original imutável e foi corretamente bloqueada pelo banco; o teste passou a verificar a imutabilidade e divergência do valor esperado no plano, sem desativar triggers. Esse caso foi reexecutado isoladamente e passou, sem repetir os nove já aprovados. Checkstyle main/test sem erros e bootJar aprovado. Evidências brutas preservadas em `tmp/validation-historical-receipt-linking/fixture-failure`, `before-amount-fixture-fix` e `final-targeted`; o manifesto de pacote consolida o resultado mais recente por caso, sem contar reexecuções como testes distintos.

A imagem foi testada em containers sem rede/dados do cliente: modo sem manifesto retorna PLAN_FILE_REQUIRED; --apply sem aprovação retorna APPROVAL_REQUIRED. Ambos encerram com código2 antes de qualquer conexão e sem iniciar SpringApplication/API/jobs/Flyway/seeds. Prova: `isolated-cli-evidence.json` no diretório do candidato.

A prévia do plano privado confirmado foi executada somente em leitura. Além da transação READ ONLY, a conexão recebeu default_transaction_read_only=on; o container usa filesystem somente leitura, manifesto montado readonly e capacidades removidas. Retornou VALIDATED_ONLY, com original financeiro idêntico antes/depois e zero novos vínculos/eventos da operação. Os hashes do plano/estado e logs ficam em arquivos privados ignorados, não neste documento nem no frontend. Nenhum recebimento/estorno/status/desconto foi alterado.

Provas técnicas em `tmp/historical-receipt-linking-candidate`: `package-scope.json`, `isolated-cli-evidence.json`, `validate-readonly-evidence.json`, `readonly-integrity-evidence.json`, wrappers de execução/verificação. O manifesto do cliente está em `tmp/historical-receipt-linking/proposed-plan.json`, confirmado como ignorado pelo Git, e a marcação de autorização conserva o histórico de preparação e agora registra a aprovação humana expressa e a aplicação verificada. O wrapper padrão apenas valida; aplicação real requer o switch e os três argumentos de aprovação, o mesmo JAR revisado, a referência ativa esperada e estado ainda idêntico. Divergência interrompe a operação, sem selecionar novos destinos.

Inicialmente o usuário confirmou somente os destinos; a preparação não executou reparo. Depois de revisar o pacote, o usuário autorizou expressamente os dois vínculos e a auditoria, conforme [AGENTS14.1](../AGENTS.md#141-atualização-local-necessária-ao-pedido-humano--decisão-de-29102026). Não existe endpoint novo para o frontend ou autorização automática por este documento. A aplicação pontual registrada abaixo não autoriza outros registros. Após eventual execução autorizada, consultar elegibilidade e usar o encerramento normal; não forçar situação. Sem commit/push/publicação remota neste recorte.

## Aplicação pontual autorizada — 05/11/2026

Autorização direta do humano recebida nesta conversa com a resposta “autorizo”, após pergunta específica de execução dos vínculos e auditoria. Referência e destinos permanecem nos arquivos privados; não foram retransmitidos ao frontend ou versionados. A aprovação foi vinculada ao mesmo plano/estado previamente revisado, não à atualização genérica de código da AGENTS14.1.

Antes de aplicar, o comando padrão READ ONLY reconferiu plano/estado e a referência D704704F/artefato9BA1AA10; ambos os hashes permaneceram idênticos aos aprovados. A aplicação sob locks retornou APPLIED em17:02:06SãoPaulo (20:02:06 UTC), com somente dois invoice_settlement_line e um AuditEvent SYSTEM em transação única. Nenhuma repetição do comando de aplicação foi necessária.

Verificação posterior exclusivamente READ ONLY às17:02:18SãoPaulo confirmou: digest dos registros financeiros originais inalterado; pares de IDs exatos correspondentes ao plano e ao evento; ator SYSTEM sem userId forjado e referência de aprovação/hashes corretos. O predicado UNPROVEN_HISTORY foi lido do código de produção JpaInvoiceSettlementRepository e consultado no mesmo escopo: false após a associação. Valores/datas/status/descontos/NFs/títulos e ledger antigo por alocação preservados; nenhum novo recebimento, estorno ou fechamento forçado.

Provas privadas adicionais: `approved-preview-evidence.json`, `pre-apply-snapshot.json`, `apply-evidence.json`, `post-apply-integrity-evidence.json` e estado de autorização. A API continua D704704F, com OpenAPI F42E4998 inalterado; sem migration, seed, publicação/reinício ou alteração de regra financeira. Frontend avisado somente do estado técnico e orientado a reconsultar a elegibilidade por sessão legítima, sem PUT ou sessão fabricada para validação. O contrato não foi concluído pela ferramenta: conclusão continua pelo fluxo normal autenticado, após revalidação da elegibilidade.

Esta tarefa pontual foi concluída; não representa entrega de módulo/endpoint genérico de conciliação nem autorização para outros reparos. Dados privados do caso não integram o protocolo público.

## Verificação autenticada do frontend após a aplicação

Frontend confirmou em sessão legítima já aberta, somente leitura: ao selecionar COMPLETED no formulário local, a elegibilidade oficial indicou aplicação DIRECT após confirmação e habilitou Confirmar situação. O diálogo foi fechado sem confirmar; nenhum PUT ou alteração de situação/financeiro foi executado. Essa evidência confirma a revalidação da aplicação, além do predicado SQL anterior; não é autorização de nova conciliação nem prova de contrato já concluído. Registro técnico também feito pelo frontend em seu guia F06-27, sem dados privados.
