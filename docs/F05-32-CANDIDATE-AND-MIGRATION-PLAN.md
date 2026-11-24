# F05-32 — Candidato, aplicação da V90 e retorno

Preparado em 28/10/2026. **Ativado somente localmente em 29/10/2026 sob nova autorização humana específica:** V90 aplicada pelo Flyway target90 e JAR final ativo na API8081, sem seeds ou operações financeiras reais. A autorização anterior de F05-36 não foi reutilizada. [Resultado, provas e limites de recuperação](F05-32-LOCAL-ACTIVATION-2026-10-29.md).

## Artefatos preservados e provas

- [JAR candidato](../tmp/invoice-manual-settlement-candidate/canteiro.io-f05-32.jar): SHA-256 `0E25A12A80175D955C17383E1859A8CFB1754A51700B7CD77864275537D576ED`.
- `bootJar` aprovado junto da validação final focada, BUILD SUCCESSFUL2m16s: mesmo bytecode executado nos testes finais, sem alterações alheias no pacote. O empacotamento inicial de18s foi sucedido pelos dois endurecimentos abaixo.
- OpenAPI executável: `postman/canteiro-io.openapi.json`, 3.1.0, 147 paths/255 schemas; SHA-256 `27037B8762036EA7F6B695D5D6A966F92BB73C602F31DBB8055DD8764833B607`.
- [V90 aditiva](../src/main/resources/db/migration/V90__preserve_manual_invoice_item_receipt_links.sql): SHA-256 `5787A8ACEC8571307F268A73BA1A94669287B254C0D69EAAA14C4585B941D2BB`.
- Referência de comparação: JAR F05-36 preservado, SHA-256 `FCEEA62E421945764C8B8DB92D82E1C1F83CC59284DD903195EC5908DD488597`. Nova publicação exige conferir a referência realmente ativa; se divergir, revisar novamente antes de substituir.
- [Manifesto por entrada](../tmp/invoice-manual-settlement-candidate/package-comparison.json) e [comparador](../tmp/invoice-manual-settlement-candidate/compare-package.ps1): 1.519→1.541 entradas, 13 alteradas/22 adicionadas/nenhuma removida, sem diferenças fora da lista explícita. Inclui somente classes do fluxo manual/consulta/estorno e V90. Bibliotecas, configurações, loader e migrations V1–V89 são idênticos por conteúdo.
- Três records internos de ReadInvoicesUseCase foram regenerados; `javap -p -c -s -constants` confirmou bytecode/assinaturas equivalentes, sem diferenças semânticas. [Revisão preservada](../tmp/invoice-manual-settlement-candidate/nested-record-review.json).
- [Contrato, 89 testes/11 suítes e limites](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md). Relatórios em `tmp/validation-invoice-manual-settlement/build`.

O candidato inicial C81618AF... foi preservado como `tmp/invoice-manual-settlement-candidate/canteiro.io-f05-32-initial-C81618AF.jar` e **não é o release final**. O pacote final inclui verificação adicional de que o serviço de InvoiceItem coincide com o da alocação e limite de observação1000, conforme a coluna existente. O snapshot inicial978FE33B... foi preservado em `canteiro.openapi.initial-978FE33B.json`; o export final acima altera maxLength de note para1000 e continua147paths/255schemas. Os tipos devem ser regenerados pelo snapshot final. A V90 não mudou.

Na preparação de28/10 ainda não havia imagem/container de F05-32. Em29/10 a imagem local foi criada exclusivamente do JAR final preservado e publicada após a autorização específica. O conteúdo do pacote foi comparado por hash de cada entrada descomprimida, não apenas por nomes. O manifesto de conformidade de escopo não é autorização de publicação.

## Efeito da V90

V90 adiciona uma constraint única sobre a identidade já única de invoice_item, duas tabelas (`invoice_settlement_operation`, `invoice_settlement_line`), índices, foreign keys de tenant/cadeia e triggers de integridade/imutabilidade. A soma das linhas deve igualar a baixa no commit; estorno preserva exatamente o original. Não há backfill, reatribuição de histórico, alteração de valores anteriores ou exclusão física.

O pacote depende da V90 para consultar/gravar o novo ledger. **Não publicar somente o JAR sobre V89 com migrations desligadas e depois habilitar o formulário.** Não reutilizar automaticamente o helper de F05-36, que valida V89 e foi autorizado para uma troca sem migrations.

## Sequência de ativação — executada somente localmente em29/10

As etapas abaixo foram concluídas no escopo backend; habilitação local do gate pertence ao frontend. [Provas da execução](F05-32-LOCAL-ACTIVATION-2026-10-29.md). Novas publicações/migrations não são autorizadas por este registro.

1. Obter autorização humana específica para aplicar **somente V90 aditiva pelo Flyway** e atualizar somente a API local em8081 com o JAR acima. O frontend mantém `VITE_ENABLE_INVOICE_SETTLEMENTS=false` até a ativação comprovada; essa flag não substitui autorização no backend.
2. Conferir hashes, pacote ativo, schema/histórico Flyway atual V89, identidade do banco e plano de recuperação. Preservar o container/imagem atual, configuração privada e volume de storage; nenhuma operação deve apagar banco/volume ou imprimir credenciais.
3. Revisar o comando e o runner Flyway operacional antes da execução. Usar somente migrations extraídas do pacote aprovado, validação de checksums ligada e target90; abortar se houver checksum divergente ou qualquer outra migration pendente. Não usar `clean`, `repair`, baseline automático, SQL manual fora do Flyway ou reconstrução de schema. A V90 pode adquirir lock para adicionar a constraint/índices; coordenar a janela de escrita da API.
4. Executar a migração uma única vez no banco confirmado, em etapa controlada separada de jobs/seeds. Conferir V90 bem-sucedida e nenhuma mudança histórica/atribuição automática. O mecanismo/credenciais ficam privados; não colocar senhas em argumento ou arquivo de documentação.
5. Publicar exclusivamente o JAR preservado, mantendo os seeds owner/demo e a execução automática de Flyway na API desligados, além da mesma rede/porta/volume/política operacional. Não recompilar a árvore suja para montar a imagem.
6. Conferir hash ativo, startup, V90, flags, `/v3/api-docs` e respostas anônimas401; comparar todos os caminhos compartilhados/componentes com o export aprovado, admitindo somente a diferença já conhecida do webhook local desabilitado. Não executar upload, recebimento, estorno ou outra operação financeira real sem autorização própria.
7. Comunicar as provas ao frontend antes de reconsiderar seu gate de ativação. Depois da ativação, novo recebimento usa exclusivamente `/invoice-settlements`; o genérico `/settlements` permanece bloqueado, sem bypass de configuração.

## Retorno e compatibilidade do ledger

- Se a migração falhar, não habilitar o novo fluxo nem publicar o candidato sobre schema incompleto. Inspecionar o histórico Flyway e a causa sem `repair`/limpeza automática. Não afirmar que a aplicação anterior foi restaurada sem conferir seu estado.
- Se V90 tiver sido aplicada e **nenhum recebimento com novas linhas tiver sido gravado**, é possível considerar retorno somente da aplicação ao pacote F05-36 preservado, mantendo as tabelas aditivas e o formulário novo bloqueado. Pedidos pendentes novos devem permanecer preservados e não ser aprovados por versão que desconhece seu handler.
- Se já existirem recebimentos/estornos com vínculos V90, **não recolocar uma versão antiga para executar mutações financeiras desses registros**. F05-36 não grava as linhas de NF no estorno. Preferir correção/retorno a build compatível com o ledger; se não houver build compatível, manter a operação financeira indisponível/instância em manutenção até decisão explícita. A simples remoção do botão não protege a API antiga.
- Nunca remover as tabelas/linhas V90, reconstruir vínculos, estornar dinheiro verdadeiro ou restaurar banco sobre lançamentos atuais para trocar aplicação. Um retorno de aplicação não desfaz operações de negócio já autorizadas.

Conciliação de legado, origem de créditos/renegociações e substituição de NF recebida permanecem fluxos separados, com bloqueio conservador no candidato. Não é necessária uma regra nova de rateio para os novos vínculos íntegros já implementados.
