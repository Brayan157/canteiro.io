# XML de NFS-e e cobertura por serviço cobrado — F05-39/40/41

Prévia XML e cobertura por serviço publicadas isoladamente em localhost:8081 em 07/11/2026. [Validação do runtime](F05-39-41-VALIDATION-2026-11-06.md). A [confirmação de evidência fiscal após o registro](FRONTEND-INVOICE-XML-FISCAL-SNAPSHOT.md) é um recorte separado da F05-42, ainda sem publicação local.

## Regra oficial dos novos registros

Uma NF seleciona serviços inteiros **naquela cobrança**, sem fracionar livremente o montante cobrado do serviço. Exemplo fictício: cobrança com 100000.00 de A e 100000.00 de B permite NF de A, de B ou de ambos. NF de 50000.00 dividindo A/B é recusada. Não comparar com o valor contratual integral de A/B. Isso não altera a permissão de receber parcialmente uma NF/item.

O grupo é uma projeção determinística de empresa+contrato+cobrança+serviço, contendo **todas** as BillingAllocations ativas do serviço naquela cobrança. `billingServiceGroupId` é estável e não é uma nova linha/tabela: a identidade é derivada no servidor. Título/nome/descrição não identificam o grupo. Serviço igual em cobranças diferentes tem grupos diferentes. Origem de base e `measurementContractCompositionId` continuam individualizadas em cada linha.

O conjunto comercial corresponde ao modelo atual da cobrança, que já persiste exatamente as origens selecionadas. Não admitir que o cliente invente subgrupos para dividir um serviço. Novo GET não congela nem reserva capacidade: o POST usa IDs e valores das origens e revalida o conjunto completo sob lock do contrato. Cancelamento, acréscimo de origem ou mudança de valor entre GET e POST/aprovação invalida a seleção antiga, em vez de aceitar metade. Não há alteração/reconstrução de itens, composições, notas ou recebimentos históricos.

## GET da cobertura documental

`GET /api/v1/company/billing-installments/{billingInstallmentId}/invoice-coverage`

Exige **INVOICES.READ + BILLING.READ** e tenant da sessão. Resposta 200, `Cache-Control: no-store`, com:

- `billingInstallmentId`, `contractId`, `currency`, `calculatedAt` UTC;
- `available` e `unavailableReason`: ausência de origem comprovável, cobrança não aberta ou ajuste legado incompatível não viram zero;
- `amount`, `documentedAmount`, `remainingToDocumentAmount`: totais oficiais da cobrança antes de retenções, como strings decimais canônicas, ou null quando indisponíveis;
- `services[]`: grupos, nomes legíveis, valores e origens oficiais.

Cada serviço traz `billingServiceGroupId`, `contractServiceId`, `serviceName`, `state`, `selectable`, `blockingReason`, `amount`, `documentedAmount`, `availableToDocumentAmount`, `origins[]`. Cada origem contém `billingAllocationId`, `measurementContractCompositionId` opcional, `amount` e `documentedAmount`. Não somar esses valores no frontend; usar os totais entregues.

| state | Comportamento |
|---|---|
| NOT_DOCUMENTED | Pode selecionar somente o grupo inteiro quando `available/selectable` forem true. |
| FULLY_DOCUMENTED | Já documentado integralmente; não selecionar novamente. |
| LEGACY_PARTIAL | Há cobertura antiga fracionada, por valor ou subconjunto de origens. Mostrar os valores oficiais e a necessidade de conferência; não complementar silenciosamente. |
| REVIEW_REQUIRED | Origem/ajuste/saldo incompatível ou insuficientemente comprovado. Não selecionar nem fabricar grupo/valor. |

Um saldo numérico positivo de LEGACY_PARTIAL não autoriza NF desse saldo. O legado continua consultável e imutável; nenhuma conciliação foi executada. Ajuste negativo contratual sem serviço não é distribuído automaticamente entre grupos.

NF com itens de cobranças diferentes do mesmo contrato continua possível. Cada cobrança soma **somente seus InvoiceItems registrados**, pelas BillingAllocations exatas. Exemplo fictício: NF total 1000.00 com 900.00 da cobrança A e 100.00 da B documenta 900.00 em A e 100.00 em B. Não replicar o total da NF nas duas. Propostas e canceladas não consomem cobertura ativa.

## Registro manual e aprovação

Usar `POST /api/v1/company/invoices` com o contrato e as linhas oficiais de todos os grupos selecionados. O frontend copia os `billingAllocationId`/`amount` das origens, sem calcular distribuição ou permitir valor livre menor que o grupo. Mesmo um grupo com base+duas composições requer todas as três linhas exatas. A API calcula o total, verifica capacidades e exige o grupo completo; não basta selecionar uma origem inteira e omitir as demais do mesmo serviço/cobrança.

No request de criação, `documentTotalAmount` é string decimal canônica opcional para compatibilidade com o cadastro manual anterior. **O cliente assistido por XML deve enviar o valor documental revisado**, usando `sourceTotals.commercialAmountCandidate.value` quando disponível, nunca o líquido após retenções. A API compara esse valor com a soma oficial dos grupos completos e persiste a confirmação na proposta202 para revalidar na aprovação. Não calcular essa soma no frontend, omitir o campo para contornar divergência nem alterar o valor do XML para caber nos grupos. Ausência de candidato exige revisão/fluxo manual, não fórmula local. O campo não constitui validação de assinatura nem vínculo formal imutável com o arquivo: esse pacote é F05-42.

Alçada de criação e padrão 201 direto/202 por aprovação permanecem. Em 202 não há nota/itens/cobertura/reserva oficiais; a aprovação revalida dados correntes sob lock. Uma proposta anterior incompatível não ganha bypass. Uma operação com vários grupos é transação única; conflito não deixa parte registrada.

Razões de 409 Problem Details, sempre com correlationId:

| reason | Ação esperada |
|---|---|
| INVOICE_SERVICE_GROUP_INCOMPLETE | Recarregar/selecionar todas as origens e valores oficiais do grupo. |
| INVOICE_SERVICE_GROUP_ALREADY_DOCUMENTED | Grupo já documentado; não repetir. |
| INVOICE_SERVICE_GROUP_LEGACY_PARTIAL | Mostrar conferência do histórico; não completar por conta própria. |
| INVOICE_SERVICE_GROUP_REVIEW_REQUIRED | Não há base comercial integral comprovada para esse fluxo. |
| INVOICE_DOCUMENT_TOTAL_MISMATCH | Total documental confirmado difere dos grupos oficiais selecionados; revisar XML, documentos e seleção, sem rateio local. |
| INVOICE_FISCAL_IDENTITY_ALREADY_REGISTERED | Número+série ou chave já registrados no tenant; não criar nova identidade para contornar duplicidade. |

400 continua sendo usado para entrada inválida, inclusive precisão monetária incompatível; 403 sem permissão e 404 para contexto/origem ausentes ou estrangeiros. Nenhuma dessas guardas cria crédito, estorna caixa ou altera recebimento parcial. Unicidade V48 permanece inclusive para histórico cancelado; conflito fiscal não expõe a chave/número no erro.

## POST de prévia XML

`POST /api/v1/company/invoices/xml/preview`, multipart:

- `file`: parte obrigatória de um objeto multipart/form-data, arquivo `.xml`, MIME application/xml ou text/xml, até 10 MiB;
- `contractId`: parâmetro query obrigatório no OpenAPI, do tenant autenticado (Spring também aceita parâmetro de formulário);
- `billingInstallmentId`: parâmetro query de contexto inicial opcional; exige também BILLING.READ e deve pertencer ao mesmo contrato. Não limita toda a NF a essa cobrança.

Exige INVOICES.READ e alçada de criação de NF. Usuário REQUEST_CREATE recebe prévia **200**, com `creationMode=REQUEST_APPROVAL`; não é uma solicitação 202 nem uma NF provisória. Sem alçada, 403. IDs/contextos estrangeiros retornam 404. A consulta não confere autorização de suporte ou acesso irrestrito a outros módulos.

Resposta 200, no-store:

| Campo | Conteúdo |
|---|---|
| contractId/currency/creationMode | Contexto oficial; não aceitar tenant livre. |
| source | Perfil NFSE_NACIONAL_1_01, schemaValidationStatus=NOT_PERFORMED, signaturePresent, signatureValidationStatus=NOT_VERIFIED/NOT_PRESENT e substitutionIndicated. |
| prefill | Número da NFS-e, data de emissão quando comprovável e demais candidatos com `{value, sourcePath}`. `issuedOn.value` usa `YYYY-MM-DD` extraído de `/NFSe/infNFSe/dhProc` na data local indicada no XML. A série da DPS continua separada. |
| dps | Número, série, emissão e competência próprios da DPS. Não copiar automaticamente para cabeçalho da NF. |
| sourceTotals | Bruto do serviço, descontos reportados, retido total, líquido e candidato comercial antes da retenção; ausência é campo omitido, não zero. |
| taxCandidates | Tributo, base, alíquota aplicada, valor, código de retenção e withholdingStatus; valores presentes com proveniência. Nenhum tratamento oficial/obrigação é criado. |
| additionalTaxFields | Alíquota ISS declarada e CST PIS/COFINS disponíveis, separados de alíquota aplicada e obrigação. |
| partyChecks | Emitente/tomador comparados somente com Company/cliente da cadeia escolhida: MATCH/MISMATCH/UNDETERMINED. Não devolver documentos completos, endereços ou contatos. |
| billingContext | Cobertura oficial somente se a cobrança inicial foi informada e autorizada. Mesma estrutura do GET; não reserva. |
| requiresHumanReview/hasBlockingIssues/issues | Revisão sempre obrigatória; avisos/códigos/mensagens controladas e sem conteúdo bruto do parser. |

Dinheiro é normalizado para duas casas, percentual até quatro, sempre texto sem símbolos/formatação local. Não usar double/float, somar impostos/valores ou derivar saldo no frontend. O candidato comercial antes da retenção só é entregue quando líquido+retido explícitos conciliam com bruto menos descontos informados no perfil suportado, sem estrutura IBS/CBS não suportada; caso contrário fica indisponível com bloqueio. A prévia não é validação fiscal nem prova de que o XML pertence ao contrato.

Se `dhProc` estiver ausente ou inválido, a prévia omite `issuedOn` e mantém `NF_ISSUED_ON_REQUIRES_CONFIRMATION` como aviso não bloqueante. `dhEmi` e `dCompet` continuam na DPS. A data extraída só preenche a prévia. A NF e a evidência fiscal já registradas mantêm seus próprios dados. [Nota Técnica DANFSe 008](https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/rtc/nt-008-se-cgnfse-danfse-20260505.pdf/@@download/file).

A correção está ativa em `localhost:8081` desde 08/11/2026. O OpenAPI vivo manteve rotas e schemas, inclusive `InvoiceXmlHeader.issuedOn`. O JAR ativo é `ec97599b5f6959ceaceeefd5e7066e08d00707622447d3fca2d0bb729340c078`. Os dez testes de parser, prévia e evidência fiscal passaram. O histórico Flyway do banco local permaneceu na V93.

### Tributos e ausência de informações

ISS: código 1 é reportado como NOT_RETAINED; 2/3 como RETAINED, mantendo código/proveniência. Intermediário (3) requer revisão e não vira automaticamente WITHHELD_BY_CUSTOMER. Sem código ou com código desconhecido: UNDETERMINED. Não retido não significa automaticamente PAYABLE_BY_COMPANY.

PIS/COFINS e retenções federais disponíveis são apresentados sem classificação presumida; o recorte mantém UNDETERMINED onde a interpretação não foi implementada/comprovada. CST ou falta de montante não criam zero. `pTotTribSN` é percentual informativo, não valor de obrigação. Retido total sem componentes comprovados gera WITHHOLDING_BREAKDOWN_INCOMPLETE, sem inventar tributo ou ratear diferença.

Já existe cálculo tributário explícito AMOUNT/PERCENTAGE com base, alíquota, confirmedAmount e operationId. Usá-lo somente com confirmação e aritmética compatível; não alterar o valor fiscal para caber na fórmula. AMOUNT não finge armazenar uma base/alíquota calculada. Abrir XML não chama criação tributária, não cria Payable e não reaplica retenção existente.

### Avisos, substituição e bloqueios

Uma referência de substituição **não impede ler a prévia nem ver os campos extraídos**. SUBSTITUTION_REQUIRES_REVIEW é aviso não bloqueante para preenchimento assistido após conferência. Seu alcance é impedir que a interface interprete a referência como autorização de cancelar/substituir uma nota antiga; não existe essa ação automática no recorte. A criação manual posterior continua sujeita a cobertura e unicidade oficiais. Se o histórico impedir registrar a nova nota, mostrar o conflito e usar o fluxo formal autorizado; nunca estornar recebimento verdadeiro para forçar passagem.

`hasBlockingIssues` sinaliza que não se deve aplicar automaticamente o conjunto de candidatos para preenchimento financeiro — por exemplo emitente/tomador divergentes/não comprovados, moeda não compatível, totais não conciliados ou IBSCBS não suportado. Os dados legíveis ainda podem ser apresentados para conferência. Não é um endpoint de confirmação nem um bloqueio fiscal externo da NF: não há upload/registro formal vinculado a esse preview stateless. A interface não deve afirmar que a NF foi importada/validada ou que o XML ficou vinculado ao cadastro.

Erros: 400 para arquivo malformado, DTD/XXE/XInclude, perfil não suportado, repetição ambígua de campos ou decimais incompatíveis; 401/403 autenticação/permissão; 404 contexto; 413 limite do arquivo/requisição, em Problem Details. O limite multipart do ambiente inclui overhead e pode ser mais restritivo.

## Etapas e limites de segurança

1. Escolher contrato/cobrança por identidade oficial, enviar XML e consultar grupos/origens.
2. Mostrar candidatos e proveniência; revisar data/série, partes, totais e tributos. Descrição livre não seleciona ContractService, composição ou BillingAllocation.
3. Selecionar grupos inteiros e confirmar os dados. Registrar NF pelo endpoint oficial, que aplica/revalida a guarda. Resposta 202 deixa tudo oficial inalterado.
4. Após existir NF oficial, o upload de anexos aceita um PDF com a alçada direta requerida. A prévia XML é temporária. Evidência fiscal pode usar um XML legado já vinculado à NF, e o fluxo próprio para novas evidências permanece planejado. Crie tributos somente nas operações explícitas autorizadas. [Guia de anexos](FRONTEND-INVOICE-ATTACHMENTS.md).

Preview não persiste o arquivo/campos e não cria anexo/ChangeRequest/Invoice/InvoiceItem/TaxObligation/Payable/baixa/reserva. Guarda somente auditoria mínima de consulta: ator/tenant/data/perfil/resultado, sem valores, descrição, documentos, assinatura ou chave. Respostas sem cache persistente; frontend não grava XML/candidatos em localStorage/IndexedDB/logs/telemetria. Texto extraído é dado não confiável: renderizar como texto, nunca como HTML ou link executável. Não existe URL pública, consulta SEFIN ou emissão. O XML privado do usuário não foi usado como fixture nem executado como importação financeira.

Parser usa as bibliotecas existentes do JDK (JAXP/XPath), a validação estrutural de anexo já existente e BigDecimal. Limite de 10 MiB e limites estruturais do validador; DTD, entidades externas, XInclude e rede desabilitados. Não confundir extração com validação XSD, assinatura, autenticidade/autoridade fiscal, situação atual ou antimalware.

Fonte de produção: [Portal NFS-e](https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual). Código de retenção ISS e separação bruto/retido/líquido também foram conferidos no [manual público municipal de integração do padrão nacional, versão 1.01](https://portalcontribuinte.duquedecaxias.rj.gov.br/pdfs/Manual_integracao_v101.pdf). O ZIP de esquemas do portal respondeu 403; não houve contorno ou alegação de validação por XSD. Perfil de extração é deliberadamente limitado; formatos/versões externos não são aceitos por semelhança.

## Integração, evidências e runtime

OpenAPI exportado da aplicação de teste por `/v3/api-docs`: **3.1.0, 156 paths/280 schemas**, SHA-256 `9FF228DE1D38A0EB61281C4C4DBE147F824A6F00C48FB4C8F31C4350BD0EEF42`. JAR final `75CA838F067375B5ED95C0071ED5064F16409D1F36A730665982A5641A5FDEE0`; [validação, escopo e pendências de publicação](F05-39-41-VALIDATION-2026-11-06.md). O arquivo não prova publicação em localhost:8081. A nova guarda e o XML devem ser habilitados juntos no frontend somente após confirmar o runtime correspondente.

Este recorte não adiciona migration. Porém o build compartilhado contém a V92 da entrega de fornecedores/dados para pagamento, ainda não autorizada no banco persistente; não publicar esse build sobre schema antigo por inferência nem aplicar aquela migration por autorização deste recorte. Nenhuma mudança de configuração/chave, operação financeira, emissão fiscal, push ou publicação é autorizada por esta documentação.
