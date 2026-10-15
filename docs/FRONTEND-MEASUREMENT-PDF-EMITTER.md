# Identificação da empresa emitente no PDF de medição

Data: 15/10/2026. **Implementado, testado e publicado localmente com autorização humana**. Escopo exclusivo de PDF, sem alteração de tela, XLSX, rota, payload ou tipos OpenAPI.

## Comportamento

O cabeçalho do PDF mostra sempre a identificação textual da **Company emitente do tenant autenticado**, inclusive quando existe logo:

- logo privada existente, quando disponível/compatível;
- nome cadastrado: nome fantasia quando preenchido, senão razão social;
- CNPJ cadastrado, formatado sem regravar o valor;
- título da medição centralizado em bloco separado abaixo da identificação.

Não usa o cliente final, texto fixo, CNPJ de exemplo ou imagem enviada na requisição do relatório. A consulta continua exigindo MEASUREMENTS.EXPORT, tenant e auditoria. Company/documento e logo são carregados internamente pelo backend. Não há nova URL pública ou DTO web com chave privada.

O bloco é repetido em cada página, com largura reservada à logo, quebra do nome e cálculo da altura antes do título e da tabela. Permanecem dez colunas, serviços/subtotais, histórico/aceitos, moedas/totais oficiais, streaming/cache temporário, filename e prévias inline.

Sem documento: **CNPJ: não informado**. Se o documento cadastrado não tiver formato de CNPJ, indica **CNPJ: não informado (documento cadastrado incompatível)**, sem transformar CPF/documento estrangeiro em número de CNPJ. A apresentação aceita 12 posições alfanuméricas e dois dígitos finais, com a mesma máscara do formato numérico, conforme a [Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/cnpj/cnpj-alfanumerico.pdf/view). Isso é formatação do cadastro, não consulta fiscal, validação dos dígitos, regularidade cadastral ou mudança nas regras de criação de Company.

## Caminhos existentes abrangidos

Após `/api/v1/company/measurements/{measurementId}`:

| GET | Conteúdo |
|---|---|
| `/accumulated/pdf` | PDF acumulado attachment |
| `/accumulated/spreadsheet/preview` | PDF acumulado inline |
| `/versions/{versionId}/pdf` | PDF histórico da versão attachment |
| `/versions/{versionId}/spreadsheet/preview` | PDF da versão inline |

Ambos os geradores usam o mesmo cabeçalho PDF. A planilha XLSX, inclusive seus metadados/IDs/compatibilidade de importação, permanece inalterada. O frontend apenas baixa/exibe o Blob autenticado e conserva Content-Disposition: não monta ou edita o PDF localmente nem precisa gerar novos tipos.

## Provas

- 20 testes unitários/de geração aprovados em seis suítes; Checkstyle main/test aprovado.
- 12 testes de integração aprovados: prévia, marca da emitente/isolamento entre empresas, seis rotas HTTP reais/CORS e OpenAPI.
- Seis testes das amostras repetidos após acrescentar subtotais oficiais às fixtures de QA; não são seis testes únicos adicionais.
- Os testes medem posição do CNPJ/nome/título/tabela, preservação de margens, repetição em todas as páginas, documentos ausentes/incompatíveis, CNPJ numérico/alfanumérico, logo válida/inválida, texto completo e células XLSX inalteradas.
- Evidências: `tmp/validation-pdf-identity/{unit,integration,final-samples}`. OpenAPI de teste permaneceu `4C19DC6650DC7C82F1650A054EDFF971491C51DEB7BB0AEB05DAFD3A65D36892`; não substitui o export vivo do backend/frontend.
- Três amostras sintéticas finais em `output/pdf`: `medicao-pdf-emitente-com-logo.pdf` (duas páginas), `medicao-pdf-emitente-sem-logo.pdf` (uma página, histórico) e `medicao-pdf-emitente-cabecalho-longo.pdf` (seis páginas, nome/título longos e CNPJ ausente). As nove páginas foram renderizadas pelo Poppler e inspecionadas integralmente sem sobreposição/corte do cabeçalho. PNGs em `tmp/pdfs/identity-render`.

Os dados/imagem das amostras são fixtures fictícias. Nenhuma imagem real foi escolhida/enviada e nenhuma Company/dado comercial foi alterado pelo agente. As 32 verificações são distintas; os seis testes finais de amostras são repetição de um subconjunto, não total adicional. Não foi executada/anunciada uma suíte geral de todo o sistema.

## Publicação local autorizada

O humano respondeu “Pode publicar localmente” à pergunta explícita sobre colocar nome/CNPJ no PDF do site preservando banco, anexos e volumes. Somente canteiro-api foi recriado; não foram alterados infraestrutura, Compose, Dockerfile, usuário, volume, segredo, gateway, imagem cadastrada ou registros comerciais.

Comparação das **1.371 entradas de cada JAR** antes de publicar encontrou exatamente três classes diferentes: MeasurementCompanyBranding, MeasurementCompanyBrandingLoader e StreamingMeasurementAccumulatedReportGenerator$PdfTable. Writers XLSX, recursos, migrations e bibliotecas são byte a byte iguais. SHA-256 do JAR candidato: `8E50CCF759BC53E182C68E152833E4BD5B307CF8ABF5E765055514BA10F5D0D3`.

- Imagem: `sha256:c470982eab9e6f6ba3e6b7dd9894c0d1e241cd892ae64f60e84f8cd0a16ea628`.
- Container: `a6b7350b606c8ca66bc5dad9bc03dce6e94b63a35dcf004f30de4a9b5c4405ec`.
- Startup concluído em `2026-10-15T17:10:52Z` (14:10 local), zero reinícios automáticos na verificação final.
- GET /v3/api-docs e catálogo: 200. OpenAPI vivo **inalterado**, `7166C8DED235CA31E6C3B517C5DDF1F7C68C0A034F72692F1781D21B9B75D878`, 123 paths/193 schemas; sem regeneração de tipos necessária para este recorte.
- CORS continua expondo X-Correlation-Id e Content-Disposition; rota de empresa sem token permanece 401.
- Flyway segue na V84, 84 migrations, checksum -228426622, sem migration nova.
- PostgreSQL/Mailpit mantiveram seus IDs e início. Hashes de Company, Contract, Measurement, MeasurementVersion e MeasurementItem ficaram iguais antes/depois.
- Volume privado canteiroio-api-private-storage e modo/dono 100:101/0700 preservados, API como canteiro (não root). Um arquivo privado antes/depois, com mesmo manifesto SHA-256 `ee0f32211e2a4308202c19845b19705d0b31ff9d93352f4b8fa3aaa0d3a3c9f7`.
- Backup preventivo PostgreSQL interno: `/tmp/canteiro-before-pdf-identity-20261015.dump`, criado/listado sem exportar dados pessoais. Não substitui backup externo/restore testado.

A consulta final de leitura confirmou apenas flags: a emitente do exemplo tem referência de logo cadastrada e documento com formato de CNPJ exibível. Nenhum CNPJ, chave ou imagem real foi reproduzido em logs/artefatos de QA. Pacotes/comparador/export vivo estão em `tmp/pdf-identity-publication`.

As provas de layout e texto deste recorte são as amostras sintéticas e testes de rotas, não um download autenticado da empresa real realizado pelo agente do backend. O frontend pode reabrir/baixar o PDF oficial na própria sessão para confirmar a identificação real; não deve alterar o arquivo localmente. Nenhuma nova mensagem foi enviada à conversa do frontend sem autorização adicional; o resultado está disponível no status/documentos deste trabalho.
