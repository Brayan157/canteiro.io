# SDD — Plataforma SaaS de Gestão Financeira de Obras

**Nome comercial:** a definir  
**Repositório atual:** Canteiro.io  
**Versão do documento:** 0.9<br>
**Data:** 15 de outubro de 2026<br>
**Status:** requisitos de negócio consolidados; recomendações técnicas identificadas como tal  

Atualização 0.7: medição contínua, identidade de serviço entre avanços, destino contratual persistido, composição incremental e visão/relatórios acumulados. Estas decisões especificam o comportamento exigido; o estado de implementação permanece no backlog e a disponibilidade de cada rota depende do OpenAPI executável.

Atualização 0.8: contratos de implementação da medição contínua, reconhecimento por raiz de item, crédito/renegociação de recebíveis e atribuição imutável de baixas. Integração publicada no [guia atual do frontend](FRONTEND-MEASUREMENT-CONTINUOUS.md); verificação e pendências são registradas no backlog.

Atualização 0.9: moedas explícitas BRL/USD/EUR, imutáveis e sem câmbio; relatórios oficiais com dez colunas, marca privada, agrupamentos e apresentação monetária pt-BR. Publicação local autorizada em 14/10/2026, com V84 aplicada e OpenAPI vivo exportado; [F04-41 concluída em 15/10/2026](F04-41-LOCAL-PUBLICATION.md) após confirmação do frontend de contrato/tipos sincronizados, moedas e PDF/XLSX autenticados com filename oficial. CORS e storage local privado foram publicados com autorizações separadas; a logo real aguarda o usuário/interface do frontend, sem autorizar envio de imagem por agentes.

## 1. Objetivo

Definir o desenho técnico e funcional do MVP de uma plataforma SaaS multiempresa para controle financeiro de obras. Neste produto, **Obra** é uma entidade própria: pertence a um cliente final e pode concentrar vários contratos da empresa prestadora.

O documento orienta a evolução do backend Spring e fornece:

- limites do MVP;
- regras de negócio e estados;
- arquitetura e isolamento multiempresa;
- modelo de dados conceitual;
- estratégia de permissões, aprovação e auditoria;
- integrações externas;
- backlog priorizado, dependências e nível de dificuldade.

O frontend será um projeto separado no futuro e consumirá a API REST Spring. Esta primeira arquitetura deve ser frontend-ready, mas não inclui a implementação da interface web.

## 2. Glossário

| Termo | Significado |
|---|---|
| Plataforma | O produto SaaS, cujo nome comercial ainda será definido. |
| Proprietário da plataforma | Administrador principal do SaaS. Gerencia empresas, planos, assinaturas e equipe de suporte. |
| Empresa assinante | Empresa que contrata e utiliza a plataforma. É o tenant dos seus dados. |
| Cliente final | Cliente cadastrado pela empresa assinante, por exemplo, a empresa dona de uma usina. Não é cliente da plataforma. |
| Obra | Local/projeto de execução pertencente a um cliente final. Pode ter endereço próprio, ocorrer na sede da empresa prestadora ou em outro local, e pode conter vários contratos. |
| Contrato | Relação comercial vinculada a uma única obra. Uma obra pode ter vários contratos. |
| Serviço do contrato | Item comercial da obra. Cada ocorrência pertence a exatamente um contrato. |
| Modelo de serviço | Modelo reutilizável da empresa. Ao ser usado, é copiado para o contrato e torna-se independente. |
| Funcionário | Pessoa cadastrada pela empresa para identificação e operação interna. Seu perfil é permanente; seus vínculos de trabalho são históricos e independentes. |
| Vínculo de trabalho | Período histórico de trabalho de um funcionário em uma empresa. Pode passar por experiência, efetivação, aviso prévio, desligamento e readmissão. Não é o contrato comercial de uma obra. |
| Conformidade do funcionário | Controle administrativo de cursos, certificados, exames e documentos necessários ao exercício de uma função, com validade e alertas. Não armazena diagnóstico ou prontuário médico. |
| Medição | Documento contínuo que pode ser alimentado durante vários meses, preservando o mesmo `measurementId`, título e serviços. Versões registram avanços/aceites. Pode nascer sem obra e contrato; o vínculo posterior é explícito e obrigatório antes da finalização, composição ou efeito contratual. |
| Serviço contínuo da medição | Identidade estável de um serviço durante toda a medição, independente do título e do grupo aceito de cada versão. Preserva o destino comercial e recebe novas atividades em avanços posteriores. |
| Avanço da medição | Conjunto incremental de itens de uma versão, sujeito a seu próprio aceite externo. Uma revisão comum não copia nem reaplica os valores aceitos anteriores. |
| Composição de medição | Aplicação comercial imutável de uma origem aceita em um serviço do contrato; preserva medição, avanço, serviço contínuo, grupo e itens de origem. |
| Faturamento | Planejamento e registro de parcelas a cobrar por serviços de um contrato. |
| NF | Nota fiscal registrada no sistema. Pertence a um único contrato, pode possuir vários itens de serviços e não representa recebimento automático. |
| Conta a receber | Título financeiro a receber, com vencimento, baixas e situação própria. |
| Receita da obra | Consolidação das receitas de todos os contratos pertencentes à obra; não é lançada diretamente na obra. |
| Gasto da obra | Custo, despesa ou imposto vinculado à obra. Pode detalhar um contrato, mas não depende dele. |
| Gasto da empresa | Obrigação ou despesa sem obra vinculada, como imposto geral da empresa. Entra em caixa e gastos gerais, mas não no lucro de uma obra. |
| Conta a pagar | Obrigação a fornecedor, imposto ou terceiro, classificada com escopo Obra ou Empresa. |
| Auditoria | Registro imutável de quem fez, aprovou, alterou, cancelou ou consultou uma ação relevante. |

## 3. Escopo e fronteiras

### 3.1 Incluído no MVP

1. Administração da plataforma:
   - empresas assinantes;
   - catálogo de planos/módulos cumulativos;
   - regras de preço para combinações de planos;
   - período de teste de 30 dias;
   - assinaturas, cobranças, inadimplência, desbloqueios de confiança e cortesias temporárias;
   - usuários de suporte da plataforma.

2. Identidade, segurança e governança:
   - login, recuperação de senha, sessões/tokens e bloqueio de acesso;
   - isolamento estrito dos dados por empresa;
   - papéis e permissões granulares;
   - solicitações de aprovação, quando o usuário não possuir alçada direta;
   - trilha de auditoria de ações, alterações, aprovações, cancelamentos e atuação do suporte.

3. Operação da empresa assinante:
   - cadastro de clientes finais, pessoas físicas ou jurídicas;
   - obras, contratos, endereço/local, status e histórico;
   - catálogo de modelos de serviço e serviços do contrato;
   - descontos por serviço e por contrato;
   - medições contínuas, avanços/revisões, destino contratual, planilhas e aceite externo registrado internamente;
   - faturamento, parcelas, NFs, impostos, contas a receber e recebimentos;
   - fornecedores, custos, despesas, contas a pagar e pagamentos;
   - fluxo de caixa e relatórios financeiros.
   - funcionários, vínculos de trabalho históricos, documentos privados, cursos, certificados, exames periódicos e alertas de validade.

4. Relatórios iniciais:
   - clientes ativos;
   - valores a receber;
   - contratos abertos;
   - contratos finalizados;
   - contratos a receber;
   - controle de faturamento;
   - gastos, custos, receita e resultado por obra;
   - gastos gerais da empresa.

5. Saídas e documentos:
   - exportação de planilhas e PDF de medição por versão e acumulados;
   - PDF, Excel e DOCX para relatórios selecionados;
   - anexos de contratos, planilhas/aceites de medição e PDF/XML de NF.

### 3.2 Fora do MVP

- frontend web e aplicativo móvel;
- portal do cliente final para aceitar medições diretamente no sistema;
- emissão, consulta ou autorização de NF junto à prefeitura, SEFAZ ou outro órgão fiscal;
- integração bancária, conciliação automática ou importação de extratos;
- cálculo fiscal automático. Os impostos serão informados pelo usuário;
- rateio automático de despesas administrativas entre obras;
- integrações com ERP, contabilidade, folha de pagamento ou CRM;
- relatórios ainda não descritos.

### 3.3 Decisões de escopo

| ID | Decisão |
|---|---|
| D-01 | Obra é uma entidade própria, na tabela obra (entidade Work). Contrato não representa obra. |
| D-02 | A tabela FinalCustomer (final_customer) pertence à empresa assinante. Uma empresa possui vários clientes finais; cada cliente final possui várias obras. |
| D-03 | Uma obra possui obrigatoriamente um único cliente final, local/endereço próprio ou tipo de local de execução, e pode conter vários contratos. |
| D-04 | Um contrato pertence obrigatoriamente a uma única obra e pode existir inicialmente sem serviço. |
| D-05 | Um serviço de contrato pertence a somente um contrato. Reutilização significa copiar um modelo ou serviço anterior, nunca compartilhar o mesmo registro entre contratos. |
| D-06 | Uma medição pode ser criada sem obra e sem contrato. `contractId` nunca pode existir sem `workId`, e o contrato deve pertencer à obra e ao tenant. O vínculo posterior é explícito, auditado e sujeito à alçada/aprovação; a obra vinculada não pode ser trocada e o contrato pode ser acrescentado depois, mas não substituído silenciosamente. Antes de Work e Contract válidos, a medição não pode ser finalizada, convertida em serviço nem produzir efeito financeiro. |
| D-06A | A exclusão de medição é cancelamento lógico e auditado, sujeito à alçada/aprovação. Só é permitida quando `contractId` estiver ausente; o vínculo apenas com obra não impede o cancelamento. Versões, itens, documentos e eventos nunca são apagados fisicamente. |
| D-07 | Aceite da medição ocorre fora do sistema. A empresa usuária registra o aceite internamente e guarda a evidência/anexo quando houver. Rejeição externa exige descrição das correções; pode marcar itens ativos da mesma versão. Motivo e itens ficam imutavelmente retidos na versão rejeitada, auditados e visíveis no detalhe. A revisão seguinte pode partir de versão aceita ou rejeitada; quando rejeitada, cria nova versão `DRAFT` ligada à anterior e copia seus itens ativos, sem modificar a versão rejeitada. |
| D-08 | Toda inclusão, alteração, cancelamento e exclusão lógica é auditada. Ação com permissão direta, ou com a combinação de solicitação e aprovação no mesmo módulo/operação, possui alçada direta efetiva; somente as demais aguardam aprovação. |
| D-09 | Usuário da empresa pertence a uma única empresa. Usuário de plataforma/suporte é global e pode atuar em várias empresas conforme suas permissões. |
| D-10 | Não haverá exclusão física de dados financeiros, obras, contratos, serviços, NFs ou medições. O sistema usa cancelamento, inativação e histórico. |
| D-11 | Uma NF pertence a um único contrato e pode distribuir seu valor entre vários serviços desse contrato. Um serviço pode ser faturado em várias NFs. |
| D-12 | O sistema não permite que o faturamento acumulado aprovado ultrapasse o valor líquido do contrato. |
| D-13 | Uma redução de valor/desconto do contrato que deixaria o valor líquido abaixo do faturamento líquido já aprovado é bloqueada até haver estorno, crédito ou renegociação correspondente. |
| D-14 | O onboarding público de uma empresa exige ao menos um plano. Ele cria a Company, o proprietário inicial pendente, o vínculo com a Company, as seleções de plano e o token de ativação; não depende de aprovação manual da plataforma. |
| D-15 | O proprietário inicial recebe automaticamente o papel `Company Administrator` com todas as permissões ativas do catálogo controlado no momento do onboarding. Após ativar a conta, possui alçada direta efetiva, sem deixar de gerar auditoria nas ações futuras. |
| D-16 | Desconto de contrato é uma linha de ajuste contratual de faturamento, separada dos valores dos serviços; ele não regrava nem rateia os preços dos serviços. |
| D-17 | Controle de faturamento, parcelas, saldo faturável, NFs e contas a receber pertencem ao contrato. A obra somente consolida esses valores entre seus contratos. |
| D-18 | Receita, custos, gastos vinculados e lucro são apurados no nível da obra, somando os resultados dos seus contratos e os gastos diretamente atribuídos à obra. |
| D-19 | Todo gasto possui escopo Obra ou Empresa. Gasto de Empresa não possui obra/contrato, integra caixa e relatórios gerais da empresa, mas não altera lucro de qualquer obra. |
| D-20 | O `DELETE` operacional de dados pessoais de funcionário, como endereço, conta bancária e chave Pix, é exclusão lógica: desativa o registro e o retira das consultas operacionais, sem apagar a trilha de auditoria. A eliminação ou anonimização definitiva é um fluxo separado de privacidade, autorizado e auditado, executado apenas após avaliar a finalidade e as obrigações de retenção aplicáveis. |
| D-21 | Conta bancária, dígito de conta e chave Pix de funcionário são referências para pagamento manual, não uma integração bancária. Listagens e detalhes operacionais exibem somente valores mascarados. A visualização do valor completo exige ação explícita, permissão granular controlada `EMPLOYEES.VIEW_PAYMENT_DETAILS`, tenant derivado da sessão e `AuditEvent` imutável de consulta; não pode ser exportada, registrada em logs ou mantida em cache do cliente. Quando houver chave Pix e nome/cidade confirmados do seu titular, a visualização pode gerar BR Code/QR estático sem valor, cobrança, vencimento ou identificador financeiro; esses artefatos são efêmeros, nunca persistidos/auditados, e dados do funcionário não podem ser inferidos como dados do titular. |
| D-22 | O proprietário inicial da empresa e o papel criado para ele no onboarding são protegidos pelo sistema. Eles permanecem legíveis nas listagens por transparência, sinalizados como `companyOwner` e `systemProtected`, mas a administração da própria empresa não pode desativar o proprietário, trocar seus papéis, alterar/desativar o papel protegido, modificar suas permissões ou atribuir esse papel a outro usuário. Uma eventual transferência de propriedade é fluxo separado, auditado e fora do MVP. |
| D-23 | A central de aprovações pesquisa e apresenta solicitações apenas por projeções seguras, legíveis e derivadas no servidor. A busca considera o solicitante e o registro por nome/título sanitizados, sempre dentro do tenant autenticado e com `AUDIT.READ`; nunca consulta nem expõe documentos, PIS, telefones, endereços, remuneração ou referências de pagamento. A aprovação de uma criação retorna a entidade efetivamente criada quando o caso de uso a fornecer. |
| D-24 | A auditoria apresenta o responsável por `actorDisplayName`, um nome seguro e legível — jamais e-mail, UUID ou identificador técnico. Para usuário vinculado a funcionário, usa o nome completo do funcionário; `SYSTEM` aparece como “Sistema”; sem nome histórico seguro, usa “Usuário histórico indisponível”. O e-mail do ator, quando disponível, é um campo separado (`actorEmail`) e somente é retornado a quem já possui `AUDIT.READ`. |
| D-25 | A foto de funcionário é opcional e serve exclusivamente para identificação visual interna. Não é usada para reconhecimento facial, extração biométrica, ponto, vigilância ou decisão automatizada. É armazenada em chave privada, disponibilizada somente por download autenticado no tenant, limitada a PNG/JPEG validado de até 2 MiB e 2048 × 2048 pixels, sem cache público. A auditoria registra apenas a existência/remoção da foto, nunca sua imagem ou chave de storage. |
| D-26 | A central de aprovações pode projetar no solicitante o `employeeId` e a rota autenticada `photoUrl`, exclusivamente quando o usuário estiver vinculado a funcionário da mesma empresa e tiver foto. A projeção não revela chave de storage, URL pública ou dado pessoal adicional; o download mantém sua própria permissão `EMPLOYEES.READ`. |
| D-27 | Clientes finais e funcionários possuem consulta paginada e filtrável exclusivamente no tenant autenticado. Cliente final pode ser pesquisado por nome, CPF/CNPJ normalizado, tipo e situação; funcionário, por nome, documento normalizado, cargo, departamento, vínculo, modalidade/remuneração, situação de trabalho e situação do cadastro. Filtros de documento e remuneração são internos à empresa, exigem a permissão de leitura do módulo correspondente e não são indexados nem expostos fora da API autenticada. A edição de cliente final usa a alçada direta/solicitação de aprovação já existente e preserva auditoria antes/depois. |
| D-28 | Obras e contratos possuem consulta paginada e filtrável exclusivamente no tenant autenticado. Obras podem ser pesquisadas por nome, referência, cliente final, situação, tipo/local de execução e períodos de início/previsão; além do UUID opcional, o cliente final pode ser localizado por trecho de nome ou CPF/CNPJ normalizado. Contratos podem ser pesquisados por nome, referência, obra, nome do cliente final da obra, situação e períodos de início/previsão/conclusão; os filtros relacionais por nome são resolvidos somente no servidor dentro do tenant. A edição usa alçada direta ou solicitação de aprovação e registra auditoria antes/depois. A edição de obra preserva `finalCustomerId` e a edição de contrato preserva `workId`: uma troca desses vínculos exige fluxo explícito futuro, pois pode afetar a hierarquia comercial, medições e faturamento. |
| D-29 | Relatórios, planilhas e documentos potencialmente volumosos são produzidos por streaming. A leitura ocorre em lotes ordenados e limitados; a resposta HTTP grava diretamente no fluxo de saída, sem montar uma lista completa de linhas ou um `byte[]` completo do arquivo. XLSX usa escrita de linhas em disco temporário e PDF usa cache temporário configurado. O tamanho do lote e a janela de linhas são parâmetros técnicos limitados, e toda consulta mantém tenant, permissão, filtros e auditoria. |
| D-30 | Contrato possui `contractIdentifier` opcional, texto livre de negócio para número, código ou nome externo do instrumento (por exemplo, `0001/2027` ou `Contrato padrão`). Ele não é único, nem mesmo dentro da empresa, pois clientes finais distintos podem reutilizar a mesma numeração ou padrão. |
| D-31 | Desconto de serviço e ajuste de desconto de contrato podem ser alterados ou revertidos somente por ação explícita, com justificativa, alçada direta ou solicitação de aprovação e auditoria antes/depois. Reversão não apaga evento nem registro: o serviço volta a não ter desconto e o desconto de contrato é desativado logicamente. Para reproduzir um desconto anterior, registra-se uma nova alteração com o valor desejado; não há rollback automático de auditoria ou faturamento. |
| D-32 | O perfil de `Employee` é permanente no tenant e não representa, por si só, um vínculo de trabalho ativo. A fonte canônica de situação laboral é `EmployeeEmployment`: cada admissão ou readmissão cria um novo vínculo histórico. O vínculo anterior nunca é reaberto, sobrescrito ou apagado. |
| D-33 | `EmployeeEmployment` registra início, tipo de vínculo, situação, período de experiência opcional, efetivação e desligamento. Situações permitidas são `PROBATION`, `ACTIVE`, `NOTICE_PERIOD` e `TERMINATED`. A efetivação, o início/revogação de aviso prévio e a confirmação de desligamento são operações explícitas, auditadas e sujeitas à alçada direta/solicitação de aprovação. O fim previsto do aviso não desliga automaticamente o funcionário: a confirmação de desligamento é obrigatória. |
| D-34 | Aviso prévio é um registro próprio ligado a um único vínculo de trabalho, com início, término previsto, situação `OPEN`, `REVOKED` ou `COMPLETED`, motivo e estado laboral anterior. A revogação preserva o aviso histórico e restaura somente o estado laboral que existia antes dele; um aviso concluído não pode ser reaberto. |
| D-35 | Cursos, certificados e exames periódicos pertencem ao funcionário e ao tenant, têm tipo, título seguro, emissão, validade opcional, situação administrativa e anexos privados. Para exame, a aplicação guarda somente aptidão/controle administrativo e validade, nunca diagnóstico, laudo clínico detalhado ou prontuário. |
| D-36 | Documentos pessoais, comprovantes de endereço, certificados e evidências de exame são anexos privados de funcionário. Eles não possuem URL pública, não são incluídos em auditoria, logs, analytics, telemetria ou exportações comuns; a auditoria registra somente categoria, operação e metadados seguros. Exclusão operacional é lógica, e a eliminação definitiva segue o fluxo de privacidade e retenção. |
| D-37 | O vencimento de contrato comercial, experiência, aviso prévio, curso, certificado ou exame gera apenas alerta/notificação idempotente e auditável; nunca efetiva, desliga, bloqueia ou altera automaticamente um vínculo, contrato ou documento. Destinatários, antecedências e canais são configurados pela empresa dentro das permissões autorizadas. |
| D-38 | A importação de planilha de medição sincroniza somente itens de versão `DRAFT` em duas etapas: prévia validada e confirmação explícita do mesmo arquivo por hash. A chave de cada linha existente é exclusivamente o `itemId` estável incluído de forma oculta na exportação; `itemNumber`, título de serviço e atividade nunca identificam uma linha. A confirmação é atômica, recalcula totais no backend, inativa logicamente as linhas ausentes e registra a origem da planilha, autor, hash, versão anterior e alterações. Versões aceitas ou posteriores exigem revisão nova, sem alterar itens históricos. A exportação pode acrescentar linhas visuais mescladas `SERVIÇO: ...` para grupos consecutivos e cabeçalho com logo privada/fallback da empresa; essas linhas não são itens e o importador as ignora. As linhas de item preservam sempre as colunas estáveis `Serviço` e `Item ID`, e totais oficiais consideram somente itens ativos. |
| D-39 | Cortesia de assinatura é uma concessão auditável do proprietário da plataforma para uma `Company` e sua `Subscription`. Ela é distinta do trial e não altera preço, composição, histórico de cobrança ou planos contratados. Pode ser `TEMPORARY`, com início e fim inclusivos, ou `LIFETIME`, sem término e declarada explicitamente por tipo — ausência de data nunca significa vitalícia. Ambas exigem motivo, concedente, situação e versão otimista; concessões não podem se sobrepor. Durante a vigência, dão acesso integral e suspendem a criação de novas cobranças e avisos de inadimplência daquela assinatura. Cobranças abertas anteriores não são apagadas nem baixadas automaticamente: recebem tratamento explícito, auditável e definido pelo backend para não restringirem a empresa durante a cortesia e para não causarem bloqueio surpresa após expiração ou revogação. Revogação exige motivo, não apaga histórico e só vale prospectivamente. |
| D-40 | Uma `BillingAllocation` de serviço originada em medição preserva a `MeasurementContractComposition` do mesmo contrato e `ContractService`; a referência é opcional somente para a parcela de valor sem origem de medição. Essa origem percorre NF, título, recebimento e estorno. Uma composição não pode receber alocações ativas acima do líquido aceito. A projeção financeira reconhece origens aceitas vigentes, suas reversões/substituições e ajustes oficiais, sem somar snapshots corretivos duplicados; dinheiro é decimal canônico em texto. Origem ainda não composta não pode ser faturada. Recebimento parcial é atribuído às origens pelo backend, com rateio proporcional e resíduo determinístico na última alocação por UUID, considerando estornos. Duas medições no mesmo serviço contratual nunca recebem o valor integral da mesma baixa. Rascunhos, itens enviados/pendentes/rejeitados e propostas `202` não geram saldo contratual, NF, título ou recebimento. |
| D-41 | A medição mantém uma identidade contínua de serviço entre revisões. O corpo principal, a grade e os relatórios acumulados apresentam itens aceitos anteriores e itens do avanço atual agrupados nesse serviço, mesmo quando a nova revisão começa vazia. Aceitos permanecem congelados, com preços e origem preservados; somente itens da `DRAFT` atual podem ser editados, removidos, movidos ou reordenados por operação comum. A leitura acumulada é calculada no backend e não cria cópias oficiais de itens anteriores. |
| D-42 | O destino de cada serviço contínuo pode ser preparado antes do aceite: novo serviço contratual ou `contractServiceId` existente da mesma cadeia/empresa. A escolha é persistida, auditada, sujeita à alçada/aprovação e não muda valores comerciais. O aceite com contrato/destino, ou o vínculo posterior de origens já aceitas, coordena a aplicação autorizada somente do incremento novo. Aceite externo e aprovação interna da composição são fatos separados; permissões de medição não concedem permissões de serviço. Repetição/concorrência não duplica destino, serviço, composição ou solicitação para a mesma origem. O destino aplicado permanece nos próximos avanços; reassociar origem já aceita/composta exige operação formal. |
| D-43 | O relatório acumulado apresenta três valores oficiais: total líquido aceito, já recebido e falta receber. `remainingToReceive` inclui tanto o aceito ainda não faturado quanto o faturado ainda não recebido, considerados os ajustes, retenções, créditos e estornos oficiais aplicáveis; não equivale apenas a `billedOutstandingAmount`. A API publica o saldo, sua disponibilidade e instante de referência. Sem contrato ou sem autorização financeira, valores de recebimento/saldo ficam indisponíveis, nunca recebem zeros fictícios, e a exportação dos itens/totais permitidos continua disponível. |

## 4. Atores e acesso

### 4.1 Perfis de plataforma

| Perfil | Escopo | Pode | Não pode |
|---|---|---|---|
| Proprietário da plataforma | Global | Gerir empresas, planos, pacotes, assinaturas, cobranças, equipe e parâmetros da plataforma. | Ter seu acesso reduzido por outra empresa. |
| Administrador da plataforma | Global, delegável | Operações administrativas definidas pelo proprietário. | Excluir empresas ou alterar regras críticas sem permissão explícita. |
| Suporte da plataforma | Todas as empresas no MVP | Consultar, cadastrar/editar dados operacionais autorizados, gerar e enviar relatórios. | Aprovar alterações de clientes finais, excluir registros, alterar planos/assinaturas, mudar dados estruturais da empresa ou administrar seus papéis. |

Toda ação do suporte deve gravar o usuário de suporte, a empresa acessada, o módulo, a ação e, quando houver envio, o destinatário e o relatório enviado.

### 4.2 Perfis configuráveis pela empresa

A empresa não cria permissões novas em texto livre. Ela monta perfis a partir de um catálogo controlado de permissões por módulo e ação. Isso permite, por exemplo, que uma gerente de RH aprove apenas alterações de funcionários, enquanto outra pessoa cadastre contratos mas não aprove valores financeiros.

Permissões devem separar, no mínimo:

- consulta;
- criação, edição e cancelamento diretos;
- solicitação de criação/edição/cancelamento;
- aprovação;
- rejeição;
- exportação;
- envio de relatório;
- gestão de usuários;
- gestão de papéis.
- visualização de dados completos de pagamento de funcionário, separada da consulta comum de funcionários e concedida somente pelo catálogo controlado como `EMPLOYEES.VIEW_PAYMENT_DETAILS`.

Para cada módulo e operação, a API calcula a **alçada direta efetiva**: ela existe quando
o usuário possui a permissão direta correspondente ou quando reúne a permissão de
solicitação e a de aprovação para a mesma operação. A segunda combinação atende empresas
pequenas: a alteração é aplicada diretamente, sem criar solicitação pendente, mas com
`AuditEvent` completo. Aprovação isolada nunca concede direito de editar; solicitação
isolada continua criando pendência.

Módulos mínimos: empresa, usuários, clientes, contratos, serviços, descontos, medições, faturamento, NFs, contas a receber, contas a pagar, custos/despesas, relatórios e auditoria.

Perfis iniciais sugeridos:

| Perfil | Uso inicial |
|---|---|
| Empresa master | Controla a empresa, usuários e todos os módulos permitidos pelo plano. |
| Auditor master | Aprova solicitações em todos os módulos permitidos. |
| Auditor financeiro | Aprova somente faturamento, NFs, recebimentos, pagamentos, custos e despesas. |
| Operador de cadastro | Cria ou altera dados, enviando-os para aprovação quando não tiver alçada direta. |
| Relatórios | Consulta e exporta dados autorizados, sem alterar registros. |

No onboarding, o perfil `Empresa master` é materializado como o papel `Company Administrator` e atribuído ao proprietário inicial. Ele recebe todas as permissões ativas do catálogo controlado disponível naquele momento. Esse perfil permite que uma empresa com um único usuário trabalhe sem pendências inviáveis de aprovar; empresas maiores podem posteriormente criar e atribuir papéis mais restritos.

### 4.3 Fluxo de aprovação

1. Usuário tenta criar, alterar ou cancelar um registro.
2. A API verifica sua permissão para a ação e módulo.
3. A API calcula a alçada direta efetiva: permissão direta da ação, ou solicitação e aprovação reunidas para o mesmo módulo e operação. Se existir, grava a alteração e cria um evento de auditoria.
4. Caso não exista alçada direta efetiva, cria uma Solicitação de Alteração com a proposta, os valores anteriores, os valores novos e o solicitante.
5. O dado oficial continua inalterado e a proposta não entra em relatórios operacionais.
6. Um auditor com a permissão daquele módulo aprova ou rejeita. O solicitante nunca aprova sua própria solicitação; a combinação de solicitação e aprovação evita esse caso por executar diretamente e registrar auditoria.
7. Uma aprovação qualificada é suficiente. A aprovação aplica a proposta em transação atômica e cria eventos de auditoria.
8. Rejeições exigem motivo. A solicitação poderá ser ajustada e reenviada como nova revisão.

### 4.4 Retenção da auditoria

Para não comprometer responsabilidade financeira, o histórico não deve ser apagado em 10 ou 30 dias. A decisão de implementação é:

- retenção técnica mínima de **5 anos**, configurável para prazo maior;
- tela inicial mostrando os últimos **30 dias**, com filtros e paginação para períodos anteriores;
- registros de auditoria imutáveis, sem atualização ou exclusão pelos usuários;
- anexos podem ter política de retenção própria, sem apagar o evento de auditoria.

## 5. Regras de negócio

### 5.1 Obras, contratos, serviços e descontos

1. Um cliente final pode possuir várias obras.
2. Uma obra possui um único cliente final e pode ter vários contratos.
3. A obra guarda nome/identificação, status, datas, tipo de local de execução e endereço. O tipo de local inicial é: local do cliente final, sede/unidade da empresa prestadora ou outro local informado.
4. Um contrato pertence a uma única obra e pode ser criado sem serviços.
5. Um serviço pertence a um único contrato e possui status próprio.
6. Modelo de serviço reutilizado cria uma cópia editável; a edição da cópia não altera o modelo nem outros contratos.
7. Desconto de serviço, fixo ou percentual, altera o valor líquido daquele serviço e, por consequência, o subtotal dos serviços do contrato.
8. Desconto de contrato, fixo ou percentual, reduz somente o valor líquido do contrato. Ele não altera os valores individuais dos serviços.
9. O valor líquido do contrato é o limite total de faturamento e emissão de NF.
10. Quando houver desconto de contrato, o faturamento deve apresentar uma linha de ajuste contratual negativa, sem serviço associado. Assim, os serviços preservam seus próprios preços e o valor líquido faturado respeita o valor do contrato.
11. Uma revisão que reduza o valor líquido do contrato abaixo do faturamento líquido aprovado é bloqueada. Antes dela, o usuário deve estornar/cancelar valores ainda não efetivados ou registrar crédito/renegociação auditada.

Fórmulas:

| Cálculo | Regra |
|---|---|
| Valor bruto dos serviços | Soma dos valores brutos de cada serviço. |
| Desconto de serviço | Aplicado a cada serviço antes do subtotal do contrato. |
| Subtotal de serviços | Soma dos valores líquidos dos serviços. |
| Desconto de contrato | Aplicado sobre o subtotal de serviços; não é distribuído de volta aos serviços. |
| Valor líquido do contrato | Subtotal de serviços menos desconto do contrato. Nunca pode ser negativo. |
| Limite de faturamento | Soma líquida das alocações de faturamento aprovadas/ativas, contando cada valor uma única vez, não pode exceder o valor líquido do contrato. Linhas de desconto contratual são negativas e não pertencem a serviço. NF e conta a receber apenas referenciam a alocação; não consomem saldo novamente. |

O motor de validação deve bloquear concorrência: duas operações simultâneas não podem, juntas, ultrapassar o saldo do contrato. Será utilizada versão otimista no registro e validação transacional no banco.

### 5.2 Estados

Estados de negócio são independentes do fluxo de aprovação.

| Entidade | Estados |
|---|---|
| Contrato | Rascunho, Aberto, Ativo, Concluído, Finalizado, Suspenso, Cancelado. |
| Serviço | Rascunho, Ativo, Concluído, Cancelado. Situação financeira derivada: Não faturado, Parcialmente faturado, Totalmente faturado. |
| Medição | Rascunho, Gerada, Enviada, Aguardando aceite, Aceita, Finalizada, Rejeitada, Cancelada. |
| Revisão de medição | Rascunho, Enviada, Aguardando aceite, Aceita, Rejeitada. |
| Parcela/faturamento | Rascunho, Em aberto, Parcialmente recebida, Recebida, Em atraso, Renegociada, Cancelada. |
| Conta a pagar | Rascunho, Em aberto, Parcialmente paga, Paga, Em atraso, Renegociada, Cancelada. |
| NF | Rascunho, Registrada, Cancelada. NF não terá status Paga. |
| Solicitação de alteração | Rascunho, Em análise, Aprovada, Aplicada, Rejeitada, Cancelada. |

### 5.3 Medições

Uma medição possui cabeçalho, itens, desconto opcional, versão, total de peso, total financeiro, documentos gerados e vínculo opcional ao contrato. O detalhe da versão expõe também um resumo oficial por `serviceTitle`, calculado no backend somente com itens ativos: quantidade de itens, valor bruto e peso total quando aplicável. Desconto de cabeçalho não é rateado por serviço sem regra explícita de domínio; o único líquido oficial permanece no total da versão.

Para a operação contínua, o mesmo documento é alimentado por vários meses: uma revisão registra o novo avanço, enquanto a consulta e os relatórios acumulados continuam exibindo todos os serviços e itens aceitos vigentes no corpo principal. Uma revisão vazia não oculta a execução anterior. O histórico por versão continua disponível com seu conteúdo original e não substitui essa visão acumulada. As regras detalhadas estão na seção 5.3.1; a implementação atual é acompanhada por F04-28 a F04-38.

Tipos de cobrança iniciais:

| Tipo | Dados informados | Fórmula |
|---|---|---|
| Metro quadrado | Área em m² e preço por m² | área × preço por m² |
| Metro linear | Metros lineares e preço por metro | metros × preço por metro |
| Quilograma por m² | Quilogramas por m², área e preço por kg | kg por m² × área = kg total; kg total × preço por kg |
| Quilograma por metro linear | Quilogramas por metro, metros e preço por kg | kg por metro × metros = kg total; kg total × preço por kg |
| Unidade | Quantidade de unidades e preço por unidade | quantidade de unidades × preço por unidade |

Cada item deve guardar o título legível do serviço ao qual pertence, atividade, como desmontagem, montagem, fabricação ou pintura, descrição, data da atividade, tipo de cobrança, quantidades, preço por unidade aplicável, fórmula aplicada, peso total e valor total calculado. Para `UNIT`, `unitQuantity` é obrigatório, positivo e não pode coexistir com área, metragem ou parâmetros de peso; o peso total é nulo. Para m², metro linear, kg/m² e kg/metro linear, `unitQuantity` é opcional e registra somente peças/unidades executadas: deve ser decimal positiva de até quatro casas, mas não entra na fórmula, no peso, no valor, nas prévias nem nos totais oficiais. A versão soma os pesos calculáveis dos itens para obter o peso total da medição e soma os valores dos itens para obter o valor bruto; itens cobrados somente por m², metro linear ou unidade não inventam peso e contribuem apenas para o total financeiro. O desconto inicial da medição é aplicado no cabeçalho da versão e registrado como ajuste separado, preservando os valores brutos de seus itens.

Em versão `DRAFT`, a inserção de uma atividade no meio da sequência é uma operação única e transacional: a empresa informa o item ativo após o qual a nova linha deve entrar, e o backend normaliza/desloca a sequência de itens ativos. Números de itens inativos permanecem históricos e não participam dessa sequência. A inserção respeita alçada direta ou gera `ChangeRequest`; enquanto pendente, não muda números, totais nem qualquer dado oficial. A operação grava auditoria da ordem anterior e posterior e atualiza a versão com concorrência otimista. A reordenação completa usa operação própria, também transacional: recebe todos os itens ativos exatamente uma vez na ordem final e pode alterar somente seu agrupamento por `serviceTitle`; o backend atribui `itemNumber` sequencial, preserva itens inativos e não aceita uma série de atualizações individuais do frontend.

O frontend pode calcular `previewTotalWeightKg` e `previewTotalAmount` em tempo real para dar resposta imediata ao usuário, mas esses campos são apenas conferência. Para `UNIT`, a prévia de peso pode ser omitida ou ser `0.0000`; a prévia de valor deve corresponder a `unitQuantity × unitPrice`. O backend sempre recalcula com `BigDecimal`, aplica a fórmula oficial e rejeita prévias divergentes; somente `totalWeightKg`, `totalAmount`, `grossAmount`, `discountAmount` e `netAmount` devolvidos pela API são oficiais. A prévia nunca substitui validação, auditoria ou persistência do cálculo servidor.

A medição pode nascer sem obra e sem contrato. Isso permite registrar execução avulsa ou preparar a futura evolução orçamento → obra → contrato sem inventar vínculos antecipadamente. Se `contractId` for informado na criação, `workId` também é obrigatório e o contrato deve pertencer à mesma obra e empresa. Orçamento ainda não integra o MVP e não deve reutilizar o status de aceite financeiro da medição.

Contrato possui modo de formação comercial. Em `FIXED`, seus serviços e valores negociados permanecem como base e uma medição aceita pode gerar apenas um acréscimo contratual rastreável. Em `MEASUREMENT_BASED`, o valor comercial é formado progressivamente por medições aceitas. Cada versão aceita materializa grupos estáveis de serviço (`MeasurementService`); o título do serviço é apenas texto de apresentação e nunca é a chave de composição.

Uma `MeasurementContractComposition` é imutável e idempotente por grupo de serviço da versão. Ela registra medição, versão, grupo, itens de origem, ContractService de destino, usuário, justificativa, valor bruto, peso e valor líquido. A origem é `CONTRACT_ADDITION` no contrato fechado ou `MEASUREMENT_PRICING` no contrato por medição. O valor-base do ContractService não é regravado: sua posição efetiva é valor-base líquido mais composições aceitas. O rateio do desconto de cabeçalho da medição é exclusivamente do backend, proporcional ao valor bruto e com resíduo de arredondamento destinado ao último grupo em ordem estável.

Enquanto não houver obra, ficam disponíveis o cadastro e a leitura da medição, seus itens e desconto, documentos/evidências, exportações PDF/XLSX e o fluxo de envio/registro do aceite externo. A medição sem obra ou sem contrato não pode ser finalizada, convertida em `ContractService`, faturada nem produzir qualquer outro efeito financeiro. O vínculo é feito por operação própria, com tenant derivado da sessão, auditoria e alçada direta ou `ChangeRequest`. Uma obra já vinculada não pode ser trocada; depois de vinculada à obra, o contrato pode ser acrescentado se pertencer a ela, mas não pode ser substituído silenciosamente.

Fluxo:

1. A empresa cria uma medição com título obrigatório; obra e contrato são opcionais. Contrato informado exige obra compatível.
2. O sistema gera planilha em Excel e/ou PDF.
3. A empresa envia o documento ao cliente final por meio externo ao sistema.
4. Ao receber o aceite externo, a empresa registra o aceite e, se disponível, anexa o documento/evidência.
5. Itens aceitos ficam congelados. Novos itens ou aumentos criam uma nova revisão que exige novo aceite externo. Um item de versão `DRAFT` pode ser corrigido ou removido logicamente, sempre com auditoria e alçada/aprovação. A correção, substituição ou anulação de item `ACCEPTED` cria uma revisão corretiva `DRAFT`: a versão aceita e seu item permanecem imutáveis, as linhas copiadas registram a origem e a anulação fica inativa apenas na nova versão. Os totais oficiais da revisão são recalculados no backend somente a partir das linhas ativas.
6. Antes da finalização/conversão, a medição deve ser vinculada explicitamente a uma obra e a um contrato existente dessa obra, ou o fluxo futuro deve criar o contrato nessa obra e então registrar o vínculo.
7. Cada grupo estável de uma versão aceita pode compor, no máximo uma vez, um serviço novo ou um serviço existente do contrato, preservando itens e origem. A composição é idempotente; `serviceTitle` não é usado como chave. Para dividir a composição, o usuário deve separar os grupos antes do aceite.
8. A medição exibe valor faturado e saldo calculados a partir dos serviços gerados e de seus faturamentos.
9. A medição finalizada não aceita novos itens ou revisões.
10. Uma medição sem contrato pode ser cancelada logicamente em qualquer etapa, inclusive quando já estiver vinculada apenas a uma obra. Se houver contrato, o cancelamento é bloqueado para preservar a cadeia contratual; qualquer desfazimento futuro deverá ser um fluxo explícito de estorno/renegociação, não exclusão.
11. Uma correção posterior não pode apagar ou alterar a composição original. A redução financeira precisa gerar reversão/substituição auditável; quando houver faturamento aprovado, ela é bloqueada até existir crédito, estorno ou renegociação rastreável.

### 5.3.1 Medição contínua, destino e visão acumulada

A identidade do serviço contínuo pertence à medição, e não a uma única versão. O grupo aceito por versão (`MeasurementService`) continua identificando uma origem congelada daquele avanço; ele não substitui a identidade necessária para continuar o mesmo serviço em meses seguintes. Títulos podem repetir ou mudar e nunca autorizam associação financeira automática. Uma migração de dados legados deve preservar os IDs existentes e tratar associações ambíguas explicitamente, sem inferir destino contratual por título.

Sobras comprovadas de backfill sem item, reconhecimento, grupo aceito, destino, auditoria de uso ou solicitação relacionada podem receber retirada operacional por migration nova, em registro imutável `measurement_continuous_service_retirement` com origem e auditoria `SYSTEM`. A V83 identifica o lote transacional da V78 por reconhecimentos e cadeias explícitas de cópia anteriores ao lote; título nunca é critério de união ou descarte. A identidade original permanece no banco e consultas/relatórios históricos por versão não mudam. Serviços vazios intencionais e registros com origem ambígua são preservados. A consulta/lista/exportação acumulada compartilha essa filtragem e apresenta atividades e subtotais por identidade contínua, mantendo aceitos e variações em revisão separados.

Durante o lançamento, cada serviço pode preparar um destino: criar novo serviço no contrato ou complementar um `contractServiceId` ativo. O destino deve sobreviver à recarga e continuar nos próximos avanços. Contrato e serviço são validados dentro do tenant e da mesma obra. Em medição avulsa, o destino é escolhido após cadastrar e vincular o contrato pelo fluxo próprio; o sistema não cria um contrato automaticamente. Destino ainda em aprovação não é o destino oficial e deve ser consultável por seu `changeRequestId`.

Criar/editar itens, preparar destino, enviar medição, registrar rejeição ou aguardar aprovação não aumenta valor contratual. Quando o avanço é aceito e existe contrato/destino oficial, o backend coordena a composição, aplicando apenas os novos itens aceitos: `CONTRACT_ADDITION` em `FIXED` ou `MEASUREMENT_PRICING` em `MEASUREMENT_BASED`. O vínculo posterior coordena o mesmo fluxo para origens anteriormente aceitas. Preservam-se base negociada, composições anteriores, descontos, faturamentos e recebimentos. O frontend não precisa criar serviços e atualizar preços numa sequência própria.

O caso de uso verifica separadamente alçada de medição e de serviço. Se a operação proposta responder `202`, os dados que ela pretende aplicar continuam oficiais como antes. Um aceite externo já registrado pode coexistir com composição aguardando autorização interna; a resposta deve distinguir esse aceite da composição ainda pendente. Não forjar permissão, autoaprovação nem ator humano para automação. A mesma origem não gera solicitações pendentes duplicadas por repetição do comando. A aprovação revalida versão, destino, tenant e concorrência antes de aplicar.

A visão acumulada deve devolver cabeçalho, revisão operacional, identidade dos serviços, destino e situação da composição, itens com versão/origem/status/editabilidade, ordem de leitura e totais oficiais separados de aceito, pendente e acumulado. A ordem visual de leitura é determinada pelo servidor, sem alterar `itemNumber` histórico. Serviços e itens aceitos aparecem junto dos novos itens; a indicação visual é “Aceito” ou “Em revisão / aguardando aceite”. Somente a `DRAFT` atual admite mutações comuns. Um preço novo vale para a nova atividade e não reavalia os itens anteriores.

Correções formais preservam o original e sua composição. O acumulado resolve cadeias de `sourceItemId`, substituições e anulações: uma cópia corretiva pendente não duplica o aceito; uma correção oficialmente aplicada substitui/reverte a origem correspondente uma única vez. Rejeições não entram no aceito. Reduções que atinjam faturamento/recebimento exigem estorno, crédito ou renegociação antes do efeito financeiro. É vedado somar todos os snapshots aceitos indiscriminadamente.

Importação, inserção e reordenação alcançam somente itens editáveis do avanço atual. A ausência de uma linha aceita em XLSX de rascunho nunca inativa essa origem. Uma exportação acumulada deve identificar claramente aceitos somente leitura e o avanço atual; até existir contrato de importação próprio, ela não substitui o XLSX da versão usado na sincronização atual.

### 5.3.2 Projeção e resumo financeiro da medição

Os cinco campos detalhados permanecem: `acceptedAmount` (líquido aceito vigente), `billedAmount` (faturamento vigente das origens), `receivedAmount` (recebimentos confirmados atribuídos, líquidos dos estornos), `acceptedAvailableToBill` (aceito já composto e efetivamente disponível para faturar) e `billedOutstandingAmount` (saldo devido de parcelas faturadas). Grupo aceito sem composição pode compor o total documental aceito, mas não representa disponibilidade para faturamento; a projeção deve mostrar sua situação sem inventar alocação.

O PDF acumulado usa somente “Valor total da medição (aceito)”, “Já recebido” e “Falta receber”. Publicar também `remainingToReceive`, disponibilidade financeira e `asOf` UTC, preservando os cinco campos detalhados. Sem ajustes, o cenário aceito `10000.00`, faturado `7000.00` e recebido `4000.00` produz `remainingToReceive = "6000.00"`; o saldo das parcelas faturadas é `"3000.00"`. Adicionar `3000.00` em rascunho não altera os três valores. Após novo aceite/aplicação, passam a `13000.00`, `4000.00` e `9000.00`, e somente `3000.00` entra no contrato como incremento.

Retenções, créditos, renegociações e estornos são resolvidos por origem e pela regra oficial no backend. Caso a base de total aceito difira do saldo efetivamente devido, retornar explicação/ajustes seguros e explícitos no contrato; não exibir uma subtração simplificada como regra universal. O desconto contratual continua ajuste separado, sem reprecificar ou ratear o preço do `ContractService`. O cliente não soma versões, não subtrai recebimentos e não combina saldos para fabricar o terceiro valor.

Consulta/relatório devem usar uma projeção coerente com referência de revisão e instante financeiro. Itens exigem `MEASUREMENTS.READ`, exportação exige `MEASUREMENTS.EXPORT` e dados financeiros exigem `BILLING.READ` e `RECEIVABLES.READ`. Sem contrato ou sem essas permissões, o relatório mostra indisponibilidade financeira e mantém os itens/totais documentais autorizados. Isso não concede acesso indireto a finanças por permissão de exportação.

### 5.4 Faturamento, NF e recebimentos

O controle de faturamento é exclusivamente contratual. Uma obra consolida a receita dos seus contratos, mas não possui parcela, saldo faturável ou NF próprios.

1. Faturamento é registrado por parcela e alocado a um ou mais serviços do mesmo contrato. Uma alocação pode ser de serviço, positiva, ou de desconto contratual, negativa e sem serviço associado. A soma líquida das alocações aprovadas é a única fonte que consome o saldo faturável do contrato.
2. Um serviço pode ser faturado parcialmente em várias parcelas e NFs.
3. NF pertence a um único contrato e possui itens/alocações para seus serviços. Ela deve apontar para alocações de faturamento já existentes, ou criá-las na mesma transação, para impedir dupla contagem.
4. Registrar NF não baixa a conta a receber; recebimento é uma operação separada.
5. A criação ou aprovação de uma parcela gera uma conta a receber correspondente.
6. Cada conta a receber representa um título/parcela individual. Parcelamentos são títulos vinculados por um grupo financeiro comum.
7. Baixas de contas a receber podem ser parciais e devem registrar data, valor, método e observação.
8. Uma NF e seus itens não podem ultrapassar o saldo ainda disponível nas alocações de faturamento do contrato.
9. Cancelar NF, parcela ou recebimento nunca apaga o histórico; cria estorno/cancelamento auditado.
10. A receita de uma obra é uma consulta consolidada: soma os faturamentos, NFs e recebimentos de seus contratos conforme o critério do relatório. Ela não pode liberar, bloquear ou alterar o saldo de um contrato.

Transições financeiras obrigatórias:

| Ação | Efeito obrigatório |
|---|---|
| Cancelar parcela sem NF/recebimento | Cancela a alocação e devolve seu valor líquido ao saldo faturável do contrato. |
| Cancelar NF registrada | Exige cancelar/estornar seus itens, impostos e vínculos antes de liberar eventual saldo de faturamento. |
| Estornar recebimento | Cria baixa reversa, reabre o saldo da conta a receber e atualiza o fluxo de caixa. |
| Cancelar pagamento | Cria baixa reversa, reabre o saldo da conta a pagar e atualiza o fluxo de caixa. |
| Renegociar título | Preserva o título anterior como renegociado e gera novos títulos vinculados à origem. |

### 5.5 Contas a pagar, custos, despesas e lucro

1. Contas a pagar são lançadas manualmente no MVP e podem ter pagamentos parciais. Cada registro Payable representa uma parcela/título individual; contas parceladas são vários títulos ligados por um grupo financeiro comum.
2. Todo gasto/conta a pagar possui escopo financeiro obrigatório:
   - **Obra:** exige work_id; pode ter contract_id opcional, desde que o contrato pertença à mesma obra;
   - **Empresa:** não possui work_id nem contract_id.
3. Um EPI comprado para uma obra é registrado no escopo Obra, mesmo quando não for possível identificar qual contrato utilizou o item. O campo contract_id permanece vazio.
4. Gastos de escopo Empresa, como impostos gerais da empresa, despesas administrativas ou custos sem relação com uma obra, aparecem no fluxo de caixa e nos relatórios de gastos da empresa, mas nunca no lucro de uma obra.
5. A categoria classifica o gasto como custo de obra, despesa de obra, despesa da empresa ou imposto. A categoria não substitui o escopo: um imposto pode ser da obra, quando vem de uma NF de contrato, ou da empresa, quando não estiver associado a obra.
6. Ao registrar uma NF, o usuário informa impostos e retenções. Cada registro tributário herda a obra por meio do contrato e possui tratamento: a pagar pela empresa, retido pelo cliente ou somente informativo. O sistema cria o registro fiscal em todos os casos; somente o tratamento a pagar gera automaticamente uma conta a pagar de escopo Obra. O tratamento retido ajusta o valor líquido a receber, sem duplicar uma conta a pagar.
7. O fluxo de caixa é calculado, não cadastrado manualmente:
   - previsto: todas as contas abertas, de obra e de empresa, por vencimento;
   - realizado: baixas de recebimentos e pagamentos.
8. Receita da obra é sempre derivada de seus contratos. O sistema deve expor separadamente receita faturada, receita emitida em NF e receita recebida, sem misturar esses valores.
9. Resultado previsto da obra: faturamento líquido aprovado dos contratos da obra menos impostos, custos e despesas de escopo Obra.
10. Resultado de caixa da obra: recebimentos dos contratos da obra menos pagamentos de escopo Obra, considerando o período filtrado.
11. Gastos de escopo Empresa ficam fora dos dois resultados de obra e são exibidos apenas no resultado/fluxo de caixa geral da empresa.

### 5.6 Assinaturas e inadimplência da plataforma

1. A empresa escolhe pelo menos um plano para criar e utilizar a conta.
2. O onboarding é público e não exige aceite manual da plataforma: cria o proprietário pendente, o papel administrativo inicial, as seleções de plano e um token de ativação enviado por e-mail. A ativação define a senha do proprietário e libera seu login; não é aprovação da Company.
3. Planos são cumulativos. A precificação usa pacotes de combinação configurados pelo proprietário, para que combinações como Plano 1 + Plano 2 tenham preço promocional sem alterar preços individuais.
4. Cada contratação mantém foto do preço, desconto, composição e vigência para preservar histórico.
5. O período de teste é de 30 dias, iniciado quando o proprietário inicial ativa a conta pelo token. Ao fim do trial, a assinatura passa para aguardando pagamento; a cobrança e a liberação por pagamento são tratadas pelo fluxo de gateway posterior.
6. A integração é encapsulada pela porta `PaymentGateway`, com criação neutra de pagador, solicitação de cobrança contendo chave de idempotência e resultado normalizado. O adapter inicial usa HTTP contra o sandbox do Asaas, isolado na infraestrutura e habilitado somente por configuração; nenhum contrato do domínio depende de DTO ou SDK do provedor. O Asaas suporta cobranças por boleto, Pix e cartão de crédito, além de webhooks de cobrança. O cartão pode ser cobrado automaticamente; Pix e boleto têm geração recorrente de cobrança, mas o pagador realiza o pagamento. Veja a documentação oficial do Asaas: https://docs.asaas.com/docs/faq-assinaturas
7. Webhooks chegam como payload bruto e cabeçalhos; o adapter verifica a autenticidade antes de traduzi-los para eventos neutros. Somente o evento neutro já autenticado é persistido em `PaymentGatewayEvent`, que registra origem, identificadores externos, tipo, ocorrência, recebimento, atributos e estado de processamento. A idempotência usa código neutro do provedor + identificador externo do evento, combinando bloqueio transacional e constraint única; a mesma chave com conteúdo diferente é rejeitada. O endpoint responde rapidamente após a persistência. Eventos `RECEIVED` e `FAILED` são reprocessados em lote respeitando intervalo de retry; cobranças abertas são reconciliadas periodicamente por consulta pontual ao gateway. Atualizações concorrentes usam lock e versão otimista, eventos antigos não regridem estado mais recente, e transições automáticas geram auditoria com ator `SYSTEM`.
8. A regra de inadimplência é:
   - no vencimento não pago (D0): registra uma intenção idempotente de aviso para a cobrança, sem reduzir o acesso naquele dia;
   - após o vencimento (D+1): acesso somente para consulta;
   - a partir de 5 dias (D+5): status de inadimplente e manutenção de consulta;
   - a partir de 10 dias (D+10): bloqueio total;
   - a decisão considera a cobrança aberta mais antiga (`PENDING` ou `OVERDUE`), registra `CompanySubscriptionAccess` e `PlatformChargeNotice` únicos por cobrança/tipo e ignora cobranças confirmadas ou canceladas;
   - o job diário processa cada Company em transação própria, bloqueia suas cobranças abertas antes de calcular o estado e grava a auditoria automática com ator `SYSTEM`;
   - a restrição é aplicada no backend às rotas da Company: `GET`, `HEAD` e `OPTIONS` são permitidos em consulta; mutações são negadas; no bloqueio total todas as rotas da Company são negadas. Rotas de autenticação, onboarding, plataforma e suporte não recebem essa restrição;
   - as intenções de aviso são entregues por e-mail via `NotificationPort`, com adaptador SMTP isolado da assinatura. A entrega persiste estado, tentativas e auditoria `SYSTEM` desde o claim; falhas só são elegíveis após o intervalo de retry configurado, e aviso cuja cobrança já foi confirmada ou cancelada é cancelado sem envio;
   - pagamento confirmado: restaura o acesso conforme a assinatura ativa;
   - desbloqueio de confiança: até dois por cobrança vencida ao longo do histórico, concedidos exclusivamente pelo proprietário da plataforma. Cada `TrustUnlock` registra autor, motivo, início imediato e expiração futura; enquanto ativo, desconsidera somente a cobrança vinculada. Outra cobrança vencida não desbloqueada continua restringindo a Company, e o filtro reavalia os desbloqueios ativos em cada requisição para aplicar a expiração sem aguardar o job diário.
9. A plataforma mantém sua própria tabela de cobranças e eventos; o gateway não será a única fonte de verdade. `PlatformCharge` registra a Company derivada da assinatura, assinatura, código neutro do provedor, chave idempotente, identificadores externos, meio, valor, vencimento e estado normalizado. Código do provedor + chave idempotente também usa bloqueio transacional e constraint única, evitando nova chamada externa em repetição concorrente. A foreign key composta entre cobrança, assinatura e Company impede associação cruzada entre tenants.
10. O proprietário da plataforma pode conceder cortesia de assinatura por período determinado, como seis meses ou um ano, usando datas explícitas, ou como tipo explícito `LIFETIME`, sem data de término. A cortesia não é novo plano nem extensão do trial: preserva o snapshot comercial e é a única autoridade para suspender cobrança/avisos e liberar acesso integral enquanto estiver ativa. Concessão, revogação e expiração são auditadas. Uma cortesia ativa é avaliada em toda rota protegida da empresa antes do dunning; após expiração ou revogação, o acesso volta à política normal com uma próxima cobrança/due date definida de modo explícito e auditável, sem ressuscitar ou apagar silenciosamente uma cobrança anterior.

## 6. Arquitetura proposta

### 6.1 Visão geral

Arquitetura inicial: monólito modular, API REST, Java 21, Spring Boot, Spring Security, PostgreSQL, Flyway e Docker.

~~~mermaid
flowchart LR
    Frontend["Frontend futuro"] --> API["API REST Spring"]
    Support["Portal/admin da plataforma futuro"] --> API
    API --> Auth["Identidade e autorização"]
    API --> Domains["Módulos de domínio"]
    Domains --> DB["PostgreSQL"]
    API --> Files["Armazenamento de anexos"]
    API --> Payment["Gateway de pagamento"]
    API --> Mail["Provedor de e-mail"]
    Payment --> Webhook["Webhook seguro"]
    Webhook --> API
~~~

O frontend não fala diretamente com o banco, com o gateway de pagamento ou com o armazenamento de arquivos.

### 6.2 Módulos

| Módulo | Responsabilidade |
|---|---|
| platform | Empresas assinantes, planos, pacotes, assinaturas, cobranças e usuários globais. |
| identity | Login, senha, tokens, recuperação de acesso e usuários. |
| employees | Perfil de funcionário, vínculos de trabalho, aviso prévio, conformidade, documentos privados e referências de pagamento. |
| tenancy | Contexto da empresa, isolamento e validações de acesso. |
| access | Papéis, permissões, alçadas e autorização por módulo/ação. |
| governance | Solicitações de alteração, aprovação, auditoria e histórico. |
| customers | Clientes finais das empresas. |
| works | Obras, vínculo com cliente final, local de execução e status. |
| contracts | Contratos de uma obra, modelos de serviço, serviços e descontos. |
| measurements | Medições, revisões, cálculos e geração de planilhas. |
| billing | Faturamento, parcelas, alocações, NFs, impostos e contas a receber. |
| payables | Fornecedores, contas a pagar, custos, despesas e pagamentos. |
| reporting | Consultas analíticas, exportação e envio de relatórios. |
| documents | Anexos, PDF, Excel, DOCX e armazenamento. |
| notifications | E-mails de cobrança, aprovação, bloqueio, relatórios e eventos relevantes. |

### 6.3 Estratégia multiempresa

- Toda entidade da operação da empresa terá coluna company_id.
- O company_id vem do token autenticado, jamais de um campo livre aceito no corpo da requisição.
- Usuários da empresa devem possuir vínculo obrigatório com uma única empresa.
- Usuários globais de plataforma selecionam uma empresa-alvo em rota/contexto próprio, após autorização explícita do seu papel global.
- Endpoints de plataforma e endpoints de empresa ficam separados.
- Repositórios e serviços sempre filtram por company_id.
- Restrições únicas devem incluir company_id quando a unicidade for apenas interna à empresa.
- Testes automatizados devem provar que uma empresa não consegue consultar, modificar ou inferir dados de outra.
- PostgreSQL Row Level Security (RLS) será implantado como defesa adicional para tabelas de tenant. Cada transação autenticada deve estabelecer o tenant de forma segura no banco, e a conta usada pela aplicação não pode contornar as políticas. RLS nunca substitui a autorização e os filtros da aplicação.

### 6.4 Autenticação e segurança

- Spring Security com tokens de acesso de curta duração e refresh tokens revogáveis. O refresh token é entregue somente em cookie host-only `HttpOnly`, `Secure`, `SameSite=Strict`, restrito a `/api/v1/auth`, rotacionado a cada renovação e nunca retornado no JSON. “Lembrar acesso” persiste o cookie até o TTL do token; sem a opção, o cookie é de sessão. A preferência acompanha o token no backend e o logout sempre expira o cookie.
- Senhas com hash forte, política de senha e recuperação por e-mail.
- Verificação de e-mail na ativação de usuário.
- MFA entra como controle obrigatório para proprietário da plataforma e opcionalmente exigível pela empresa para administradores. O primeiro fator será TOTP, com códigos de recuperação de uso único, armazenamento cifrado de segredo, limitação de tentativas e fluxo auditado de recuperação.
- Proteção contra força bruta usa limitação de requisições distribuída nas rotas públicas de autenticação, ativação e recuperação, além de contador de falhas e bloqueio temporário progressivo por conta e origem. A resposta não pode revelar se e-mail, conta ou MFA existem.
- Validação de entrada, autorização no serviço e não apenas no controlador.
- CORS mantém origens explicitamente autorizadas e não usa wildcard com credenciais. Downloads expõem `Content-Disposition` e `X-Correlation-Id` ao navegador dessas origens, permitindo preservar o filename oficial sem liberar o conteúdo sem autenticação/tenant/permissão. A disponibilidade do ajuste na instância depende de publicação autorizada.
- Logs sem senha, token, dados completos de cartão ou conteúdo fiscal sensível.
- Segredos em variáveis de ambiente no desenvolvimento e em serviço de segredos no ambiente publicado, nunca em `application.properties` versionado. Chaves de assinatura, criptografia de dados, SMTP, banco e provedores externos são distintas, rotacionáveis e não podem usar fallback entre si.
- Endereço residencial, documento, PIS, remuneração, conta bancária e chave Pix de funcionário são dados pessoais protegidos. Documento, PIS, endereço, remuneração e dados de pagamento ficam cifrados em repouso com chaves separadas por finalidade; pesquisas exatas permitidas usam índice cego/HMAC com chave independente, nunca ciphertext determinístico ou dado em texto claro. Consultas operacionais retornam somente o mínimo necessário; conta, dígito de conta e Pix permanecem cifrados em repouso e são devolvidos apenas de forma mascarada. A exceção é uma consulta pontual de referência de pagamento manual, sempre por permissão granular, ação explícita, tenant da sessão e auditoria imutável da visualização; a resposta não pode ser exportada, incluída em relatórios, logs, telemetria ou cache persistente.
- O `DELETE` operacional apenas desativa o dado e preserva auditoria mínima, sem registrar o valor pessoal no evento. A eliminação/anonimização definitiva exige fluxo específico de privacidade, decisão auditada, prazo de retenção configurável e verificação prévia de obrigação legal/regulatória ou exercício regular de direitos.

## 7. Modelo de dados conceitual

### 7.1 Relações principais

~~~mermaid
erDiagram
    COMPANY ||--o{ COMPANY_USER : possui
    COMPANY ||--o{ FINAL_CUSTOMER : cadastra
    COMPANY ||--o{ EMPLOYEE : cadastra
    EMPLOYEE ||--o{ EMPLOYEE_EMPLOYMENT : possui_historico
    EMPLOYEE_EMPLOYMENT ||--o{ EMPLOYEE_NOTICE_PERIOD : possui
    EMPLOYEE ||--o{ EMPLOYEE_COMPLIANCE_RECORD : comprova
    EMPLOYEE ||--o{ EMPLOYEE_DOCUMENT : anexa
    FINAL_CUSTOMER ||--o{ WORK : possui
    WORK ||--o{ CONTRACT : possui
    CONTRACT ||--o{ CONTRACT_SERVICE : contem
    COMPANY ||--o{ SERVICE_TEMPLATE : possui
    SERVICE_TEMPLATE ||--o{ CONTRACT_SERVICE : origina
    WORK o|--o{ MEASUREMENT : vincula_opcionalmente
    MEASUREMENT ||--o{ MEASUREMENT_VERSION : possui
    MEASUREMENT ||--o{ MEASUREMENT_CONTINUOUS_SERVICE : mantem
    MEASUREMENT_CONTINUOUS_SERVICE ||--o{ MEASUREMENT_ITEM : agrupa_entre_avancos
    MEASUREMENT_CONTINUOUS_SERVICE ||--o{ MEASUREMENT_SERVICE_DESTINATION : preserva_historico
    CONTRACT_SERVICE o|--o{ MEASUREMENT_SERVICE_DESTINATION : destino_preparado
    MEASUREMENT_VERSION ||--o{ MEASUREMENT_ITEM : contem
    MEASUREMENT_VERSION ||--o{ MEASUREMENT_SERVICE : congela_grupo
    MEASUREMENT_CONTINUOUS_SERVICE ||--o{ MEASUREMENT_SERVICE : identifica_continuidade
    MEASUREMENT_SERVICE ||--o| MEASUREMENT_CONTRACT_COMPOSITION : aplica_uma_vez
    CONTRACT_SERVICE ||--o{ MEASUREMENT_CONTRACT_COMPOSITION : recebe_incrementos
    MEASUREMENT_CONTRACT_COMPOSITION ||--o{ MEASUREMENT_COMPOSITION_ADJUSTMENT : reverte_ou_substitui
    CONTRACT ||--o{ BILLING_INSTALLMENT : fatura
    BILLING_INSTALLMENT ||--o{ BILLING_ALLOCATION : distribui
    CONTRACT_SERVICE ||--o{ BILLING_ALLOCATION : recebe
    MEASUREMENT_CONTRACT_COMPOSITION o|--o{ BILLING_ALLOCATION : preserva_origem
    CONTRACT ||--o{ INVOICE : possui
    INVOICE ||--o{ INVOICE_ITEM : contem
    CONTRACT_SERVICE ||--o{ INVOICE_ITEM : referencia
    BILLING_ALLOCATION ||--o{ INVOICE_ITEM : documenta
    BILLING_INSTALLMENT ||--|| RECEIVABLE : gera
    RECEIVABLE ||--o{ RECEIVABLE_SETTLEMENT : recebe
    COMPANY ||--o{ PAYABLE : registra
    WORK o|--o{ PAYABLE : vincula
    CONTRACT o|--o{ PAYABLE : detalha_opcionalmente
    PAYABLE ||--o{ PAYABLE_SETTLEMENT : recebe_pagamento
    INVOICE ||--o{ TAX_OBLIGATION : gera
    TAX_OBLIGATION ||--|| PAYABLE : cria
    COMPANY ||--o{ SUBSCRIPTION : contrata
    PLAN ||--o{ SUBSCRIPTION : compoe
    SUBSCRIPTION ||--o{ PLATFORM_CHARGE : gera
    COMPANY_USER ||--o{ AUDIT_EVENT : executa
    CHANGE_REQUEST ||--o{ AUDIT_EVENT : gera
~~~

### 7.2 Entidades por contexto

| Contexto | Entidades principais |
|---|---|
| Plataforma | Company, Plan, PlanBundle, PlanBundleItem, Subscription, SubscriptionItem, PlatformCharge, PlatformChargeNotice, CompanySubscriptionAccess, PaymentGatewayEvent, TrustUnlock, SubscriptionCourtesyGrant. |
| Identidade | User, CompanyUser, PlatformUser, RefreshToken, PasswordResetToken, Role, Permission, RolePermission, UserRole. |
| Pessoas e conformidade | Employee, EmployeeEmployment, EmployeeNoticePeriod, EmployeeComplianceRecord, EmployeeDocument, EmployeePaymentAccount, EmployeeAddress. |
| Governança | ChangeRequest, ChangeRequestDecision, AuditEvent, AuditPayload, AccessLog. |
| Comercial | FinalCustomer (tabela final_customer), Work (tabela obra), WorkAddress, Contract, ServiceTemplate, ContractService, Discount, ContractRevision. Contract informa `pricing_mode`; ContractService preserva valor-base e posição formada por composições. |
| Medição | Measurement (documento contínuo, vínculo inicial opcional), MeasurementVersion (avanço), MeasurementItem, MeasurementService (grupo aceito por versão), MeasurementContinuousService (identidade entre avanços), MeasurementServiceDestination (destino/histórico), MeasurementContractComposition, MeasurementCompositionAdjustment (reversão/substituição), MeasurementDocument, MeasurementContractLink. As entidades de continuidade/destino/ajuste são requisitos conceituais; sua entrega consta no backlog. |
| Receita | BillingInstallment, BillingAllocation, Invoice, InvoiceItem, Receivable, ReceivableSettlement. |
| Despesa | Supplier, CostCenter, Payable, PayableSettlement, TaxObligation, FinancialCategory. |
| Documentos | Attachment, GeneratedDocument, ReportExport, ReportDelivery. |

### 7.3 Campos financeiros e integridade

- Valores monetários: BigDecimal e código ISO `BRL`, `USD` ou `EUR`, sempre duas casas; quantidades técnicas com até quatro casas. `Contract.currency` e `Measurement.currency` são persistidos e imutáveis desde a criação, inclusive em rascunho. O padrão avulso é BRL; medição vinculada deriva a moeda do contrato quando omitida e exige igualdade quando informada. Vínculo posterior/composição incompatível responde `409`, sem converter nem reinterpretar valores. A V84 apenas atribui BRL ao legado, sem recalcular dinheiro ou alterar itens históricos.
- Versões, serviços, grupos, composições, alocações, NFs, impostos, títulos, baixas, estornos, créditos e renegociações herdam a moeda por sua cadeia obrigatória de medição/contrato imutável; não são moedas independentes editáveis. FK composta de medição/contrato e triggers impedem divergência ou troca no banco. Respostas financeiras de topo expõem `currency`; filhos usam a mesma unidade. `202` preserva somente proposta e a aprovação revalida a cadeia.
- Não somar valores nominais de contratos em moedas diferentes. Consultas atuais de faturamento preservam uma linha/unidade por contrato; qualquer consolidação futura de obra, caixa ou resultado deve publicar grupos separados por código ISO. Gastos independentes de contrato também precisarão declarar moeda antes de integrar esses consolidados. Não há câmbio ou moeda consolidada fictícia.
- Datas: data local para emissão/vencimento e timestamp UTC para auditoria.
- FinalCustomer possui company_id obrigatório. Work possui final_customer_id obrigatório. Contract possui work_id obrigatório e não armazena um cliente final independente; o cliente final do contrato é derivado da obra. As chaves estrangeiras e a validação de tenant impedem vínculos entre empresas distintas.
- Measurement possui `company_id` obrigatório e `work_id`/`contract_id` opcionais. A constraint impede `contract_id` sem `work_id`; quando presentes, as chaves compostas e a aplicação garantem que obra, contrato e medição pertencem ao mesmo tenant e à mesma cadeia.
- Serviço contínuo, destino, grupo aceito e composição preservam `company_id`, `measurement_id` e referências estáveis. Constraints compostas impedem associar item/grupo de outra medição ou serviço de outro contrato; origem/versão usa unicidade para impedir duplicidade de composição, inclusive sob concorrência.
- Destino oficial usa versão otimista e histórico auditável. Nova migration compatível preserva IDs e composições legados; associações ambíguas não são resolvidas automaticamente por título. Revisões corretivas mantêm referências de origem e lançamentos de reversão/substituição, sem sobrescrever composição aceita.
- Work registra o tipo de local de execução e o endereço utilizado naquela obra. Isso permite executar na unidade do cliente final, na sede/unidade da empresa prestadora ou em outro endereço.
- Payable possui financial_scope obrigatório. Para escopo Obra, work_id é obrigatório e contract_id é opcional; quando contract_id existir, ele deve pertencer ao mesmo work_id. Para escopo Empresa, work_id e contract_id devem ser nulos.
- Receita, gastos e lucro de obra são projeções calculadas a partir dos contratos e títulos financeiros da obra; não haverá lançamento direto de receita ou saldo na tabela obra.
- `EmployeePaymentAccount` armazena banco, código, agência, tipo, número de conta, dígito e chave Pix. Número, dígito e chave Pix usam ciphertext em repouso; a consulta operacional devolve somente máscaras. A leitura completa é pontual, permissionada e auditada, para o responsável executar pagamento manual fora do sistema.
- `EmployeeEmployment` possui `company_id`, `employee_id`, tipo de vínculo, início, estado, datas de experiência/efetivação/desligamento e versão otimista. Há no máximo um vínculo aberto (`PROBATION`, `ACTIVE` ou `NOTICE_PERIOD`) por funcionário e empresa; uma readmissão só é possível após o vínculo anterior estar `TERMINATED`.
- `EmployeeNoticePeriod` pertence a um vínculo, preserva o estado anterior e registra início, fim previsto, fim efetivo, motivo e situação. `REVOKED` não apaga nem reabre o aviso; apenas devolve o vínculo ao estado anterior permitido.
- `EmployeeComplianceRecord` representa curso, certificado ou exame por metadados administrativos seguros. Para curso/certificado pode guardar carga horária e emissor; para exame, somente tipo, aptidão administrativa e validade. O conteúdo médico detalhado não é dado de domínio nem anexo aceito por padrão.
- `EmployeeDocument` referencia objeto privado no `StorageProvider`, categoria, metadados mínimos, hash, validade opcional, situação e exclusão lógica. A chave interna de storage nunca integra DTO público.
- Nenhum saldo é informado manualmente quando puder ser calculado a partir de lançamentos aprovados.
- Registros financeiros usam referência, descrição, emissão, vencimento, valor bruto, descontos, impostos, valor líquido, situação e origem.
- Títulos parcelados são registros individuais ligados por um identificador de grupo financeiro; isso permite vencimentos, baixas, renegociações e estornos independentes por parcela.
- Alterações de valor preservam versões e eventos antes/depois.
- NFs registram, ao menos, número, série opcional, chave opcional, emissão, valor total, itens, impostos, situação e anexos PDF/XML.

## 8. API REST

Versão inicial: /api/v1.

| Grupo | Exemplos de recursos |
|---|---|
| Auth | login, refresh, logout, recuperação de senha, ativação, perfil. |
| Plataforma | empresas, planos, pacotes, assinaturas, cobranças, desbloqueios, cortesias temporárias e suporte. |
| Empresa | dados da empresa, usuários, papéis, permissões, auditoria. |
| Funcionários | perfil, vínculos de trabalho, experiência, aviso prévio, desligamento/readmissão, cursos, exames, documentos privados e referências de pagamento. |
| Clientes finais | clientes finais, contatos e endereços. |
| Obras | obras, local de execução, status e vínculo com cliente final. |
| Contratos | contratos de uma obra, serviços, modelos, descontos e status. |
| Medições | medições, revisões, itens, aceite registrado, geração de Excel/PDF, vínculo com contrato. |
| Receita | faturamentos, parcelas, alocações, NFs, impostos, contas a receber e baixas por contrato. |
| Despesas | fornecedores, categorias, contas a pagar, custos/despesas de obra, gastos da empresa e baixas. |
| Relatórios | filtros, prévias, exportações Excel/PDF/DOCX, histórico e envio. |
| Governança | solicitações pendentes, aprovações, rejeições, eventos de auditoria. |

Convenções obrigatórias:

- paginação, ordenação e filtros em listagens;
- erros padronizados com código, mensagem e correlação;
- validação de permissão antes da execução;
- idempotência para webhooks e operações financeiras críticas;
- controle de versão otimista em registros que afetam saldo;
- OpenAPI atualizado e exemplos de erro/autorização.

## 9. Documentos, relatórios e anexos

### 9.1 Relatórios iniciais

| Relatório | Dados principais |
|---|---|
| Clientes finais ativos | Cliente final, contatos, contratos ativos, saldo e situação. |
| Valores a receber | Parcela, cliente, contrato, vencimento, valor, recebido, saldo e atraso. |
| Contratos abertos | Cliente final, obra, valor líquido, faturado, saldo, status e prazo. |
| Contratos finalizados | Cliente final, obra, valores contratados, faturados, emitidos em NF, recebidos e saldo final. |
| Contratos a receber | Contrato, parcelas futuras/vencidas, NFs e projeção de recebimento. |
| Controle de faturamento | Serviço, parcela, NF, faturado, saldo por serviço e saldo do contrato. |
| Gastos e resultado por obra | Receita faturada, emitida e recebida de todos os contratos; custos, despesas e impostos de escopo Obra; resultado previsto e resultado de caixa. |
| Gastos gerais da empresa | Despesas e impostos de escopo Empresa, vencimentos, pagamentos e impacto no caixa geral, sem atribuição de lucro a uma obra. |
| Medição acumulada | Mesmo título e serviços contínuos, itens aceitos vigentes e itens da revisão atual identificados, totais aceitos separados dos pendentes, marca privada da emitente e resumo oficial aceito/recebido/falta receber quando autorizado. |

Formatos: Excel, PDF e DOCX. Relatórios devem ser gerados de dados aprovados/efetivos, exceto quando o filtro selecionar explicitamente pendências.

Toda exportação deve usar streaming: buscar dados em lotes ordenados, escrever o arquivo diretamente no `OutputStream` HTTP e manter no heap somente a janela técnica atual. É proibido materializar o conjunto completo de linhas, `List` integral, `byte[]` integral ou `ByteArrayOutputStream` proporcional ao arquivo. Para XLSX, usar escritor de linhas com arquivos temporários; para PDF/DOCX, usar o mecanismo de cache temporário/flush oferecido pela biblioteca. O evento de auditoria registra formato, filtros seguros, total de linhas e resumo permitido, nunca o conteúdo do relatório.

PDF/XLSX acumulados e sua prévia devem representar a mesma projeção: título cadastrado centralizado, logo privada ou nome da empresa emitente, cabeçalhos de serviço e itens aceitos/avanço atual com identificação explícita. A inclusão do avanço atual não o reconhece financeiramente. Os três valores financeiros do PDF permanecem baseados nas origens aceitas vigentes. O `filename` vem do título sanitizado; PDF de prévia é inline, downloads mantêm a extensão, e nenhuma rota cria URL pública. Relatórios históricos por versão preservam seu significado e não passam a incluir versões posteriores.

No PDF oficial, acumulado ou por versão e respectivas prévias, a logo nunca substitui a identificação textual: mostrar sempre nome cadastrado (fantasia, senão razão social) e CNPJ da Company emitente da sessão, com máscara e fallback explícito quando ausente/incompatível, sem inventar documento ou usar cliente final. Repetir o bloco em cada página e reservar alturas/larguras para que logo, nome/CNPJ, título centralizado e tabela não colidam. Este complemento é exclusivo de PDF; não altera a apresentação XLSX ou o cadastro da empresa. Implementação/verificação/publicação acompanhadas em [F04-43](FRONTEND-MEASUREMENT-PDF-EMITTER.md).

### 9.2 Armazenamento de arquivos

- Interface StorageProvider para não acoplar o sistema a um fornecedor.
- Desenvolvimento: armazenamento local controlado. No Docker local, volume privado persistente em /app/var/storage, diretório 0700 pertencente ao usuário não root da API; não usar a camada descartável do container para conservar anexos. A preparação local e sua prova após recriação estão em [F08-23](F08-23-LOCAL-PRIVATE-STORAGE.md). Não remover o volume em um restart comum; backup externo/object storage de produção são responsabilidades separadas.
- Produção: armazenamento de objetos compatível com S3, com URLs temporárias.
- Arquivos permitidos: PDF, XLSX, CSV, DOCX e XML, com limite configurável e verificação de tipo.
- Todo anexo registra empresa, entidade de origem, autor, data, hash e evento de auditoria.
- Documento de funcionário aceita somente PDF, PNG ou JPEG, passa por validação de tipo/tamanho, verificação antimalware antes da disponibilidade e é gravado em objeto privado cifrado em repouso. Download é autenticado, autorizado e sem cache público; URL pública, chave de storage e conteúdo do documento nunca são retornados em listagens.
- Cursos, certificados, comprovantes de endereço e documentos pessoais podem anexar cópia/foto. Exames aceitam somente o comprovante administrativo necessário à validade; a empresa não deve enviar diagnóstico, prontuário, resultado clínico detalhado ou dado biométrico.

## 10. Infraestrutura, qualidade e operação

### 10.1 Situação atual do repositório

A base atual possui Java 21, Spring Boot, JPA, Flyway, PostgreSQL, Spring Security declarado, Swagger e Docker Compose para PostgreSQL. Há apenas a primeira tabela de empresa e um esqueleto de camadas; ainda não há autenticação própria, endpoints de negócio, domínio financeiro ou controle multiempresa.

Antes de ampliar funcionalidades, deve-se:

1. corrigir o fluxo de atualização da entidade Company, que hoje tende a recriar a entidade;
2. substituir credenciais fixas por variáveis de ambiente;
3. alinhar constraints da migration com a entidade;
4. configurar tratamento de erro, validações e convenções de API;
5. criar Dockerfile da aplicação e Docker Compose completo para ambiente local.

### 10.2 Estratégia de testes

| Camada | Cobertura necessária |
|---|---|
| Unidade | Fórmulas de medição, descontos, limites de faturamento, estados e permissões. |
| Integração | Repositórios, migrations Flyway, transações, concorrência e PostgreSQL com Testcontainers. |
| Segurança | Isolamento entre empresas, papéis, suporte global, rate limit, bloqueio por tentativas, MFA, RLS, criptografia de dados sensíveis e auditoria. |
| API | Contratos OpenAPI, validação, erros, paginação e autorização. |
| Integração externa | Webhooks idempotentes, falhas de gateway, e-mails e armazenamento de anexos. |
| Regressão financeira | Estornos, pagamentos parciais, cancelamentos e fechamento de saldo. |
| Medição contínua | Revisão vazia mantém aceitos na grade/PDF/XLSX; destino persiste antes do aceite; avanço aplica somente seu incremento; repetição/concorrência e `202` não duplicam composição/pedido; duas medições no mesmo serviço isolam baixas; correções não duplicam cópias e importação não inativa aceitos. |

### 10.3 Operação

- ambientes local, homologação e produção;
- backups automáticos do PostgreSQL e política de restauração testada;
- logs estruturados com ID de correlação;
- health checks, métricas e alertas;
- execução de migrations Flyway em pipeline controlado;
- CI com compilação, testes e análise estática;
- Docker com imagens versionadas;
- segredos por ambiente;
- rotação documentada de chaves de assinatura e criptografia, com inventário de segredos sem seus valores;
- documentação operacional para reprocessar webhooks e recuperar anexos.

## 11. Backlog técnico e estimativa

Estimativas são em dias ideais de desenvolvimento para uma pessoa experiente e não incluem frontend. Elas variam conforme refinamento de telas, provedor de pagamento, ambiente de produção e regras fiscais.

| Fase | Tarefa | Dependência | Complexidade | Estimativa |
|---|---|---|---|---|
| 0 | Corrigir base atual, convenções, erros, UUIDs, DTOs e validações | — | M | 3–5 dias |
| 0 | Dockerfile, Docker Compose da aplicação, configuração por ambiente e segredos | — | M | 3–5 dias |
| 0 | Base de testes com Testcontainers, CI e padrão de migrations | Fase 0 | L | 5–8 dias |
| 1 | Usuários, login, senha, tokens, recuperação e ativação | Fase 0 | L | 8–12 dias |
| 1 | Tenant context e isolamento obrigatório por empresa | Identidade | XL | 10–16 dias |
| 1 | Papéis, permissões granulares e alçadas por módulo | Tenant context | XL | 12–18 dias |
| 1 | Usuários globais de plataforma e suporte multiempresa | Papéis | L | 5–8 dias |
| 1 | Auditoria imutável, solicitações, aprovação e rejeição | Papéis | XL | 15–24 dias |
| 2 | Empresas, planos, módulos cumulativos e pacotes promocionais | Fase 1 | L | 8–12 dias |
| 2 | Assinaturas, trial, preços históricos e estados de acesso | Planos | L | 8–12 dias |
| 2 | Adaptador de gateway, Asaas, webhooks e conciliação | Assinaturas | XL | 15–24 dias |
| 2 | Inadimplência, bloqueios, desbloqueios de confiança e e-mails | Gateway | L | 7–12 dias |
| 3 | Clientes finais, contatos e endereços | Fase 1 | M | 5–8 dias |
| 3 | Obras, tipos de local de execução, status e histórico | Clientes finais | L | 6–10 dias |
| 3 | Contratos de obra, status e histórico | Obras | L | 7–11 dias |
| 3 | Modelos de serviço, serviços, descontos e limites de contrato | Contratos | XL | 12–18 dias |
| 4 | Medições, revisões, itens e fórmulas de cálculo | Fase 3 | XL | 14–22 dias |
| 4 | Excel/PDF de medição, anexos e registro de aceite externo | Medições | L | 8–14 dias |
| 4 | Conversão de medição aceita em serviços adicionais do contrato | Medições e serviços | L | 6–10 dias |
| 5 | Faturamento, parcelas, alocações e bloqueio de excedentes | Serviços | XL | 14–22 dias |
| 5 | NFs, itens, impostos e anexos PDF/XML | Faturamento | XL | 12–20 dias |
| 5 | Contas a receber, recebimentos parciais, atraso e renegociação | Faturamento | L | 9–14 dias |
| 6 | Fornecedores, categorias, contas a pagar e classificação por escopo Obra/Empresa | Fase 1 | L | 10–16 dias |
| 6 | Pagamentos, fluxo de caixa geral, resultado por obra e gastos gerais da empresa | Receita e despesas | XL | 12–20 dias |
| 7 | Consultas de relatório, filtros, permissões e prévias | Domínio financeiro | L | 7–12 dias |
| 7 | Exportação Excel, PDF e DOCX dos relatórios iniciais | Relatórios | XL | 15–24 dias |
| 7 | Envio auditado de relatórios por e-mail | Exportação e e-mail | M | 4–7 dias |
| 8 | Testes de segurança e regressão financeira completos | Todas | XL | 15–25 dias |
| 8 | Observabilidade, backup, documentação de operação e hardening | Todas | L | 8–14 dias |
| 9 | Endurecimento de autenticação, segredos, criptografia, rate limit, bloqueio de tentativas, MFA e RLS | Fases 0, 1 e 8 | XL | 20–32 dias |
| 10 | Vínculos de trabalho, readmissão, aviso prévio, conformidade documental e notificações de vencimento | Fases 1, 3, 8 e 9 | XL | 24–38 dias |
| 11 | Cortesias temporárias de assinatura, pausa de cobrança e retomada auditável | Fase 2, Fase 8 | L | 8–14 dias |

### 11.1 Leitura da dificuldade

| Entrega | Dificuldade | Faixa de esforço |
|---|---|---|
| Núcleo técnico e segurança multiempresa | Muito alta | 55–85 dias |
| Plataforma de assinaturas e cobrança | Alta a muito alta | 38–60 dias |
| Clientes finais, obras, contratos e serviços | Alta a muito alta | 30–47 dias |
| Medições versionadas e documentos | Muito alta | 28–46 dias |
| Faturamento, NF e contas a receber | Muito alta | 35–56 dias |
| Contas a pagar, custos, despesas e fluxo de caixa | Alta a muito alta | 22–36 dias |
| Relatórios e exportações | Alta | 26–43 dias |
| Qualidade, operação e regressão | Alta | 23–39 dias |
| Segurança avançada | Muito alta | 20–32 dias |
| Pessoas, vínculos e conformidade | Muito alta | 24–38 dias |
| Cortesias temporárias de assinatura | Alta | 8–14 dias |
| **Total API em qualidade de produção** | **Muito alta / XL** | **aprox. 237–379 dias ideais** |

O intervalo não representa prazo de calendário: uma equipe pode paralelizar partes independentes, mas segurança, governança, contratos e regras financeiras formam o caminho crítico. Para uma pessoa, é um produto de vários meses; para uma equipe pequena, ainda exige fases e validações de negócio.

### 11.2 Ordem recomendada de entrega

1. Fundação, identidade, isolamento, papéis e auditoria.
2. Clientes finais, obras, contratos, serviços, descontos e limite de faturamento.
3. Faturamento, parcelas, NFs, contas a receber e primeiro relatório de faturamento.
4. Medições, revisões, planilhas e conversão em serviço de contrato.
5. Contas a pagar, custos, despesas, impostos e fluxo de caixa.
6. Planos, assinaturas, gateway de pagamento, dunning e suporte global. Esta fase pode ser desenvolvida parcialmente em paralelo após identidade.
7. Relatórios restantes, documentos, endurecimento, testes e operação.
8. Endurecimento de segurança da Fase 9 antes de publicar dados pessoais adicionais.
9. Vínculos de trabalho, conformidade de funcionários e notificações da Fase 10.
10. Cortesias de assinatura da Fase 11, após estabilizar dunning, cobrança e auditoria.

## 12. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Isolamento incorreto entre empresas | Crítico | Tenant context central, testes negativos de acesso e filtros obrigatórios. |
| Faturamento acima do contrato | Crítico | Transação, versão otimista, bloqueio de saldo e testes de concorrência. |
| Confusão entre NF, faturamento e recebimento | Alto | Entidades e status separados; relatórios distinguem emitido, a receber e recebido. |
| Mudanças sem rastreabilidade | Alto | Auditoria imutável e aprovação baseada em proposta. |
| Escopo fiscal crescer para emissão de NF | Alto | Manter apenas registro de NF no MVP e encapsular integração futura. |
| Dependência de gateway | Alto | Interface de gateway, webhooks idempotentes e reconciliação. |
| Retenção excessiva de anexos | Médio | Storage de objetos, limites de arquivo e política de retenção. |
| Exposição de dados pessoais ou de saúde do funcionário | Crítico | Criptografia em repouso, anexos privados, menor privilégio, auditoria sem conteúdo, retenção configurada e vedação de diagnóstico/prontuário. |
| Ataque de força bruta ou sessão comprometida | Crítico | Rate limit distribuído, bloqueio progressivo, MFA, rotação/revogação de token, segredos separados e monitoramento. |
| Relatórios inconsistentes | Alto | Consultas a partir de dados aprovados/efetivos, testes de regressão financeira. |
| Escopo grande demais para primeira entrega | Alto | Entregas verticais por fase e validação com usuários antes de avançar. |

## 13. Critérios de aceite de arquitetura

O desenho será considerado atendido quando:

1. um usuário de empresa não conseguir ler, alterar, enumerar ou exportar dados de outra empresa;
2. todo usuário possuir permissões verificadas no backend;
3. alterações sem alçada não mudarem o dado oficial nem aparecerem nos relatórios;
4. nenhum solicitante conseguir aprovar a própria solicitação;
5. toda ação direta, aprovada, rejeitada, cancelada ou executada por suporte deixar evento auditável;
6. não houver exclusão física de obras, registros financeiros e contratuais;
7. um cliente final puder possuir várias obras, cada obra tiver um único cliente final e cada contrato possuir uma única obra;
8. contratos sem serviços puderem existir, mas não possam receber faturamento acima de seu valor líquido;
9. saldo faturável, parcelas, NFs e contas a receber sejam controlados por contrato, sem que um total de obra modifique o limite de um contrato;
10. receitas, custos, despesas e impostos de escopo Obra sejam consolidados no resultado da obra a partir de todos os seus contratos;
11. gasto de escopo Empresa não possua obra/contrato e nunca componha lucro de uma obra;
12. serviços de contrato não puderem pertencer a mais de um contrato;
13. uma medição aceita preservar sua versão, fórmulas, totais, evidências e origem dos serviços gerados;
14. uma NF não puder apontar para mais de um contrato;
15. recebimentos e pagamentos parciais atualizarem corretamente os saldos e o fluxo de caixa;
16. empresa inadimplente passar pelos estados de consulta e bloqueio definidos;
17. relatórios respeitarem tenant, permissão, aprovação e filtros;
18. todas as migrations possam iniciar um banco vazio e atualizar um ambiente existente sem alterar dados manualmente.
19. exportações e relatórios de grande volume lerem e escreverem em streaming, sem reter o conjunto ou o arquivo completo em memória.
20. falhas de autenticação sejam limitadas e bloqueadas sem revelar a existência de uma conta, e o MFA proteja ao menos o proprietário da plataforma;
21. políticas RLS, filtros de tenant e testes negativos impeçam consulta ou mutação de outra empresa mesmo por consulta acidental;
22. documento, PIS, endereço, remuneração, conta e Pix de funcionário estejam cifrados em repouso, e documentos privados nunca sejam públicos nem apareçam em logs/auditoria;
23. readmissão crie novo vínculo de trabalho sem alterar o desligamento anterior, e aviso prévio possa ser revogado sem perda de histórico;
24. alerta de vencimento de contrato comercial, experiência, aviso prévio, curso, certificado ou exame seja idempotente e não altere automaticamente situação de negócio.
25. a consulta/grade/PDF/XLSX acumulados mantenham os serviços e itens aceitos no corpo principal após criar nova revisão, com somente o avanço `DRAFT` atual editável;
26. destino preparado em rascunho sobreviva à recarga sem alterar contrato, e aceite/vínculo aplique somente incrementos aceitos autorizados, uma única vez;
27. o resumo oficial publique total aceito, recebido e falta receber com disponibilidade/asOf, sem somas locais nem baixa fictícia em medição avulsa;
28. composições, baixas e estornos preservem a medição de origem mesmo quando medições diferentes complementarem o mesmo serviço, e correções/substituições não dupliquem valores;
29. a entrega ao frontend inclua OpenAPI executável/exportado e caminhos definitivos para a visão e relatórios acumulados; histórico isolado ou resumo financeiro sozinho não concluem essa integração.

## 14. Próximos passos de implementação

### 14.1. Contratos implementados da medição contínua

- `MeasurementContinuousService` mantém identidade e destino (`destinationAction`, `contractServiceId`, `lockVersion`) entre avanços; o histórico de alterações fica em `AuditEvent`. Não há associação financeira automática por título.
- `MeasurementItemRecognition` registra, de forma imutável, gross/net/peso e raiz (`rootItemId`) por origem aceita. A projeção acumulada resolve a posição aceita mais recente de cada raiz. Uma cópia corretiva pendente é proposta separada; rejeição não modifica o reconhecimento.
- Composições positivas representam somente o incremento novo. Redução exige reversão imutável dos predecessores afetados e substituição por origem corretiva. Faturamento ativo da origem impede redução até sua reversão/cancelamento; aceito reduzido ainda sem reconciliação comercial não pode ser faturado pela posição obsoleta.
- Destino, aceite e composição têm alçadas independentes. Composição `AWAITING_APPROVAL` não altera `ContractService`. Repetição é idempotente por grupo aceito sob lock da medição/contrato; proposta equivalente pendente de workflow/vínculo é reutilizada sob lock.
- `GET /api/v1/company/measurements/{measurementId}/accumulated` fornece grade e totais documentais oficiais separados. `pendingTotals` é a variação proposta, inclusive negativa, e não um saldo faturável. O rateio do desconto do cabeçalho é exclusivo do backend.
- `financial-summary` e a projeção acumulada retornam `remainingToReceive`, `financialAdjustmentAmount`, `acceptedUncomposedAmount`, disponibilidade e `asOf` UTC. Sem vínculo/permissão, valores financeiros protegidos são nulos, não zero. A leitura/exportação dos itens autorizados permanece disponível.
- PDF/XLSX acumulados e prévia lêem itens em lotes e usam escrita streaming/SXSSF e cache de PDF em disco. A leitura usa snapshot transacional; mantém marca privada, título, serviço, origem e situação de cada linha. Downloads por versão permanecem históricos. A planilha acumulada é relatório e não pode ser sincronizada pelo importador de rascunho.

### 14.2. Origem financeira, crédito e renegociação

- `ReceivableSettlementAllocation` atribui a baixa a cada `BillingAllocation` do mesmo título/parcela/contrato/tenant. O recebimento não é re-rateado retroativamente; estorno registra as mesmas parcelas negativas. Rateio monetário usa duas casas, truncamento proporcional e fechamento determinístico dos resíduos por UUID, limitado à capacidade remanescente de cada origem.
- Desconto contratual, retenções e créditos são ajustes oficiais do devido, não alterações do preço histórico aceito nem dinheiro recebido. Crédito é lançamento imutável com reversão própria e motivo, restrito ao saldo não pago. Não libera a alocação canônica nem permite reduzir silenciosamente valor comprometido.
- Renegociação reprograma exclusivamente o saldo ainda devido em novos títulos individuais de mesma parcela e grupo financeiro, com origem persistida no título anterior. A soma é exata; não gera novo faturamento, juros, aumento de receita, perdão ou baixa fictícia. O título anterior passa a `RENEGOTIATED` e não aceita novas baixas.
- Cancelamento da renegociação é lógico e exige reversão anterior de recebimentos, ajustes e renegociações descendentes; reabre o saldo original. Título/pagamento original renegociado e retenção que formou os novos títulos não podem ser alterados sem reabrir a renegociação.
- Valores com origem em medição exigem a composição na alocação; valores sem origem podem usar somente a base negociada disponível. Conversões individuais legadas são preservadas, mas não podem compor novamente a mesma raiz no fluxo contínuo.
- Quando houver conversão individual legada, o resumo contínuo indica `LEGACY_ORIGIN_REQUIRES_RECONCILIATION` e não inventa zero de faturamento/recebimento sem atribuição segura. A reconciliação histórica precisa ser explícita; não reinterpretar preços nem atribuir baixas automaticamente por nome. O OpenAPI 3.1 documenta os financeiros indisponíveis como `string | null`, preservando decimal canônico quando disponível.

### 14.3. Revisão financeira F05-14

O [registro de revisão](F05-14-FINANCIAL-INVARIANTS-REVIEW.md) acompanha a cadeia composição → posição comercial → faturamento → NF → baixa/estorno com provas nos GETs oficiais, nos modos `FIXED` e `MEASUREMENT_BASED`. As respostas da receita usam strings decimais canônicas de duas casas, mantendo `BigDecimal` no servidor e sem arredondar na serialização. Isso inclui alocações, resumo paginado, NF/itens, títulos/saldos, baixas e impostos.

Alteração comercial de serviço compartilha o lock contratual versionado com composição/faturamento, antes das leituras usadas na decisão, e revalida seu comprometimento próprio, não apenas o teto global do contrato. A base líquida negociada também precisa cobrir as alocações sem origem: composição aceita não pode encobrir excesso antes atribuído à base. Serviço cancelado não recebe faturamento; serviço com composições aceitas vigentes exige reversão formal antes do cancelamento. Redução/reversão de desconto mantém lastro suficiente para as alocações contratuais negativas ativas. NF resolve alocações ativas e capacidade somente após obter o lock. Aprovações repetem as validações; uma proposta `202` não antecipa valores oficiais. São reforços dos invariantes existentes, sem nova fórmula, hierarquia, efeito financeiro da medição ou migration de dados reais.

### 14.4. Moedas e apresentação oficial de relatórios

`GET /api/v1/company/currencies` lista códigos, nomes, símbolos, duas casas e BRL como padrão no contexto autenticado. Criação de contrato/medição aceita `currency` opcional; `PUT` de contrato aceita apenas omissão ou o código original. Não há troca de moeda por edição comum: se um rascunho nasceu na moeda errada, deve ser cancelado logicamente/recriado quando permitido. Aceite, composição e comprometimentos nunca são reinterpretados.

PDF/XLSX acumulados e por versão compartilham a ordem visual: título → faixa centralizada de serviço → atividades → subtotal → totais. Dez colunas visíveis: Item, Situação, Atividade/descrição, Data, Und, m ou m², kg/m ou kg/m², Peso (kg), Preço (símbolo), Valor (símbolo). O acumulado agrupa por identidade contínua e separa aceitos de variação pendente; o histórico permanece limitado à versão consultada. `unitQuantity` fora de UNIT é apenas operacional. Nenhum enum técnico ou coluna Fórmula é mostrado.

A marca é a logo privada de Company, carregada por chave interna do tenant e decodificada/validada antes de inserir; imagem inválida/ausente usa nome fantasia/razão social. O aspecto/transparência é preservado. PDF quebra descrições longas, repete cabeçalho/serviço e referência de item continuado, mantendo cifra em cada preço, valor, desconto, subtotal e total: `R$ 8.000,00`, `US$ 8.000,00`, `€ 8.000,00`. Quantidades, pesos e percentuais não recebem cifra. Formatação recebe BigDecimal diretamente, sem double/float.

XLSX por versão preserva campos estáveis ocultos, decimais exatos, fórmulas de prévia não oficiais e metadados muito ocultos de medição/versão/moeda. O importador usa os IDs oficiais, valida a origem e a moeda e nunca usa totais/fórmulas como cálculo oficial. Aceita templates legados apenas para BRL. O XLSX acumulado continua somente leitura e não é importável. O backend mantém streaming com janela de linhas/cache em disco, autorização, tenant, auditoria, filenames seguros e downloads privados `no-store`.

Detalhes de integração, testes e estado da publicação: [guia de moedas e relatórios](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md).

### 14.5. Continuação do planejamento geral

1. Tratar [AGENTS.md](../AGENTS.md) como regras obrigatórias de implementação e preservar seus invariantes.
2. Executar [CODEX-IMPLEMENTATION-BACKLOG.md](CODEX-IMPLEMENTATION-BACKLOG.md) em tarefas pequenas, respeitando dependências e perfil Codex recomendado.
3. Validar este SDD como referência do MVP.
4. Escolher e habilitar a conta de gateway de pagamento; o adaptador inicial recomendado é Asaas.
5. Definir provedor de e-mail e armazenamento de arquivos para produção.
6. Corrigir a fundação atual antes de criar novos CRUDs.
7. Implementar primeiro os invariantes de segurança, auditoria e saldo; somente depois as telas ou endpoints de operação.
