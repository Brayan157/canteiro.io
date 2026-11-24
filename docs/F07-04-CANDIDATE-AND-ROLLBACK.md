# Controle de faturamento PDF — pacote revisável e retorno

29/10/2026. **Publicado somente na API local8081 após autorização humana específica**, sem migrations/seeds ou operações financeiras reais. As autorizações anteriores de F05-32/F05-37 não foram reutilizadas. [Provas da ativação](F07-04-LOCAL-ACTIVATION-2026-10-29.md), [contrato de integração](FRONTEND-CONTRACT-BILLING-PDF.md). Para trabalhos futuros, observar a decisão humana de atualização local no AGENTS14.1, sem repetir confirmação pelo mero novo pacote do mesmo pedido.

## Artefatos e provas

- JAR `tmp/contract-billing-pdf-candidate/canteiro.io-f07-04.jar`, SHA-256 `351C5B5A59C4EF10190901E03EDD62479FDDB543CB4276343862CBE60CE0594C`.
- Export executável em `postman/canteiro-io.openapi.json` e cópia `tmp/contract-billing-pdf-candidate/canteiro.openapi.candidate.json`, SHA-256 `B0A3B9ACCAADDC3176525C46A39684DBB2D0820D947A5775F07FEE56788B37B5`,148paths/257schemas. Acrescenta somente `/api/v1/company/billing-control/contracts/{contractId}/report/pdf`; caminhos compartilhados e componentes inteiramente idênticos ao export anterior7BF8E221.... Nenhum DTO/enum JSON novo.
- BootJar aprovado em22s, compileJava/processResources UP-TO-DATE no mesmo output validado. Resultado agregado:39 testes distintos/4suítes aprovados em rodadas identificadas, sem somar reexecuções. Últimos lotes:8 testes API/contrato em2m14s e6 do gerador/QA em54s, Checkstyle main/test aprovado. [Detalhes e resultados](FRONTEND-CONTRACT-BILLING-PDF.md).
- Comparação por hash de todas as entradas descomprimidas contra JAR F05-37/B971...:1544→1559 entradas; **1classe existente alterada,15classes adicionadas, zero removidas ou inesperadas**. Allowlist por classe, sem liberar módulo inteiro. Bibliotecas, configurações, loader e migrationsV1–V90 idênticos. Manifesto/comparador em `tmp/contract-billing-pdf-candidate/package-comparison.json` e `compare-package.ps1`.
- Única classe existente alterada: `ContractNetAmountCalculator`, para expor a mesma regra por subtotal agregado, sem lista integral de serviços. Método antigo delega à mesma lógica; quatro testes de domínio aprovados, incluindo paridade explícita do subtotal com desconto percentual e ajuste de medição. Nenhuma fórmula financeira nova, rateio, baixa ou alteração de regra de emissão.
- Classes novas restritas ao controller, caso de uso, portas/projeções e adapters JDBC/PDFBox deste relatório. Fluxos de recebimento/estorno/ledgerV90/F05-37 não foram substituídos.

Referência ativa conferida somente por leitura em29/10 às13:19:16 America/Sao_Paulo: JAR `B971C83CB82DBDA6BACBF1485DECCC5220F38D8B8D115FE96432FB359F09CF37`, imagem `canteiroio-api:local-total-invoice-settlement-proposal-20261029`, ID `sha256:e2637a2958f9f6ffd47604e2655411c5e2bbfd26d540bffc6c8e614762f8beb4`, em execução desde12:19:37UTC. Schema90/checksum1503018190, Flyway automático/owner seed/demo seedfalse. [Metadados privados filtrados](../tmp/contract-billing-pdf-candidate/baseline-runtime.json). Conferir novamente antes de eventual troca; registro não garante ausência de mudanças posteriores.

## Publicação futura — depende de autorização específica

1. Revisar este pacote e a integração frontend. Confirmar autorização humana para atualizar **somente API8081 com este JAR, sem migrations**, sem login/operações financeiras reais; não inferir aprovação de relatório ou de mensagens entre agentes.
2. Conferir hash ativoB971..., schema90 e configuração/rede/porta/volume. Se diferir da referência, parar a substituição para revisar. Não recompilar a árvore compartilhada nem incorporar outras mudanças.
3. Construir imagem somente por cópia do JAR preservado sobre a base ativa conferida; conferir hash dentro da imagem. Preservar container/imagem anteriores compatíveis com ledgerV90, configuração privada, storage e política de restart.
4. Substituir somente a aplicação local, com Flyway automático e seedsfalse. Não executar runnerV90, migration, SQL de mutação, clean/repair/baseline, seed, restauração de banco, upload, relatório com dados reais ou comandos financeiros como parte desta troca.
5. Conferir startup/hash/schema90/flags e OpenAPI200 com o novo caminho. Todos os caminhos compartilhados e componentes devem equivaler ao export aprovado, admitindo somente a ausência conhecida do webhook payments local desabilitado. Verificar401 ProblemDetails/correlationId na rota nova sem autenticação; não precisa gerar PDF de cliente real para essa prova.
6. Comunicar evidências ao frontend antes de declarar o relatório disponível localmente. Não publicar remoto ou outros formatos/módulos.

## Retorno

Retornar somente à versão atual F05-37/B971..., preservando schema90, ledger, recebimentos, propostas e storage. O PDF novo não cria schema nem formato persistido financeiro, portanto a referência continua compatível com os mesmos vínculos V90; o frontend precisa tratar a ausência da rota/mostrar indisponibilidade se houver retorno.

Não usar F05-36/FCEE... ou versões anteriores ao ledgerV90 para mutações financeiras. Não apagar auditorias geradas pelo relatório, restaurar banco sobre operações posteriores, desfazer migrations ou estornar dinheiro para trocar aplicação. Conferir startup/hash/flags após um retorno; não declarar restauração apenas porque o comando foi solicitado.

## PDFs exclusivamente sintéticos

Amostras pequenas para revisão/fixture autorizada do frontend: `output/pdf/billing-control-synthetic-logo.pdf` e `output/pdf/billing-control-synthetic-fallback.pdf`,5páginas cada. Identidades/logo/valores são inventados para testes e não copiam os PDFs privados do usuário. Hashes e renderização em `tmp/contract-billing-pdf-candidate/pdf-qa-manifest.json`; dezpáginas finais conferidas visualmente. Não são relatórios de dados reais nem autorização de emissão fiscal.
