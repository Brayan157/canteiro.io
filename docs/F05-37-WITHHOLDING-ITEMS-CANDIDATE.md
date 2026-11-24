# F05-37 — retenção proporcional por item, candidato e retorno

29/10/2026. Candidato testado, conferido pelo frontend e ativado somente em8081 às23:42:22SãoPaulo. [Registro](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md). Plano prévio preservado abaixo. Sem migration, seed ou operação financeira real.

Decisão humana comprovada: “Sim, distribuir proporcionalmente entre os serviços”. A distribuição é do imposto registrado, sobre os valores brutos dos InvoiceItems exatos, no escopo integral do título original. Não autoriza rateio de recebimento parcial, entre títulos, alteração de dinheiro histórico ou atualização fiscal automática. [Contrato](FRONTEND-INVOICE-ITEM-WITHHOLDING.md).

## Evidências

- 50 testes distintos/4suítes aprovados em2m32s:43 ManualInvoiceSettlementApiIntegrationTest,5 InvoiceWithholdingCapacityTest,1 InvoiceSettlementServiceTest,1 OpenApiContractIntegrationTest. Zero falhas, erros ou ignorados. Checkstyle main/test e bootJar aprovados. Não foi executada toda a suíte do projeto.
- Cenários novos: proporção por item, centavos determinísticos, exemplo790000/5000=785000, líquidos e saldo após parcial manual, estorno, replay,202 sem efeito, aprovação que revalida novo imposto, tenant/permissões, histórico acima do novo líquido, escopo entre títulos e base divergente. As demais regressões manuais preservam concorrência, créditos, renegociação e origens aceitas.
- Resultados: `tmp/validation-withholding-items/build/test-results/test`; resumo em `tmp/withholding-items-candidate/tests-summary.json`.
- Export executável: `postman/canteiro-io.openapi.json`, SHA256 **49F89DB0775210BFE9FF56D89487AC589C68CC8E37B9B1B6142D836791D27DDE**,148paths/257schemas. Delta do0C36E221: somente schemas InvoiceSettlementOptionResponse/TotalInvoiceSettlementProposalResponse e descrição do GET de opções. Três novos campos required nullable string/decimal, padrão duas casas.
- JAR: `tmp/withholding-items-candidate/canteiro.io-withholding-items.jar`, SHA256 **E97C650CBC8F23B5DE8F62CE7438845D02D43574A38B18D9DC628AED2E8691BE**. Comparado integralmente ao E09835F5:13classes alteradas e3adicionadas, todas receivables. Bibliotecas, configuração e migrationsV1–V91 idênticas; zero remoções. Manifesto `tmp/withholding-items-candidate/package-scope.json`.

## Atualização local e retorno

Baseline conferido: canteiro-api ativo com JAR **E09835F5734B5B6BBB5AEBF287D3EA01A2C6B2665DC00AD4D6E36011741950BE**, imagem `sha256:0986127243bcdcc71176e7e8b3fa7914c7b5497fe04cf83b520b1cc82ffeafea`, Flyway91/success/checksum-1539369050. Preservado em `tmp/withholding-items-candidate/rollback-e09835f5.jar`.

Após conferência, imagem por cópia do JAR validado, sem recompilação ou download de dependência. Script preserva ambiente sem imprimir segredos, rede, volume privado, porta8081 e restartpolicy; Flyway e ambos os seeds false. API anterior parada fica preservada como `canteiro-api-before-withholding-items-20261029`.

Verificar readiness/hash exato, schema91 inalterado, OpenAPI200/componentes e caminhos compartilhados idênticos ao export,401ProblemDetails+correlationId anônimo. Única rota exportada ausente na instância deve ser o webhook de pagamento desabilitado. Não usar NF7 ou outra operação real para teste.

Se falhar: parar candidato, preservar nome para diagnóstico e restaurar o container/JAR E09835F5 compatível com schema91, sem apagar ou restaurar banco. Como a mudança é de cálculo/projeção sem migration, o retorno não precisa reescrever registros históricos. Não executar Compose contra um build não conferido.
