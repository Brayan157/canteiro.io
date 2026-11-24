# SDD — Plataforma SaaS de Gestão Financeira de Obras

**Nome comercial:** a definir  
**Repositório atual:** Canteiro.io  
**Versão do documento:** 0.9<br>
**Data:** 16 de outubro de 2026<br>
**Status:** requisitos de negócio consolidados; recomendações técnicas identificadas como tal  

Atualização 0.7: medição contínua, identidade de serviço entre avanços, destino contratual persistido, composição incremental e visão/relatórios acumulados. Estas decisões especificam o comportamento exigido; o estado de implementação permanece no backlog e a disponibilidade de cada rota depende do OpenAPI executável.

Atualização 0.8: contratos de implementação da medição contínua, reconhecimento por raiz de item, crédito/renegociação de recebíveis e atribuição imutável de baixas. Integração publicada no [guia atual do frontend](FRONTEND-MEASUREMENT-CONTINUOUS.md); verificação e pendências são registradas no backlog.

Atualização 0.9: moedas explícitas BRL/USD/EUR, imutáveis e sem câmbio; relatórios oficiais com dez colunas, marca privada, agrupamentos e apresentação monetária pt-BR. Publicação local autorizada em 14/10/2026, com V84 aplicada e OpenAPI vivo exportado; [F04-41 concluída em 15/10/2026](F04-41-LOCAL-PUBLICATION.md) após confirmação do frontend de contrato/tipos sincronizados, moedas e PDF/XLSX autenticados com filename oficial. CORS e storage local privado foram publicados com autorizações separadas; a logo real aguarda o usuário/interface do frontend, sem autorizar envio de imagem por agentes.

Complemento de planejamento de 25/10/2026: locações mensais ou por diária/quantidade, serviços por diária e lembretes de contas de aluguel. Regras confirmadas e decisões em aberto estão nas seções 5.5.1 e 5.7, com implementação futura nas tarefas F06-15 a F06-19 e F12-12. Este complemento não publica contratos executáveis nem altera o lançamento atual de Payable.

Priorização humana de 26/10/2026 após conversas com empresários: simplificação financeira existente, H.H. manual e vencimentos da equipe. Somente planejamento nesta atualização; recorte/dependências nas seções 3.4, 5.7.1 e 5.9, aproveitando F10/F12, sem autorizar implementação, migração, publicação ou envio externo.

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
| D-16 | Desconto contratual é separado dos preços/aceites, que não são regravados. F05-21 autorizada em 16/10/2026: novos faturamentos recebem líquido positivo, com distribuição proporcional no backend exclusivamente sobre a capacidade ainda não comprometida de base/composição exata. Cada alocação congela base consumida e desconto aplicado; linhas negativas legadas e seus efeitos históricos são preservados. |
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
| D-27 | Clientes finais e funcionários possuem consulta paginada e filtrável exclusivamente no tenant autenticado. Cliente final pode ser pesquisado por nome, CPF/CNPJ normalizado, tipo e situação. A lista operacional de funcionário pesquisa somente por nome, cargo, departamento, vínculo e situações de trabalho/cadastro; não aceita documento/PIS nem modalidade/faixa de remuneração e não ordena por remuneração, para não levar esses dados a URL, histórico ou logs operacionais. Salário e perfil completo permanecem no detalhe autenticado; permissões granulares de RH ainda são pendentes. A edição de cliente final usa a alçada direta/solicitação de aprovação já existente e preserva auditoria antes/depois. |
| D-28 | Obras e contratos possuem consulta paginada e filtrável exclusivamente no tenant autenticado. Obras podem ser pesquisadas por nome, referência, cliente final, situação, tipo/local de execução e períodos de início/previsão; além do UUID opcional, o cliente final pode ser localizado por trecho de nome ou CPF/CNPJ normalizado. Contratos podem ser pesquisados por nome, referência, obra, nome do cliente final da obra, situação e períodos de início/previsão/conclusão; os filtros relacionais por nome são resolvidos somente no servidor dentro do tenant. A edição usa alçada direta ou solicitação de aprovação e registra auditoria antes/depois. A edição de obra preserva `finalCustomerId` e a edição de contrato preserva `workId`: uma troca desses vínculos exige fluxo explícito futuro, pois pode afetar a hierarquia comercial, medições e faturamento. |
| D-29 | Relatórios, planilhas e documentos potencialmente volumosos são produzidos por streaming. A leitura ocorre em lotes ordenados e limitados; a resposta HTTP grava diretamente no fluxo de saída, sem montar uma lista completa de linhas ou um `byte[]` completo do arquivo. XLSX usa escrita de linhas em disco temporário e PDF usa cache temporário configurado. O tamanho do lote e a janela de linhas são parâmetros técnicos limitados, e toda consulta mantém tenant, permissão, filtros e auditoria. |
| D-30 | Contrato possui `contractIdentifier` opcional, texto livre de negócio para número, código ou nome externo do instrumento (por exemplo, `0001/2027` ou `Contrato padrão`). Ele não é único, nem mesmo dentro da empresa, pois clientes finais distintos podem reutilizar a mesma numeração ou padrão. |
| D-31 | Desconto de serviço e ajuste de desconto de contrato podem ser alterados ou revertidos somente por ação explícita, com justificativa, alçada direta ou solicitação de aprovação e auditoria antes/depois. Reversão não apaga evento nem registro: o serviço volta a não ter desconto e o desconto de contrato é desativado logicamente. Para reproduzir um desconto anterior, registra-se uma nova alteração com o valor desejado; não há rollback automático de auditoria ou faturamento. |
| D-32 | O perfil de `Employee` é permanente no tenant e não representa, por si só, um vínculo de trabalho ativo. A fonte canônica de situação laboral é `EmployeeEmployment`: cada admissão ou readmissão cria um novo vínculo histórico. O vínculo anterior nunca é reaberto, sobrescrito ou apagado. |
| D-33 | `EmployeeEmployment` registra início, tipo de vínculo, situação, período de experiência opcional, efetivação e desligamento. Situações permitidas são `PROBATION`, `ACTIVE`, `NOTICE_PERIOD` e `TERMINATED`. A efetivação, o início/revogação de aviso prévio e a confirmação de desligamento são operações explícitas, auditadas e sujeitas à alçada direta/solicitação de aprovação. O fim previsto do aviso não desliga automaticamente o funcionário: a confirmação de desligamento é obrigatória. |
| D-34 | Aviso prévio é um registro próprio ligado a um único vínculo de trabalho, com início, término previsto, situação `OPEN`, `REVOKED` ou `COMPLETED`, motivo e estado laboral anterior. A revogação preserva o aviso histórico e restaura somente o estado laboral que existia antes dele; um aviso concluído não pode ser reaberto. |
| D-35 | Cursos, certificados e exames periódicos pertencem ao funcionário e ao tenant, têm tipo, título seguro, emissão, validade opcional, situação administrativa e anexos privados. Para exame, a aplicação guarda somente aptidão/controle administrativo e validade, nunca diagnóstico, laudo clínico detalhado ou prontuário. |
| D-36 | Documentos pessoais, comprovantes de endereço, certificados e evidências de exame são anexos privados de funcionário. Eles não possuem URL pública, não são incluídos em auditoria, logs, analytics, telemetria ou exportações comuns; a auditoria registra somente categoria, operação e metadados seguros. Exclusão operacional é lógica, e a eliminação definitiva segue o fluxo de privacidade e retenção. |
| D-37 | O vencimento explicitamente cadastrado de contrato/vínculo de trabalho do funcionário com a empresa, experiência, aviso prévio, curso, certificado ou exame gera apenas alerta idempotente e auditável; nunca efetiva, renova, desliga, declara aptidão, bloqueia ou altera automaticamente vínculo/documento. No mínimo priorizado de 26/10/2026, painel próximos/vencidos/sem data e alertas internos têm antecedência configurável e acesso RH/saúde restrito; sem data não é vencido ou irregular. Contrato comercial mantém planejamento separado e não substitui o prazo trabalhista. Destinatários/cadência/fuso dependem de definição; canais externos são expansão futura autorizada separadamente. |
| D-38 | A importação de planilha de medição sincroniza somente itens de versão `DRAFT` em duas etapas: prévia validada e confirmação explícita do mesmo arquivo por hash. A chave de cada linha existente é exclusivamente o `itemId` estável incluído de forma oculta na exportação; `itemNumber`, título de serviço e atividade nunca identificam uma linha. A confirmação é atômica, recalcula totais no backend, inativa logicamente as linhas ausentes e registra a origem da planilha, autor, hash, versão anterior e alterações. Versões aceitas ou posteriores exigem revisão nova, sem alterar itens históricos. A exportação pode acrescentar linhas visuais mescladas `SERVIÇO: ...` para grupos consecutivos e cabeçalho com logo privada/fallback da empresa; essas linhas não são itens e o importador as ignora. As linhas de item preservam sempre as colunas estáveis `Serviço` e `Item ID`, e totais oficiais consideram somente itens ativos. |
| D-39 | Cortesia de assinatura é uma concessão auditável do proprietário da plataforma para uma `Company` e sua `Subscription`. Ela é distinta do trial e não altera preço, composição, histórico de cobrança ou planos contratados. Pode ser `TEMPORARY`, com início e fim inclusivos, ou `LIFETIME`, sem término e declarada explicitamente por tipo — ausência de data nunca significa vitalícia. Ambas exigem motivo, concedente, situação e versão otimista; concessões não podem se sobrepor. Durante a vigência, dão acesso integral e suspendem a criação de novas cobranças e avisos de inadimplência daquela assinatura. Cobranças abertas anteriores não são apagadas nem baixadas automaticamente: recebem tratamento explícito, auditável e definido pelo backend para não restringirem a empresa durante a cortesia e para não causarem bloqueio surpresa após expiração ou revogação. Revogação exige motivo, não apaga histórico e só vale prospectivamente. |
| D-40 | Uma `BillingAllocation` de serviço originada em medição preserva a `MeasurementContractComposition` do mesmo contrato e `ContractService`; a referência é opcional somente para a parcela de valor sem origem de medição. Essa origem percorre NF, título, recebimento e estorno. Uma composição não pode receber alocações ativas acima do líquido aceito. A projeção financeira reconhece origens aceitas vigentes, suas reversões/substituições e ajustes oficiais, sem somar snapshots corretivos duplicados; dinheiro é decimal canônico em texto. Origem ainda não composta não pode ser faturada. Conforme decisão manual F05-32, novos recebimentos registram valores explicitamente informados por NF/InvoiceItem/alocação/composição, sem rateio entre itens/serviços/NFs. Estorno reproduz exatamente esses vínculos e quantias negativas; atribuições proporcionais legadas permanecem históricas, sem inventar vínculo retroativo à NF. Duas medições no mesmo serviço contratual nunca recebem o valor integral da mesma baixa. Rascunhos, itens enviados/pendentes/rejeitados e propostas `202` não geram saldo contratual, NF, título ou recebimento. |
| D-41 | A medição mantém uma identidade contínua de serviço entre revisões. O corpo principal, a grade e os relatórios acumulados apresentam itens aceitos anteriores e itens do avanço atual agrupados nesse serviço, mesmo quando a nova revisão começa vazia. Aceitos permanecem congelados, com preços e origem preservados; somente itens da `DRAFT` atual podem ser editados, removidos, movidos ou reordenados por operação comum. A leitura acumulada é calculada no backend e não cria cópias oficiais de itens anteriores. |
| D-42 | O destino de cada serviço contínuo pode ser preparado antes do aceite: novo serviço contratual ou `contractServiceId` existente da mesma cadeia/empresa. A escolha é persistida, auditada, sujeita à alçada/aprovação e não muda valores comerciais. O aceite com contrato/destino, ou o vínculo posterior de origens já aceitas, coordena a aplicação autorizada somente do incremento novo. Aceite externo e aprovação interna da composição são fatos separados; permissões de medição não concedem permissões de serviço. Repetição/concorrência não duplica destino, serviço, composição ou solicitação para a mesma origem. O destino aplicado permanece nos próximos avanços; reassociar origem já aceita/composta exige operação formal. |
| D-43 | O relatório acumulado apresenta três valores oficiais: total líquido aceito, já recebido e falta receber. `remainingToReceive` inclui tanto o aceito ainda não faturado quanto o faturado ainda não recebido, considerados os ajustes, retenções, créditos e estornos oficiais aplicáveis; não equivale apenas a `billedOutstandingAmount`. A API publica o saldo, sua disponibilidade e instante de referência. Sem contrato ou sem autorização financeira, valores de recebimento/saldo ficam indisponíveis, nunca recebem zeros fictícios, e a exportação dos itens/totais permitidos continua disponível. |

### 3.4 Prioridades do MVP confirmadas em 26/10/2026 — somente planejamento

Após ouvir empresários, o usuário confirmou esta ordem comercial, sem autorizar a construção integral das fases:

| Prioridade | Recorte confirmado | Aproveitamento do backlog |
|---|---|---|
| 1 | Simplificar telas financeiras existentes e a compreensão de cobranças, saldos e tributos, preservando valores/regras oficiais do backend e sem transferir cálculo ao navegador. | Fluxos existentes e F05-27/F05-28; sua entrega backend não significa tela integrada/publicação. Não criar tarefa backend duplicada de UI. |
| 2 | H.H. manual simples: funcionário, obra, serviço, dia e horas; correções rastreáveis e resumo por funcionário/período/serviço. | Mínimos de F12-01, F12-02, F12-04, F12-05 e F12-09, com identidade/histórico de F10-01/F10-02 quando aplicável. |
| 3 | Vencimentos da equipe: contrato de trabalho do funcionário com a empresa, cursos/certificados e exames periódicos; datas cadastradas, painel próximos/vencidos/sem data e alertas internos com antecedência configurável. | Mínimos de F10-01, F10-03/F10-04 quando aplicáveis, F10-06 a F10-10. Não confundir com contrato comercial ou lotação. |

Ponto/espelho, folha e integrações automáticas ficam depois. Transferências temporárias completas, diária, DSR comercial, anexos completos e canais externos não são pré-requisitos funcionais indiscriminados do mínimo manual. As proteções necessárias de identidade, acesso por obra, aprovação, privacidade/RH/saúde e tenant continuam gates, inclusive F09-02/F09-09 conforme os dados tratados. Prioridade comercial não autoriza ignorá-las nem expor informações por permissões ainda não implementadas.

Este registro não publica contratos executáveis, muda fórmula/regra legal, implementa frontend, inicia módulo/job, aplica V89 ao banco real, reinicia API/Docker ou envia notificação. Cada implementação dependerá de tarefa explícita e das decisões pendentes abaixo.

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

A retenção técnica mínima do evento não autoriza copiar indiscriminadamente dados pessoais no seu conteúdo. Novos eventos de perfil de funcionário preservam identificação operacional e indicadores de existência, sem CPF/PIS completos, nascimento, telefone, remuneração, gênero ou etnia. Eventos já imutáveis não serão regravados ou apagados neste recorte. O tratamento de cópias históricas, propostas pendentes/decididas, backups e anexos exige inventário e fluxo separado de privacidade, com finalidade, base aplicável, prazo por categoria, preservação legítima e decisão autorizada; o prazo da auditoria não é prazo universal de todos os dados.

## 5. Regras de negócio

### 5.1 Obras, contratos, serviços e descontos

1. Um cliente final pode possuir várias obras.
2. Uma obra possui um único cliente final e pode ter vários contratos.
3. A obra guarda nome/identificação, status, datas, tipo de local de execução e endereço. O tipo de local inicial é: local do cliente final, sede/unidade da empresa prestadora ou outro local informado.
4. Um contrato pertence a uma única obra e pode ser criado sem serviços.
   O número comercial (`Contract.reference`) é texto opcional de até 100 caracteres e pode repetir entre obras. Na mesma obra da empresa, uma nova referência deve ser distinta, ignorando espaços nas pontas e maiúsculas/minúsculas e preservando pontuação. Duplicidades legadas podem permanecer quando o número é mantido. Cadastro, edição e aprovação validam a regra sob bloqueio transacional da obra. O campo `contractIdentifier` é independente.
5. Um serviço pertence a um único contrato e possui status próprio.
6. Modelo de serviço reutilizado cria uma cópia editável; a edição da cópia não altera o modelo nem outros contratos.
7. Desconto de serviço, fixo ou percentual, altera o valor líquido daquele serviço e, por consequência, o subtotal dos serviços do contrato.
8. Desconto de contrato, fixo ou percentual, reduz somente o valor líquido do contrato. Ele não altera os valores individuais dos serviços.
9. O valor líquido do contrato é o limite total de faturamento e emissão de NF.
10. O fluxo legado mantém ajuste contratual negativo sem serviço. No novo fluxo líquido F05-21, o desconto ainda não consumido é distribuído pelo backend sobre capacidades ainda livres, sem linha negativa no request nem alteração dos preços/aceites. Base bruta e desconto aplicados ficam congelados em cada alocação positiva; NF e recebível utilizam somente o líquido. A convivência não pode duplicar desconto nem reatribuir compromissos antigos.
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

F05-32 validada localmente em 28/10/2026: fluxo explícito de recebimento com opções oficiais paginadas `GET /receivables/{receivableId}/invoice-settlement-options` e comando `POST /receivables/{receivableId}/invoice-settlements`. Tenant confiável, INVOICES.READ adicional, alçada CREATE de RECEIVABLES, quantias manuais por item, moeda imutável, soma exata, limites conjuntos por título/NF/item, data UTC não futura, intenção idempotente, transação/locks e revalidação na aprovação. `202` registra apenas proposta/intenção, sem recebimento, reserva ou alteração de versão/saldo oficial. Linhas imutáveis da V90 preservam NF/item/alocação/composição; estorno copia as mesmas origens negativas. Retenção já registrada limita a NF/título oficial sem divisão entre itens; créditos/renegociação/legado sem atribuição comprovável permanecem indisponíveis até conciliação. `receiptPosition` de NF usa somente vínculos comprováveis e permanece null/indisponível quando faltar prova ou permissão. O comando genérico antigo e seu indicador de disponibilidade continuam bloqueados. 89 testes/11 suítes, Checkstyle e bootJar aprovados; export27037B87... e candidato0E25A12A... preparados. Validação inicial da V90 somente em PostgreSQL temporário. Em29/10/2026, nova autorização humana específica permitiu aplicar somente V90 por Flyway target90 e publicar o JAR final0E25A12A... em8081; checksums V1–V89 válidos, contagens anteriores preservadas, novo ledger vazio, seeds/Flyway automático off, OpenAPI vivo200/componentes e caminhos compartilhados idênticos ao export27037B87...,401/correlationId anônimos. Sem operação financeira real. [Provas e recuperação](F05-32-LOCAL-ACTIVATION-2026-10-29.md). [Contrato, provas e plano de ativação separado](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md).

O controle de faturamento é exclusivamente contratual. Uma obra consolida a receita dos seus contratos, mas não possui parcela, saldo faturável ou NF próprios.

F05-27/FRONT-F08-19 concluída e validada em 26/10/2026: cobrança por serviço, com capacidade utilizável completa/versionada antes de paginar serviços, cobertura e capacidade bloqueada separadas. O usuário confirmou **base inicial primeiro, depois composições por data oficial de aceite crescente**; empates por IDs imutáveis de medição/versão/composição em ordem lexicográfica. Sem consumo proporcional nem reatribuição histórica. GET `billing-control/contracts/{contractId}/net-capacity/services` e POST `billing-installments/net/by-service` permitem todo disponível ou parcial com resolução exata no backend. Preservam desconto livre F05-21, origem até NF/título/baixa/estorno, intenção idempotente, lock/versão, alçada, `202` sem reserva e revalidação/409 na aprovação; todo disponível não se expande silenciosamente. Legado por origem permanece igual. [Contrato e estado das provas](FRONTEND-SERVICE-BILLING-AND-TAX.md).

F05-28/FRONT-F08-20 concluída e validada em 26/10/2026: tributo por valor explícito ou base/alíquota informadas, sem inferir base do total da NF, incidência, regime, percentual, isenção, tratamento ou vencimento. O usuário confirmou alíquota positiva com até quatro casas, sem arredondamento de entrada; resultado monetário com duas casas HALF_UP já vigente no domínio/ADR 0003. POST `invoices/{invoiceId}/tax-obligations/preview` confere sem efeito financeiro; POST `/calculated` exige valor confirmado e intenção idempotente, alçada e revalidação/202 sem efeito; GET `/{taxObligationId}/calculation` exige INVOICES.READ e preserva histórico/legado sem base percentual fictícia. V89 aditiva registra cálculo/intenção com FKs/imutabilidade, somente em testes isolados nesta entrega. Três tratamentos, vínculos/saldo, aprovação, tenant e legado preservados; não é motor fiscal nem pagamento/recebimento automático. [Contrato e estado das provas](FRONTEND-SERVICE-BILLING-AND-TAX.md).

Histórico contextual de recebimentos (F05-26 / FRONT-F08-15, 26/10/2026): `GET /api/v1/company/receivables/{receivableId}/settlements` exige somente `RECEIVABLES.READ`, com tenant e cadeia título/contrato validados, página/filtros/sort controlados e preservação dos históricos cancelados/renegociados. Retorna recebimentos positivos e estornos negativos em decimal canônico, moeda do contrato, método, situação persistida, rótulo controlado, instantes UTC e IDs exatos entre original/estorno; não associa por valor/data/nome/serviço compartilhado. Original estornado não é regravado nem ocultado. Observações livres, dados bancários/fiscais, conteúdo de auditoria e atribuições financeiras não são expandidos. Consulta não altera saldo/atraso, versões ou ledger, e `202` não vira lançamento. `balance` permanece autoridade financeira, sem soma no cliente; a futura seleção para POST de estorno usa o ID opaco do original sem conceder alçada por leitura. [Contrato, validação e export F05-26](FRONTEND-RECEIVABLE-SETTLEMENT-HISTORY.md); comandos e publicação permanecem separados.

Leitura de recebíveis para FRONT-F08-11 (F05-25, 26/10/2026): `GET /api/v1/company/receivables` e `GET /api/v1/company/receivables/{receivableId}` exigem somente `RECEIVABLES.READ`, com tenant da sessão. São consultas sem efeitos financeiros/auditoria de alteração, com filtros comerciais literais/página/sort controlados, moeda oficial do contrato, valor de face decimal canônico e referências humanas mínimas de título, parcela, contrato, obra e cliente. Essa projeção não autoriza acesso aos módulos relacionados nem exige `BILLING.READ`. Inclui títulos cancelados/renegociados e sua linhagem exata, sem somá-los como nova receita e sem apresentar propostas `202` como registros. `status` é a situação persistida, sem recálculo de atraso; saldo e situação financeira atuais continuam exclusivamente em `GET /receivables/{receivableId}/balance`. Sem tributos, conteúdo de medição ou histórico de baixas. [Contrato, validação e export F05-25](FRONTEND-RECEIVABLE-READ.md); publicação da API é separada.

Leitura de NFs para FRONT-F08-10 (F05-22, 25/10/2026): `GET /api/v1/company/invoices` e `GET /api/v1/company/invoices/{invoiceId}` usam `INVOICES.READ` e tenant da sessão. A lista é paginada com filtros/ordenação controlados por identificação comercial e contexto contratual; o detalhe preserva itens, alocações/origens exatas, valores e histórico cancelado. Dinheiro é decimal canônico em texto e moeda é a do contrato. GETs não registram recebimento nem alteram saldos, estado ou auditoria financeira e não expõem chave de acesso fiscal, conteúdo/anexo ou storage. Anexos e tributos são tratados separadamente em F05-23 e F05-24. Estado da validação, export e contrato em [guia F05-22](FRONTEND-INVOICE-READ.md). A disponibilidade na instância depende de publicação separada.

Anexos de NF possuem recorte próprio F05-23: `GET /api/v1/company/invoices/{invoiceId}/attachments` lista somente metadados paginados com `INVOICES.READ`, sem acessar storage; `GET /api/v1/company/invoices/{invoiceId}/attachments/{attachmentId}/download` exige também `INVOICES.EXPORT` e devolve o PDF/XML existente. Validar tenant e cadeia NF-anexo-contrato antes do storage, incluindo NF cancelada, sem alterar valores/estado financeiro. Download verifica tamanho/hash, registra apenas auditoria segura de exportação e usa filename sanitizado, MIME oficial, `no-store` e `nosniff`, sem URL pública ou redirecionamento. Arquivo segue o limite atual de 10 MiB e a porta existente de leitura de bytes; listagem não materializa arquivos. POST de upload permanece inalterado. Contrato, status de validação e limitações em [guia F05-23](FRONTEND-INVOICE-ATTACHMENTS.md); publicação é separada.

Consulta histórica de tributos da NF (F05-24, 26/10/2026): `GET /api/v1/company/invoices/{invoiceId}/tax-obligations` exige `INVOICES.READ`, tenant da sessão e cadeia coerente de NF, contrato, tributo e eventual recebível. Retorna página com identificação/descrição do tributo, tratamento, situação, vencimento opcional, valor decimal canônico e moeda oficial do contrato. Situação/tratamento são filtros exatos opcionais; omitidos preservam registrados e cancelados, inclusive NF cancelada. O DTO não expõe referências/detalhes de recebíveis ou contas a pagar e não infere situação de pagamento. A leitura não cria Payable, reaplica retenção, deduz valor, gera baixa ou altera auditoria/estado financeiro. POST tributário e regras de domínio permanecem iguais; proposta `202` não vira tributo oficial. [Contrato, estado da validação e export F05-24](FRONTEND-INVOICE-TAXES.md). Não implementa motor fiscal ou relatório de margem; publicação posterior é separada.

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

Esclarecimento de produto confirmado em 26/10/2026: uma cobrança/parcela indevida pode ser cancelada pelo fluxo existente, com justificativa, tenant, alçada/aprovação e auditoria, desde que respeite os bloqueios por NF registrada, recebimento vigente, renegociação e demais vínculos oficiais. O cancelamento não apaga registros e uma proposta `202` não cancela o título nem libera capacidade antes da aprovação. Isso não equivale a cancelar a cobrança porque o cliente ainda não pagou uma NF: registrar NF não registra recebimento; o cancelamento permitido dessa NF mantém a cobrança/título e a dívida em aberto, reavalia seus efeitos tributários oficiais e permite registrar outra NF sobre a capacidade documental liberada, sem duplicar dívida nem consumir novamente o saldo de faturamento. O sistema registra o estado documental interno, sem executar cancelamento fiscal externo.

Correção F05-36 de anexos e guarda documental, publicada somente em localhost:8081 em 28/10/2026 após nova autorização humana: upload devolve os oito metadados contratuais completos, incluindo `createdAt` UTC gerado na persistência antes da projeção imutável. Cancelar NF exige origem íntegra item/alocação/parcela/título e ausência comprovável de recebimentos vigentes ou tributos pagos. Enquanto F05-32 não fornece vínculo manual exato por NF/item, título compartilhado com dinheiro recebido bloqueia conservadoramente cada NF relacionada, sem atribuir esse dinheiro a uma delas. Histórico incompleto, renegociação/crédito ou estorno inconsistente exige conciliação. Validar antes de solicitar e novamente na aprovação/aplicação; `202` não altera nem versões/datas oficiais. Não criar estorno automático nem permitir substituição de NF recebida por esse fluxo. 40 testes/5 suítes, Checkstyle e bootJar aprovados; pacote FCEEA62E... comparado ao anterior e hash ativo conferido. OpenAPI vivo 200 com todos os caminhos compartilhados/componentes idênticos ao export 976E9070...; 401 anônimo, Flyway/seeds false, V89/rede/volume preservados e container anterior disponível para retorno. Sem operação financeira real ou liberação de novas baixas. [Contrato, diagnóstico, testes e publicação](FRONTEND-INVOICE-UPLOAD-AND-CANCELLATION-SAFETY.md).

O condicionamento manual da baixa à NF/item, sem rateio automático, foi entregue para novos vínculos comprováveis pela F05-32, ativada somente localmente em29/10/2026 sob autorização humana específica para V90/API8081/configuração local do frontend. Conciliação histórica, créditos/renegociação sem origem suficiente e substituição de NF com recebimento verdadeiro continuam fluxos separados e conservadoramente bloqueados. A contenção do POST genérico permanece vigente. Não orientar estorno fictício de recebimento verdadeiro para trocar documento; estorno contábil justificado de uma baixa registrada por engano é fato distinto de devolução bancária. [Contrato e limites](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md), [ativação verificada](F05-32-LOCAL-ACTIVATION-2026-10-29.md).

Recorte de proteção confirmado em 26/10/2026, validado/publicado localmente em 27/10/2026 (F05-29 a F05-31): novas baixas rejeitam `receivedOn` posterior ao dia atual UTC do Clock oficial, com violação de campo segura em `400`, inclusive solicitação/aprovação/aplicação. O saldo expõe `maxReceivedOn`. Enquanto o vínculo manual exato por NF/item não existir, a contenção autorizada bloqueia todos os novos recebimentos, mesmo com NF, com `409 BUSINESS_RULE_VIOLATION`/`SETTLEMENT_INVOICE_LINK_REQUIRED`, sem `202`, reserva ou efeito financeiro; propostas antigas são bloqueadas na aplicação. O saldo publica `settlementCreationAvailable=false` e o motivo controlado, com schema string/null sem enum que exclua null. Consultas e estornos históricos autorizados permanecem disponíveis, sem alterar baixas futuras já gravadas ou fabricar vínculo documental. Build testado ativado somente em 8081, Flyway desabilitado, schema real V89 preservado; não inclui migration ou fluxo manual completo. [Provas e limitações](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md).

No controle de faturamento por contrato, lista e detalhe acrescentam `receivedAmountAvailable` e `receivedAmount`: soma oficial dos lançamentos de recebimento/estorno `CONFIRMED`, sem contar retenções, créditos, NFs ou valor de face de títulos renegociados como dinheiro. Exige `RECEIVABLES.READ` adicional a `BILLING.READ`; sem autorização retorna false/null, não zero fictício, e não consulta o ledger. Preserva a moeda do contrato e histórico por lançamento, sem duplicar títulos descendentes nem somar páginas no cliente. [Contrato, estado da validação e publicação](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md). Este recorte não cria migration nem entrega o fluxo completo por NF/InvoiceItem.

Fechamento de integração em 27/10/2026: FRONT-F08-23/24 consumiram o snapshot definitivo de F05-29/30/31, com tipos gerados, recebido oficial minimizado por permissão/cache, limite UTC do servidor e contenção fechada quando disponibilidade/motivo não forem reconhecidos. O [guia e suas referências frontend](FRONTEND-RECEIPT-DATE-AND-BILLING-CASH.md) distinguem os 185 testes/13 arquivos e 41 E2E de produção com API interceptada das provas backend; não houve jornada autenticada contra dados reais. FRONT-F08-21 e F05-32 permanecem pendentes para o vínculo manual completo por NF/item e políticas de conciliação/substituição. Não inferir nova autorização de migrations, publicação ou operações reais desse fechamento visual/contratual.

Recorte de consulta autorizado, validado e publicado somente em 8081 em 27/10/2026 — F05-33/F05-34: lista de recebíveis aceita `financialView=OUTSTANDING|RECEIVED|ALL` antes de count/paginação, mantendo `ALL` omitido por compatibilidade; UI operacional pede `OUTSTANDING`. `financialPosition` separa dinheiro confirmado líquido de estornos, retenção, crédito, líquido e saldo devido. Cancelados/renegociados ficam no histórico; encerramento só por ajuste não é dinheiro recebido. NF recebe `receiptPosition` separada do status documental, com permissão financeira adicional: enquanto F05-32 não fornece vínculo manual NF/item, retorna indisponível/motivo e valores null, nunca recebimento inferido do contrato/título. F05-34 permanece parcial quanto à situação paga/parcial comprovável. 45 testes/9 suítes, Checkstyle e bootJar aprovados; export executável e schemas vivos conferidos. Sem migration ou liberação de baixa; Flyway/seeds off, V89 e storage preservados. [Contrato, hash, provas e limitações](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md).

Fechamento FRONT-F08-25/26 em 27/10/2026: frontend confirmou tipos regenerados do export `0AB14484...`, 126 testes/8 arquivos, 31 testes da política/build/scanner e 34 E2E de artefato com API interceptada, além de leituras públicas do OpenAPI/401. Evidências reportadas, consultadas e distinguidas dos testes backend no [guia de integração](FRONTEND-RECEIVABLE-POSITION-AND-INVOICE-RECEIPTS.md). F08-25 concluída; F08-26/F05-34 permanecem parciais quanto à comprovação paga/parcial/não paga por NF. Não é homologação autenticada real nem autorização de novas operações/publicações. Pedido extra de valor no cadastro de contrato retirado pelo usuário, sem alteração desse domínio.

Navegação por contrato nas NFs — F05-35 concluída/publicada somente em 8081 em 27/10/2026: índice paginado `GET /api/v1/company/invoices/contracts` e contexto exato `GET /api/v1/company/invoices/contracts/{contractId}` exigem somente INVOICES.READ e tenant confiável, limitados aos contratos representados por NFs oficiais inclusive canceladas. Publicam apenas ID/nome/referência/identificador comercial, sem somas financeiras. Busca literal e paginação/count são do servidor; contexto sobrevive à recarga/filtro vazio sem acesso ao contrato completo. Lista de notas continua `GET /invoices?contractId` com ID exato, nunca agrupamento local ou nome como chave. Sem mudanças de cadastro, cancelamento ou ledger. Nova autorização de publicação local executada após 30 testes/5 suítes, Checkstyle e bootJar aprovados; export A300B8C9... e contrato vivo conferidos. Sem migration real ou liberação de baixas; schema V89/storage preservados e retorno controlado disponível. [Contrato, hashes e provas](FRONTEND-INVOICE-CONTRACT-INDEX.md).

Fechamento de integração FRONT-F08-27/28/29 em 27/10/2026: export A300B8C9... consumido/tipos gerados; navegação por contrato exato, descoberta do cancelamento existente e remoção somente visual da coluna de moeda integradas no frontend. Relatório consultado registra 114 testes/8 arquivos, 31 E2E distintos com API interceptada, lint/tipagem/build/scanner/31 testes de política e leitura pública do OpenAPI/401, sem homologação autenticada real ou operações financeiras. [Referência e limites](FRONTEND-INVOICE-CONTRACT-INDEX.md). Não amplia F05-35 para novos comandos/cancelamentos, ledger, migrations ou publicações; F05-32 permanece pendente.

Transições financeiras obrigatórias:

| Ação | Efeito obrigatório |
|---|---|
| Cancelar parcela sem NF/recebimento | Cancela a alocação e devolve seu valor líquido ao saldo faturável do contrato. |
| Cancelamento permitido de NF registrada | Cancela seus itens e efeitos tributários/vínculos conforme o fluxo oficial, preservando cobrança, título e dívida. Libera somente a capacidade documental correspondente para outra NF; não libera o saldo faturável comprometido pelas alocações. |
| Estornar recebimento | Cria baixa reversa, reabre o saldo da conta a receber e atualiza o fluxo de caixa. |
| Cancelar pagamento | Cria baixa reversa, reabre o saldo da conta a pagar e atualiza o fluxo de caixa. |
| Renegociar título | Preserva o título anterior como renegociado e gera novos títulos vinculados à origem. |

Histórico F05-37, anterior à autorização proporcional do fim de29/10/2026 (regra atual no complemento abaixo e no [guia](FRONTEND-INVOICE-ITEM-WITHHOLDING.md)): o recebimento TOTAL pode ter preenchimento proposto pelo backend a partir dos serviços/valores da cobrança e de suas origens documentadas NF/item/alocação/composição. Só publicar linhas quando a solução dos limites oficiais for única, descontando recebimentos exatos anteriores; nunca escolher proporção/FIFO, distribuir retenção ambígua ou somar tetos individuais. Proposta não reserva, não cria baixa nem altera202; confirmação e aprovação revalidam tudo. Parciais permanecem manuais. API8081 recebeu este aditivo em29/10 sob nova autorização humana, sem migrations; 22 testes/2suítes e Checkstyle aprovados; export executável7BF8E221.../147paths/257schemas disponível, JAR candidato B971C83C... preservado/comparado ao ativo0E25..., sem alterar bibliotecas/configuração/migrations; [pacote e retorno F05-32 compatível](F05-37-CANDIDATE-AND-ROLLBACK.md), publicação somente local comprovada com JAR B971C83C..., OpenAPI vivo200 compatível com7BF8E221...,401 anônimos, schema90/ambiente/volume e seeds/Flywayfalse preservados, sem operações financeiras reais. [Registro atual](F05-37-LOCAL-ACTIVATION-2026-10-29.md). [Contrato, exemplos e única decisão adicional nos casos ambíguos](FRONTEND-TOTAL-INVOICE-SETTLEMENT-PROPOSAL.md).

### 5.4.1 Pré-preenchimento XML e cobertura documental por serviços

Decisão humana de06/11/2026: novos registros de NF selecionam um ou mais serviços completos daquela cobrança, sem dividir livremente o valor já cobrado de um serviço. Valor contratual integral é outra referência. F05-39/41 reúnem todas as origens exatas de base/medição por empresa/contrato/cobrança/serviço em identidade projetada estável, sem nova tabela/migration. A consulta entrega valores documentados/faltantes oficiais, grupos e origens; a criação manual e a aprovação revalidam conjunto/valores sob lock contratual.202 não produz cobertura. Não agrupar por título ou serviço isolado entre cobranças, nem autorizar subgrupos inventados pelo cliente.

Uma NF pode abranger grupos de várias cobranças do mesmo contrato. Cada cobrança soma somente os seus InvoiceItems por BillingAllocation, sem replicar total da NF. Legado fracionado permanece legível e exige conferência para novo complemento; nenhuma origem, imposto, nota ou recebimento é reescrito. Recebimento parcial continua permitido. O total documental confirmado pode ser enviado como documentTotalAmount e é comparado com os grupos oficiais, inclusive na aprovação. Dinheiro permanece string decimal na API; o frontend não soma/rateia valores. Compatibilidade do cadastro manual anterior não equivale a vínculo fiscal comprovado do arquivo.

F05-40 oferece prévia autenticada/stateless do XML nacional1.01, com valores e proveniência, dados da NF separados da DPS, base/alíquota/ISS/retenções/líquido, checagem mínima de partes e revisão humana. Ausência não é zero/não retido; retenção agregada incompleta não é distribuída, e percentual informativo não gera obrigação. Código de retenção não concede tratamento oficial automaticamente. Não substituir data/série da NF pelas da DPS. Referência de substituição é aviso e não impede ver candidatos, mas nunca cancela/substitui histórico. Totais/moeda/partes ou estruturas fiscais não suportadas são sinalizados explicitamente.

Extração usa JDK/JAXP/XPath e BigDecimal, com a validação estrutural segura existente, sem DTD/XXE/XInclude/rede, limite10MiB e limites de estrutura. Não valida XSD, assinatura, autenticidade/autoridade/situação fiscal nem antimalware; isso é explícito no contrato. O ZIP oficial de XSD retornou403 e não foi contornado. Preview não cria NF/anexo/imposto/Payable/baixa/proposta/reserva; só auditoria mínima sem conteúdo, valores, documentos ou chave. Cálculo tributário explícito e idempotência já existentes podem ser usados somente após conferência, sem inventar incidência ou reaplicar retenção. Modelo privado não foi movido ou usado como fixture.

[Guia efetivo](FRONTEND-INVOICE-XML-AND-COVERAGE.md), [provas/export/pacote](F05-39-41-VALIDATION-2026-11-06.md). Registro 201/202, anexação e tributos são etapas distintas. F05-39 a F05-41 foram publicados isoladamente em localhost:8081, com as rotas de XML e cobertura verificadas. O pacote de publicação preservou o histórico Flyway.

**F05-42, evidência fiscal estruturada.** A empresa pode confirmar explicitamente os fatos extraídos de um XML privado já anexado a uma NF oficial. A confirmação valida empresa, vínculo exato do anexo, integridade do arquivo, identidade fiscal, partes, moeda e valor comercial. O snapshot é imutável e idempotente para a mesma origem. A NF e seus itens preservam a relação com as alocações, cobranças e serviços. Emissão cadastrada, processamento do documento e dados de DPS permanecem separados. Ausência é desconhecimento. Bases, alíquotas, retenções e percentuais informativos são dados documentais com proveniência. Somente lançamentos oficiais sustentam obrigação, caixa ou resultado. A confirmação não altera saldo, não rateia valores e não cria tributo, crédito, despesa ou baixa. O fluxo atômico de cadastro NF+XML+decisões tributárias permanece pendente. [Contrato da evidência fiscal](FRONTEND-INVOICE-XML-FISCAL-SNAPSHOT.md).

**F05-43, controle documental por obra.** A posição da obra consolida o líquido oficial dos contratos e as NFs registradas de cada contrato, com saldo documental e totais por moeda independentes da página. NF cancelada permanece no histórico contratual. O PDF exibe título e referência da obra, uma tabela de contratos com nome, número e valores e o resumo total por moeda. Serviços, NFs e cobranças detalhadas permanecem no relatório contratual. O acesso combina permissão de obra, faturamento e NF. A consolidação respeita D-17. [Contrato da posição e do PDF](FRONTEND-WORK-BILLING-CONTROL.md).

**F05-44, anexo de NF.** O upload oficial aceita apenas PDF e um novo arquivo somente se a NF ainda não tiver PDF. A verificação e a gravação de metadados são serializadas pelo bloqueio da NF. PDFs duplicados e XMLs legados mantêm listagem, download e evidência fiscal quando autorizados. Um XML legado não ocupa a vaga de PDF. A prévia XML continua em rota própria. A entrada de novos fatos fiscais tem fluxo próprio planejado. [Contrato dos anexos](FRONTEND-INVOICE-ATTACHMENTS.md).

### 5.5 Contas a pagar, custos, despesas e lucro

O primeiro recorte de caixa realizado (F06-12 e recebimentos da F06-10) consolida movimentos oficiais da empresa e da obra por período e moeda. O realizado usa baixas confirmadas de recebíveis e pagamentos registrados com seus estornos e datas efetivas. A empresa distingue gastos WORK e COMPANY. A obra inclui os gastos WORK vinculados, inclusive sem contrato. A leitura explicita cobertura dos movimentos registrados e indisponibilidade por permissão. [Contrato do realizado](FRONTEND-REALIZED-CASH.md).

O acumulado por obra começa no primeiro movimento efetivo autorizado e vai até hoje UTC, independentemente das datas cadastrais ou de conclusão da obra. O realizado por obra expõe o resultado de caixa como percentual das entradas líquidas registradas na mesma moeda e período, quando há acesso às duas fontes e entradas positivas. [Contrato do acumulado e do percentual](FRONTEND-REALIZED-CASH.md).

O previsto por vencimento usa a posição atual dos saldos residuais oficiais, separa vencidos dos próximos vencimentos, explicita corte UTC e horizonte e publica cobertura por moeda, escopo e permissão. Títulos com posição inconsistente não geram saldo fictício. [Contrato do previsto](FRONTEND-CASH-DUE.md).

A comparação de pagamentos usa os totais registrados líquidos de estornos, pela data efetiva de cada movimento, no intervalo escolhido e no imediatamente anterior de igual duração. Preserva moeda e escopo, inclui títulos sem fornecedor ou categoria e explicita a cobertura de cada intervalo. Sem registros em um período, diferença e percentual ficam indisponíveis. Com base anterior zero ou negativa, somente a diferença pode ser calculada quando ambos têm registros. [Contrato da comparação](FRONTEND-CASH-PAYMENTS-COMPARISON.md).

A comparação por categoria usa a mesma base e agrupa pela identidade da categoria do título, inclusive quando inativa. Títulos sem categoria formam um grupo separado. A leitura exige a permissão do catálogo de categorias além das permissões da comparação geral. As categorias iniciais para novas empresas e a adesão de empresas existentes estão em proposta. [Contrato por categoria e proposta de catálogo](FRONTEND-CASH-PAYMENTS-BY-CATEGORY.md).

F06-07 e F06-09 registram pagamentos parciais ou totais já efetuados, em moeda e escopo herdados do título individual. A baixa registra meio e data efetivos. O estorno integral registra justificativa, data e vínculo ao lançamento original por uma linha negativa imutável. A aprovação revalida título, saldo, data e elegibilidade sob bloqueio. O `202` preserva o saldo oficial e não cria lançamento. A leitura informa o valor original, o pago líquido de estornos e os saldos em aberto e vencido por título, com data de corte UTC. Quando o ledger não sustenta o status histórico, os valores derivados ficam indisponíveis. O PostgreSQL local já tem a V95 original. A V96 aditiva preserva a justificativa e permite gravar a solicitação e seu vínculo na mesma transação. Em 13/11/2026, o build compatível foi publicado e verificado em 8081 sobre a V96 já aplicada. [Contrato de integração](FRONTEND-PAYABLE-SETTLEMENTS.md).

O cadastro base F06-01 mantém Supplier e FinancialCategory por empresa, com alçada/aprovação, auditoria e inativação lógica. Fornecedor informa nome e identificador documental opcional normalizado; não há unicidade global nem união pelo documento. Categoria possui código único por tenant, nome, descrição opcional e classificação WORK_COST, WORK_EXPENSE, COMPANY_EXPENSE ou TAX. Código/classificação são imutáveis para não reinterpretar despesas futuras; nome/descrição podem ser editados com versão otimista. A categoria não decide o escopo financeiro de um título. V86, 35 testes/14 suítes, Checkstyle e OpenAPI 3.1 (131 paths/215 schemas) validados naquele recorte, sem deploy/reinício/push ou aplicação manual ao banco real. API e contrato no [guia de cadastros de despesa](FRONTEND-PAYABLE-CATALOGS.md); o lançamento operacional posterior está descrito abaixo e pagamentos permanecem nas tarefas seguintes.

F06-02 a F06-06 implementadas/validadas em 22/10/2026: `POST /api/v1/company/payables` cria 1..100 títulos individuais explícitos em um grupo financeiro gerado pelo servidor, com escopo/vínculos/moeda comuns e valor/vencimento próprios. Não há parcelamento, divisão, juros ou rateio automáticos. `operationId` garante idempotência transacional por empresa; HTTP 201 aplicado, 200 replay oficial, 202 somente ChangeRequest, sem título/grupo oficial antecipado. `GET /payables` e `GET /payables/{payableId}` exigem PAYABLES.READ, preservam tenant e fornecem referências humanas mínimas de obra/contrato/fornecedor/categoria. Fornecedor e categoria opcionais precisam estar ativos no tenant no lançamento e na aprovação. Alçada PAYABLES não é concedida pelas permissões de catálogo. WORK aceita EPI sem contrato e, quando houver contrato, exige a mesma obra/moeda; COMPANY proíbe ambos os vínculos. Categoria não infere escopo.

Moeda de gasto com contrato é herdada, ou validada se explicitada; sem contrato, admite BRL/USD/EUR com padrão BRL, persistida e imutável, sem câmbio nem soma multimoeda. Valores são strings decimais canônicas positivas de duas casas no request/response, convertidos diretamente a BigDecimal sem arredondamento operacional. Títulos usam versão otimista; referência de origem/escopo/moeda e grupos não podem ser alterados ou fisicamente excluídos. V87 aditiva atribui grupo próprio e moeda da cadeia a cada título legado, preservando IDs, valores, status, datas e origem tributária. Leitura não persiste atraso nem baixa fictícia: `overdue` é derivado na data UTC e o status permanece armazenado. Baixas, resultado de obra e fluxo de caixa continuam nas F06-07/F06-09 a F06-12; a segregação COMPANY é preservada, sem antecipar essas projeções. 69 testes/18 suítes e Checkstyle sem erros; OpenAPI 3.1 exportado com 133 paths/221 schemas. [Contrato, hash e provas](FRONTEND-PAYABLES.md). Nenhum deploy/reinício/push ou migration no banco real foi executado nesta entrega.

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

O resultado de uma obra e o resultado corporativo são indicadores separados. Despesas/tributos COMPANY entram somente no corporativo, sem rateio implícito nas obras. Ainda não existe relatório oficial de lucro/margem implementado: critérios de período, reconhecimento, completude e eventual percentual dependem de definição antes da entrega. Retenção não é universalmente custo definitivo; antecipação/crédito/obrigação/pagamento precisam de classificação por tributo, operação, regime e competência. A mesma origem TaxObligation/Payable não pode ser deduzida duas vezes. O recorte de privacidade de 22/10/2026 não cria motor fiscal nem altera as fórmulas/invariantes acima.

### 5.5.1 Planejamento futuro — locações e lembretes de contas de aluguel

**Somente planejamento, registrado em 25/10/2026.** O lançamento de contas a pagar existente continua recebendo títulos explícitos. Não há implementação de locação, cálculo recorrente, pagamento ou lembrete nesta atualização; nenhuma rota, DTO, enum ou migration é publicada para esses recursos.

As modalidades comerciais confirmadas são distintas:

| Modalidade futura | Regra confirmada | Definições ainda necessárias |
|---|---|---|
| Locação mensal fixa | Há aluguéis com valor mensal fixo. | Calendário/competência mensal, proporcionalidade, vencimento e fechamento. |
| Locação por diária e quantidade de equipamentos | Contêineres, máquinas de solda, ferramentas e outros equipamentos podem ser alugados por diária e quantidade. A diária de locação considera dias corridos durante a locação, inclusive fins de semana, e não somente dias de utilização. | Inclusão dos dias de entrega/devolução, frações, precisão da quantidade, alterações de quantidade e devoluções parciais. |
| Serviço por hora / H.H. | Considera horas efetivamente trabalhadas; execução, quantidade aceita/faturável, custo, preço e ponto permanecem fatos separados. | Critérios comerciais de apontamento, aceite e integração com ContractService, conforme seção 5.7/Fase 12. |
| Serviço por diária | Modalidade comercial expressamente solicitada, distinta da diária corrida de aluguel e do serviço por hora. | O que caracteriza diária integral/parcial, apontamento/aceite e horas adicionais. Não presumir oito horas por diária nem cobrança automática de fins de semana. |

Complemento confirmado em 25/10/2026: o cadastro planejado de aluguel/serviço e suas contas correspondentes devem contemplar a **forma prevista de cobrança/pagamento**, incluindo explicitamente **boleto** e outras formas a definir. Natureza, unidade e periodicidade comercial (mensal, diária de equipamento, diária de serviço ou hora) não determinam essa forma. O meio efetivamente utilizado em um pagamento é outro fato: como orientação para o desenho futuro, considerar registrá-lo por baixa, sem afirmar que esse contrato de Payable já exista.

Permanecem pendentes o catálogo completo e a obrigatoriedade da forma prevista, sua herança/alteração no título, instruções e anexos, além das regras para pagamentos parciais por meios distintos. Informar boleto não significa emitir/gerar boleto, integrar banco/gateway, pagar automaticamente ou confirmar pagamento. Não reutilizar o enum de recebimentos de clientes como contrato de contas a pagar nem publicar campos/DTOs/tipos fictícios antes da implementação autorizada.

O futuro controle de locação deve preservar a origem das obrigações e seu histórico, com tenant, permissões, alçada/aprovação, concorrência e auditoria. A classificação de gasto continua explícita: WORK exige obra e admite contrato opcional da mesma obra; COMPANY não possui obra/contrato e não é rateado implicitamente. Locação não cria uma hierarquia paralela de receita nem altera as regras atuais de faturamento por Contract → ContractService.

Encerrar a locação não quita, cancela ou apaga títulos anteriores. Pagamentos parciais/totais e estornos seguem os fluxos oficiais próprios. Geração futura de títulos depende de regras comerciais aprovadas, com origem e idempotência para impedir duplicação por competência/período; uma proposta pendente não produz obrigação oficial, saldo ou pagamento antecipado. Alterações/devoluções exigem fechamento explícito, sem recalcular silenciosamente títulos históricos.

A empresa deseja lembretes antes do vencimento de contas de aluguel e enquanto a obrigação permanecer pendente. Antes da implementação, definir canais, destinatários autorizados, antecedências, cadência, fuso, critérios de pendência e suspensão dos avisos conforme a situação financeira oficial. O desenho deverá prever idempotência, revalidação da obrigação antes do envio e auditoria segura. Esse requisito não autoriza ativar automação ou enviar notificações agora; não reutilizar automaticamente as regras de inadimplência da assinatura SaaS para obrigações da empresa com fornecedores.

Correspondência do planejamento do frontend: seção 6.7 do SDD do frontend e FRONT-F09-16. A página de lançamento de contas a pagar FRONT-F09-15 usa o contrato Payable existente; sua integração não significa que locação, recorrência ou lembretes estejam implementados. Detalhamento e critérios de aceite futuros: F06-15 a F06-19 e F12-12 no backlog do backend.

### 5.5.2 Contato do fornecedor e referência para pagamento manual

O fornecedor possui contato principal opcional: nome (120), telefone (40) e e-mail válido (254). Não é cadastro de funcionário nem catálogo de favorecidos e não preenche automaticamente instruções financeiras.

Cada título Payable pode ter uma instrução oficial BOLETO, PIX ou BANK_TRANSFER, com revisões imutáveis e versão independente do título. Trata-se de informação para o responsável pagar fora do sistema: não emite boleto/Pix, não descobre beneficiário, não executa transação e não confirma pagamento. Criação do título e gravação da instrução são operações distintas; um lote não distribui instruções automaticamente. O escopo WORK/COMPANY, a identidade, moeda, valor, vencimento, status e pagamentos do Payable permanecem intactos.

Boleto admite uma linha digitável ou barras, com PDF privado opcional; também é permitido registrar o tipo sem códigos e anexar somente o PDF posteriormente. Sem código e sem PDF a referência ainda não está utilizável. Pix armazena chave e tipo ou texto copia-e-cola fornecido pelo usuário, sem geração ou promessa de validação bancária. Transferência registra banco, agência, conta, dígitos separados, tipo e titular informados. Não inferir titular do fornecedor/funcionário. Campos de tipos distintos não podem coexistir.

Metadados não expõem conta, chave, código, payload, nome de titular, hash nem storageKey. Leitura completa/download e escrita exigem PAYABLES.READ + PAYABLES.VIEW_PAYMENT_DETAILS; escrita segue adicionalmente alçada UPDATE. Aprovação exige também as permissões de leitura sensível, revalida versão e título aberto e proíbe autoaprovação. O usuário sem VIEW não edita com formulário vazio. PUT é substituição integral, sem transportar PDF anterior implicitamente. Upload cria nova revisão preservando o conteúdo atual.

A aplicação usa transação/lock do título, versão esperada e unicidade por tenant/título/versão. 202 só cria proposta protegida; não altera título, instrução ou documento oficial. JSON, propostas e PDFs são cifrados com AES-GCM e chave própria, independente de JWT/RH, com contexto tenant/título. Auditoria registra ator, origem, data e referências/indicadores, sem dados para pagamento. PDF tem validação estrutural PDFBox, limite de 10 MiB e download privado autenticado com no-store; não há URL pública nem antimalware/autenticidade bancária implementados neste recorte. Propostas pendentes só são reveladas ao solicitante ou aprovador com READ+VIEW e dentro do mesmo título/tenant.

Implementação: F06-20/21; [contrato para o frontend](FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md). V92 é aditiva; criação e testes em PostgreSQL temporário não autorizam aplicação no banco persistente. Publicação local do build novo exige V92 e chave configuradas com autorização específica. Locações, recorrência, notificações e PayableSettlement continuam nos respectivos itens pendentes e não foram antecipados.

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

### 5.7 Planejamento futuro — H.H., serviços por diária, lotação e apontamento por obra

**Somente planejamento, autorizado em 22/10/2026. Não há implementação, migration, enum, DTO ou endpoint de H.H./ponto nesta entrega.** A Fase 12 do backlog delimita dependências e aceite antes de publicar contratos executáveis.

- H.H. (hora de trabalho por pessoa) é unidade comercial de serviço, não salário, folha nem marcação oficial de ponto. Quantidade executada, quantidade aprovada/faturável, preço de venda e custo trabalhista são conceitos separados; BigDecimal, moeda e arredondamento deverão ter regra explícita. Não converter salário mensal em custo/hora nem dia em horas por divisor presumido.
- Complemento de 25/10/2026: há também **serviço por diária**, modalidade comercial própria; serviço não é exclusivamente H.H. Ela não herda a contagem de dias corridos da locação de equipamento (seção 5.5.1). Definir diária integral/parcial, apontamento, aceite e horas adicionais antes de implementar; não presumir oito horas, conversão automática para H.H. ou cobrança de fim de semana. A distinção não modifica a intenção de DSR comercial nem define sua fórmula. Requisitos futuros em F12-12.
- Lotação/alocação operacional associa funcionário/vínculo à obra da mesma empresa por vigência e intervalos. Transferência temporária registra obra de origem/destino, datas, intervalos e retorno, sem sobrescrever histórico. Deve haver uma única posição efetiva no mesmo intervalo para o mesmo funcionário; alocação-base e exceção temporária se resolvem em uma linha temporal oficial, sem contar ambas. Antes da implementação, definir fuso, limites inclusivos/exclusivos, alocação parcial e comportamento de intervalos adjacentes; reforçar a vedação de sobreposição efetiva com validação transacional/constraints e concorrência. Não confundir deslocamento interno com troca de empregador ou trabalho temporário regido por legislação própria.
- Encarregado/secretária são perfis configuráveis, não autorização global. Permissão por operação deve se combinar com obras explicitamente atribuídas, inclusive consulta, pesquisa de funcionário, apontamento, aprovação, exportação e histórico. Não disponibilizar salário, documento ou saúde por necessidade de apontar horas. O recorte operacional por obra não concede acesso ao RH/financeiro completo nem pode vazar outra empresa/obra.
- Apontamento diário registra funcionário, vínculo, obra, dia, intervalos/horas e origem verificável. Quantidades de dias/horas e suas regras não podem gerar contagem dupla. Correções são rastreáveis e sujeitas à alçada/aprovação; uma proposta pendente não altera quantidade oficial. O faturamento continua exclusivamente pelo Contract/ContractService existente da obra, sem nova hierarquia ou título financeiro paralelo; execução, aceite comercial e faturamento não são fatos equivalentes.
- Esclarecimento humano de 22/10/2026: “remunerado” se refere a **DSR**. Intenção comercial: quando todo o período semanal trabalhado do funcionário for H.H. para aquele cliente/tomador, cobrar também DSR no contrato. DSR remunerado na folha e DSR eventualmente cobrado comercialmente são fatos distintos; isso não muda empregador nem transfere obrigação salarial ao tomador e não transforma descanso em horas efetivamente trabalhadas. Continuam pendentes definição de semana/período, elegibilidade comprovada, fórmula/base/valor, semana parcial, mais de um tomador, atestado/feriado, aceite e tratamento no contrato. Não presumir percentual 1/6, valor de um dia, rateio ou qualquer fórmula; validar empregador, categoria, jornada, competência e CCT/ACT com responsável habilitado antes de implementar/cobrar.
- Integração futura com ponto preserva marcações reais/originais e a origem externa. Cadastro/alocação ausente gera pendência de conciliação e alerta, nunca descarte ou bloqueio silencioso de marcação real. Não gerar batidas de escala, “consertar” original ou transformar atestado em horas trabalhadas/faturáveis. Tratamentos, justificativas e ajustes são camadas auditáveis separadas. Falta de marcação não prova falta ao trabalho; presença não basta para faturar.
- Espelho, jornadas/escalas, intervalos, divergências, ausências/atestados e eventual REP/PTRP exigem desenho próprio conforme CLT, Portaria 671 e alterações, tipo de sistema e CCT/ACT territorial/profissional vigentes. Atestados têm acesso/retenção próprios, sem diagnóstico em apontamento, auditoria operacional ou relatório comercial. Não anunciar ponto oficial/conformidade antes de validar requisitos e responsabilidades.

Referências públicas conferidas em 22/10/2026 (não substituem análise da categoria/operação nem certificam implementação): [CLT, arts. 67 e 74](https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452compilado.htm); [MTE — perguntas e respostas da Portaria 671](https://www.gov.br/trabalho-e-emprego/pt-br/assuntos/inspecao-do-trabalho/fiscalizacao-do-trabalho/Perguntas%20e%20Respostas%20REP); [MTE — registro eletrônico de ponto](https://www.gov.br/trabalho-e-emprego/pt-br/assuntos/inspecao-do-trabalho/fiscalizacao-do-trabalho/rep). Antes de implementar, reconferir a redação/efeitos e os instrumentos coletivos aplicáveis; não copiar uma CCT de outra empresa como regra universal.

### 5.7.1 Mínimo priorizado de H.H. manual — planejamento de 26/10/2026

O primeiro recorte é registro manual de execução, não ponto oficial nem folha. O responsável informa funcionário, obra, serviço, dia e horas; a origem manual, autor, instante UTC, vínculo histórico aplicável e cadeia da empresa são preservados. Correção guarda referência ao original, motivo e histórico, com alçada direta ou proposta sem efeito oficial até aprovação. Resumo oficial por funcionário/período/serviço é calculado no backend a partir dos registros efetivos, separado de pendências. Não converte hora apontada em quantidade aceita, faturamento, salário ou pagamento.

Contratos funcionais mínimos futuros, **sem nomes de endpoint, enum ou DTO publicados**:

- seleção/consulta de funcionário por projeção operacional mínima e de obras/serviços autorizados, sempre no tenant e no escopo de obra atribuído;
- registrar, consultar e corrigir execução manual com identidade estável e histórico, sem exclusão física nem troca silenciosa de vínculo; preservar proteção contra repetição/concorrência e alçada/aprovação;
- listagem paginada/filtrável e resumo por funcionário, período, obra e serviço, com critérios temporais/precisão oficiais definidos e sem cálculo de totais pelo frontend;
- identificação explícita de origem manual e da situação de aprovação, sem expor salário, documento ou saúde aos apontadores. Publicar OpenAPI/guia apenas com a implementação efetiva.

O mínimo pode avançar antes de F12-03 (transferências temporárias completas), F12-07/F12-08 (ponto/espelho), F12-11 (DSR) e F12-12 (diária). F12-02 trata do vínculo operacional opcional e da participação válida por obra e data. F12-04 trata da autorização do usuário, concedida separadamente. A identidade histórica do vínculo de trabalho continua necessária, inclusive em readmissão. A detecção de sobreposição exata depende de intervalos efetivamente informados.

Decisões ainda necessárias antes do código: formato/precisão das horas (decimal ou minutos), fuso e limites inclusivos do dia/período, uma ou várias linhas por dia, critérios de duplicidade/acréscimo/correção e sobreposição, serviço obrigatório e sua identidade (se ContractService, validar contrato/obra; se execução avulsa for desejada, definir referência operacional sem inventar contrato), vigência de acesso/alocação e revisão de divergências, quem aponta/confere/aprova e quais quantidades entram em cada resumo. Não presumir jornada legal, oito horas, divisor salarial, preço/custo ou fórmula comercial. DSR permanece sem fórmula aprovada e fora da automação inicial.

### 5.7.2 Diário de Obra — planejamento de 19/11/2026

O Diário de Obra reúne equipe, serviços e atividades, horas planejadas e realizadas quando conhecidas, e ocorrências que expliquem impedimentos ou atrasos na data de execução. É a referência operacional para acompanhar o andamento, conferir desvios e fornecer evidência para apurações futuras. O recorte é de planejamento. Qualquer contrato para fiscalização externa, assinatura, Livro de Ordem, ponto oficial ou aceite comercial exige definição própria.

O procedimento do DNIT registra ocorrências diariamente, inclusive condições que prejudicam a execução, e distingue as anotações da construtora, da supervisora e da fiscalização. O SGIV do DER/MG combina acesso por obra, preenchimento de atividades e ocorrências, mão de obra e revisão. Uma especificação de contratação do Banco Central registra horários da equipe, atividades, recursos e atrasos. Essas referências orientam o desenho operacional do Canteiro. Papéis, assinaturas e exigências de cada contratação dependem do contexto aplicável. [DNIT 097/2025–PRO, §§3.4 e 4 e anexos A/B](https://www.gov.br/dnit/pt-br/assuntos/planejamento-e-pesquisa/ipr/coletanea-de-normas/coletanea-de-normas/procedimento-pro/dnit_097_2025_pro.pdf) · [DER/MG, Diário de Obras, §§1–6](https://portal.der.mg.gov.br/documentacao-deermg/diario/) · [Banco Central, especificações técnicas, item 2.1.3](https://www.bcb.gov.br/Adm/Edital/concorrencia/DEMAP900052025/arq03_DEMAP900052025.pdf).

**Base conferida no estudo anterior à F13-02.** A tabela registra o estado inicial de código e OpenAPI que orientou os recortes. A [implementação do Diário](FRONTEND-API-ENDPOINTS.md#251-diário-de-obra-f13-02) descreve o contrato posterior.

| Área | Disponível como base | Contrato a definir |
|---|---|---|
| Obras, contratos e serviços | `WorkManagementService` consulta a tabela `obra` pelo tenant. `ContractJpaEntity` guarda `workId` e `ContractServiceJpaEntity`, `contractId`. Há datas no cadastro da obra/contrato e identidade de serviço por UUID. | Registro por obra e dia, atividades, equipe por atividade e referência opcional a serviço contratual validada na cadeia. As datas cadastrais não são um plano diário de execução. |
| Funcionários e horas | `Employee` guarda dados do cadastro e do vínculo corrente, com usuário de acesso opcional. `EmployeeListItemResponse` oferece projeção mínima de empresa. | Identidade histórica do vínculo de trabalho (F10-01/F10-02), alocação funcionário–obra com vigência, seleção no contexto do Diário e apontamentos com revisão. O perfil existente não comprova vínculo em uma data histórica. O código e o OpenAPI conferidos não têm contrato de Diário, apontamento, jornada diária planejada ou folha. |
| Autorização | `TenantContext` e `AccessAuthorizationService` fornecem tenant, catálogo de permissões, papéis e alçadas por módulo. `ChangeRequestDecisionService` e `AuditEvent` são a base de aprovação e auditoria. | Escopo e concessão explícita por obra, projeções específicas e checagem de escopo também em aprovação, rejeição, pendências, auditoria e exportação. Permissões atuais de obra/funcionário valem no tenant e não resolvem esse escopo. |
| Financeiro | `BillingAllocation` preserva contrato, serviço quando aplicável e origem de medição. NF, recebíveis e baixas mantêm suas próprias origens. `Payable` guarda escopo, obra, contrato opcional e origem tributária quando presente. Caixa realizado usa baixas e estornos oficiais. | Critério gerencial por nível, origem de custo de pessoal, atribuição explícita a serviço ou contrato, conciliação e cobertura. `Payable` não guarda serviço nem comprova folha. Controle de faturamento e caixa têm bases próprias. |

**Primeiro recorte operacional.**

1. Há um Diário por empresa, obra e data local de execução, com várias atividades, equipes e ocorrências no mesmo dia. Identidade estável, fuso de referência e revisões preservam a identificação do registro ao longo do tempo.
2. O vínculo operacional funcionário–obra é opcional na empresa, com início/fim, histórico, versão e vínculo de trabalho válido quando houver alocação. Cadastrar um funcionário ou uma obra continua possível sem lotação. A equipe pode vir de uma alocação vigente ou de participação avulsa explícita no dia, com alçada, motivo quando houver divergência e validação de empresa, obra e vínculo de trabalho histórico. Participação avulsa vale para aquela execução e não cria lotação permanente. Preservar readmissões, desligamentos e correções sem reatribuição retroativa silenciosa.
3. A autorização do usuário para preencher o Diário é concedida explicitamente por obra e operação. Concessão, revisão e revogação são auditáveis. Um integrante da equipe pode participar sem conta de acesso. Um usuário autorizado pode registrar o trabalho da equipe sem estar lotado na obra. Conceder separadamente administração de acesso e lotação, consulta, preenchimento/correção, solicitação, decisão e exportação, conforme a alçada aplicável. Usar projeção operacional mínima na seleção de pessoas e aplicar escopo também em listas, detalhes, pendências, histórico e auditoria. Custos e RH exigem permissões próprias, inclusive em resumos, buscas e exportações.
4. Uma atividade pode ter vários funcionários, e um funcionário pode contribuir em mais de uma atividade no dia. Preservar sua contribuição individual e a referência estável da atividade. Quando houver avanço quantificável, registrar quantidade, unidade e origem informadas. Quando a atividade referenciar um serviço contratado, validar `ContractService → Contract → Work` no tenant pelo ID. Atividade geral da obra pode permanecer sem serviço contratual, com identidade operacional explícita e sem atribuição financeira a um contrato. Somar contribuições individuais uma vez para obter horas-pessoa. Se forem registradas presença total e parcelas por atividade, reconciliar as parcelas com o total e mostrar horas sem atribuição. O total de presença serve para conciliação. Tratar sobreposição comprovada e concorrência entre atividades ou obras. Para registros só com duração, explicitar o limite da validação.
5. Planejado e realizado guardam fontes próprias. Usar plano explicitamente registrado, com identificação e versão para reproduzir a comparação, e valores reais informados pelo responsável, com intervalo quando conhecido ou duração explícita. Mostrar falta de plano ou falta de registro como indisponibilidade da comparação. Distinguir desvio de horário, interrupção e atraso de atividade/marco. Registrar motivo operacional, descrição, período/duração, atividade afetada e providência quando conhecidos. Distinguir motivo desconhecido, ocorrência sem impacto e impacto comprovado. Ocorrências simultâneas não multiplicam tempos afetados. O desvio de horas por si só não prova atraso de cronograma. Salário, diagnóstico e documentos de RH ficam fora dessas projeções.
6. Reutilizar a fonte de apontamentos manuais das F12-01/F12-02/F12-04/F12-05/F12-09 para o Diário e os resumos, com idempotência, versão, correção rastreável e alçada. Um `202` guarda proposta e não altera horas, equipe ou ocorrências oficiais. Aprovação revalida obra, vínculo, alçada, versão e conflitos na mesma transação. Registrar autoria e instante de cada revisão, preservando o original e distinguindo a situação do Diário e a decisão de aprovação. O Diário não produz baixa, cobrança, folha, aceite ou reserva. Clima, equipamentos, materiais, fotos/anexos e distribuição externa ficam para recortes com contrato próprio.

#### Contrato funcional F13-01

- **Identidade e turnos.** A chave de negócio é empresa, obra e data local. O backend deriva a empresa da sessão. A unicidade vale em qualquer situação, inclusive após fechamento e reabertura. Turno e frente são identificações operacionais opcionais de atividades e ocorrências no mesmo Diário. Podem existir várias atividades para o mesmo serviço e várias pessoas por atividade. O ID da atividade identifica suas contribuições mesmo após correções. A criação concorrente garante um único Diário. Repetir a mesma intenção retorna o resultado anterior sem duplicar. Criar com outra intenção para uma chave ocupada gera conflito e permite consultar o registro existente conforme a alçada.
- **Atividade e serviço.** Uma atividade tem descrição e pode ser geral, com identidade própria, ou vinculada a um único serviço contratual por ID. O contrato é derivado do serviço. Qualquer referência informada precisa concordar com a cadeia empresa, obra, contrato e serviço. Para apontar em dois serviços, registrar atividades distintas. Alterar vínculo ou remover atividade com horas exige correção rastreável que preserve a revisão anterior e revalide as contribuições. Ocorrência pode ser da obra/dia ou estar ligada a uma atividade do mesmo Diário.
- **Equipe.** Cada contribuição vincula funcionário e vínculo de trabalho válidos na data, dentro da empresa e da obra. A lotação pode vir da vigência registrada. Sem lotação ativa naquela obra, a participação avulsa exige justificativa operacional, autorização adicional explícita para registrar participação avulsa nessa obra e registro de quem a aplicou ou aprovou. Vale apenas para a pessoa, obra e data registradas. Divergência com outra lotação é registrada para conferência e não autoriza sobreposição comprovada. Readmissão usa o vínculo aplicável ao dia de trabalho. Alterar lotação depois não reatribui o histórico.
- **Data e fuso.** A data de execução é `LocalDate`. Um gestor autorizado configura o fuso IANA da obra antes do primeiro registro. O Diário preserva esse fuso e os limites UTC do seu dia local, da meia-noite inclusiva à meia-noite seguinte exclusiva. Autoria e auditoria usam `Instant` UTC. Permitir datas passadas com vínculo e alçada válidos. Recusar data futura e intervalo real com fim posterior ao relógio oficial. No primeiro recorte, o fuso da obra fica fixo após o primeiro Diário efetivo. O frontend usa a data e o fuso oficiais da obra.
- **Horas e virada de dia.** Usar minutos inteiros na fonte compartilhada do H.H. manual. Cada contribuição informa duração positiva em minutos ou início e fim com precisão de minuto e deslocamento UTC compatível com o fuso da obra. O backend calcula a duração do intervalo pelo tempo transcorrido. Horário local inexistente ou ambíguo sem deslocamento válido é recusado. Trabalho que cruza a meia-noite é dividido em contribuições nos Diários das respectivas datas. Aceitar intervalos adjacentes e rejeitar sobreposição verificável do mesmo funcionário em atividades ou obras distintas, inclusive em aplicações concorrentes. Validar duração acumulada por funcionário, obra e dia contra a extensão real desse dia. Quando houver presença total informada nesse escopo, parcelas atribuídas não podem excedê-la. Sem intervalo, sinalizar que a sobreposição exata é indeterminada. Somar apenas contribuições efetivas da fonte compartilhada. Horas fracionárias são apresentação e não alteram o total em minutos.
- **Idempotência e correção.** A mesma intenção, com os mesmos dados, produz o mesmo registro ou a mesma solicitação. Reutilizar a intenção com dados diferentes gera conflito. Cada alteração, fechamento e reabertura exige versão esperada e alçada. Aplicação direta e aprovação usam as mesmas validações transacionais. Correção exige motivo e conserva autoria, valores anteriores e versão substituída. Um `202` preserva estado, conteúdo e totais efetivos. O backend consulta resumos por data local registrada, com ambos os dias do período inclusivos.
- **Fechamento e reabertura.** O Diário nasce aberto e suas revisões aplicadas são efetivas. Fechar congela a revisão atual para conferência e não lança horas novamente. Exige alçada própria, versão atual e ausência de propostas de alteração de conteúdo pendentes. Um dia sem execução pode ser fechado se essa condição e seu motivo forem informados explicitamente e o registro oficial for compatível com a declaração. A ausência de apontamento, sozinha, não comprova zero horas. Diário fechado permite consulta por quem tem acesso. A guarda de fechamento também vale no fluxo compartilhado de H.H. para contribuições vinculadas ao Diário. Reabrir para corrigir exige motivo, alçada própria e nova revisão auditada, preservando a revisão fechada anterior. Alterações posteriores só afetam os totais quando aplicadas. Solicitação, decisão e situação do Diário são fatos distintos. Valem a alçada direta efetiva e a vedação de autoaprovação do §4.3.
- **Vigência e revogação.** A empresa designa gestores com alçada específica para conceder ou revogar escopo por obra e operação, com auditoria. O acesso vale do início inclusivo até o fim exclusivo, em instantes UTC. Fim ausente mantém a concessão ativa até revogação. Em cada operação, combinar concessão atual com a permissão do papel. A checagem usa a hora da operação e vale também para registros retroativos, seleções de equipe, histórico, solicitações, exportações e acesso à auditoria relacionada. Revogar encerra o acesso coberto por aquela concessão, inclusive ao histórico, sem remover registros ou autoria. A aprovação revalida também a permissão e o escopo atuais do solicitante para a ação de origem. Se ele perdeu o acesso, a proposta permanece consultável para o revisor autorizado, que pode rejeitá-la com motivo. Outra pessoa autorizada pode propor nova alteração. Custos e dados de RH continuam sujeitos às permissões próprias.

**Aceite para F13-02.** Conferir criação concorrente por obra/data, replay e conflito de versão, atividades gerais e serviço de outra obra, avulso com e sem alçada, readmissão, virada do dia e intervalos adjacentes ou sobrepostos. Provar que 3 h e 2 h de duas pessoas no mesmo serviço somam 300 minutos-pessoa. Conferir fechamento com proposta pendente, reabertura auditada, revisão histórica preservada e `202` sem efeito nos totais. Testar duas empresas, duas obras do mesmo tenant e revogação com proposta em aberto, inclusive bloqueio de acesso ao histórico.

**Recorte gerencial posterior.** Manter as definições de obra da §5.5. Resultado previsto usa faturamento líquido aprovado e gastos elegíveis WORK; resultado de caixa usa recebimentos e pagamentos oficiais pelas datas efetivas. Um futuro resultado operacional por serviço/contrato/obra requer definição explícita da base e data de reconhecimento de receita e custo. Dias e horas de execução, emissão fiscal, competência, vencimento e baixa são evidências diferentes. Apuração de margem depende de receita-base elegível e coberta; não usar denominador ausente, zero ou negativo como se produzisse uma margem comparável.

- **Receita e custos atribuídos.** Ler faturamento aprovado pelas alocações oficiais e suas origens, pela base e período escolhidos. Serviços só recebem parcelas identificadas de receita e de custo. Valores sem serviço ficam no nível contratual comprovado. Gastos WORK sem contrato ficam no nível da obra. Gastos COMPANY continuam no corporativo. Descontos ou ajustes legados sem identidade seguem explícitos no nível oficial. Para cada fonte, informar moeda, período, data-base, corte, origem, cobertura e parcelas sem atribuição. Consolidar em BRL, USD e EUR separados. Somar cada origem uma vez ao subir de nível.
- **Mão de obra e conciliação.** Horas-pessoa são quantidade de execução. Qualquer estimativa de custo por H.H. exige taxa, moeda e vigência autorizadas e divulgação como estimativa. O custo-hora precisa de fonte explícita. Folha e conta a pagar exigem origem comprovada para conciliar a parte que representa o mesmo fato econômico, inclusive vínculo ou grupo, competência, componentes e valores, com vínculo à baixa para a base de caixa. Contar uma única vez cada componente na base definida, preservando estornos e o saldo sem atribuição. Sobreposição incerta entre estimativa, folha e título vira pendência de conciliação, sem deduplicar por data/nome/valor nem publicar margem integral. Aplicar a mesma deduplicação por origem em TaxObligation/Payable. Retenção e crédito seguem seus tratamentos oficiais.
- **Cobertura e acesso.** Distinguir completo para a base registrada, parcial, indisponível, sem permissão e zero comprovado. Informar por fonte divergências, períodos faltantes e valores sem atribuição no nível conhecido. Custos, taxas de pessoal e totais que os revelem só podem ser consultados com autorização financeira adequada; folha individual e dados privados têm autorização própria. Conferir com fontes oficiais no backend. O Diário operacional continua disponível a quem tem a alçada correspondente.

Ana registra 3 h em um serviço e Bruno registra 2 h no mesmo serviço, somando 5 horas-pessoa. Se a estimativa for R$ 400 e uma folha de R$ 500 com título de R$ 500 comprovar a mesma origem integral, uma base gerencial de custo efetivo reconhece R$ 500 uma vez. Se forem pagos R$ 300, a base de caixa registra R$ 300 na data da baixa. Se a folha só identifica a obra, o custo permanece nesse nível até haver atribuição comprovada.

#### Base gerencial de mão de obra e conciliação (F13-04)

Usar bases explícitas de **estimativa**, **custo efetivo conciliado** e **pagamento registrado**, cada uma com período e corte próprios. A estimativa lê os minutos efetivos de `work_time_effective` por empresa, funcionário/vínculo, obra, data e atividade. Folha requer uma fonte autorizada e identificável por competência. O cadastro do funcionário oferece perfil e remuneração, e o Payable registra obrigações. Essas fontes ainda carecem da identidade comum de folha e de seus componentes.

| Base | Reconhecimento e evidência |
|---|---|
| Estimativa | Data local da execução. Usar taxa horária, moeda, unidade, vigência e versão aprovadas para o vínculo e escopo. Cada contribuição usa uma única taxa aplicável. Ausência ou ambiguidade da taxa deixa a estimativa indisponível para aqueles minutos. Usar minutos × taxa ÷ 60 com decimal exato e arredondar o total por moeda segundo o ADR 0003. Não derivar taxa de salário, preço de venda ou divisor presumido. |
| Efetivo conciliado | Competência informada na origem da folha ou do componente efetivo aprovado. Identificar vínculo ou grupo de processamento, identidade e revisão da origem, componente, moeda, valor assinado e origem de ajuste. Atribuição precisa identificar a parte exata e sua obra. Contrato e serviço são opcionais e validados pela cadeia. |
| Caixa registrado | Data efetiva de cada baixa e estorno do título. Contabilizar pagamentos pelo evento oficial, com valor assinado, moeda e vínculo imutável à baixa original quando houver estorno. Competência, vencimento e emissão permanecem disponíveis como contexto. |

A unidade de conciliação é a **parcela identificada de um componente econômico**. O vínculo de conciliação comprova a origem canônica, a representação correspondente na folha ou no título e o montante comum na mesma moeda. Para cobertura parcial, repartir a origem em partes explícitas, inclusive o resíduo, com soma que reconcilie com o valor oficial e sem exceder qualquer representação envolvida. Divisão por contrato ou serviço exige evidência aprovada. Partes sem atribuição fina ficam no menor nível comprovado. Alteração posterior cria ajuste ou reversão rastreável, preservando as atribuições anteriores, competência e moeda.

Na base efetiva, reconhecer a origem comprovada uma vez. A folha é a origem para seus componentes identificados, e seu Payable é uma representação da obrigação. Comparar a estimativa do mesmo escopo separadamente. Um título de custo de mão de obra adicional só entra na base efetiva se comprovar componente distinto, competência e atribuição. Coincidência por nome, data ou valor deixa o resíduo em conciliação. Somente a parte verificável entra no subtotal identificado. O total efetivo e a margem do escopo ficam indisponíveis enquanto houver sobreposição incerta. O caixa reconhece apenas baixas efetivas, independentemente do reconhecimento da competência.

Casos de aceite com valores sintéticos em BRL:

| Caso | Entrada | Resultado esperado |
|---|---|---|
| Coincidência integral | Estimativa 400, folha 500, título vinculado 500, baixa 300 | Estimativa 400, efetivo 500, caixa pago 300. |
| Coincidência parcial comprovada | Folha 500, dos quais 300 estão representados no título de 650. Os outros 350 do título são componentes distintos e comprovados da mesma base. | Efetivo 850. Contar os 300 comuns uma vez. |
| Coincidência parcial incerta | Folha 500, título 650 com 300 vinculados à folha e os outros 350 sem identidade comprovada. | Subtotal identificado 500, pendência 350, cobertura parcial. Total efetivo e margem indisponíveis. |
| Atribuição incompleta | Folha 500 na obra, 300 comprovados no serviço A. | Serviço A atribuído 300, resíduo de obra 200, obra 500. O total de obra incorpora o resíduo uma vez. |
| Estorno em período posterior | Baixa de 300 em outubro e estorno de 300 em novembro. | Outubro registra +300 em pagamentos, novembro −300. A competência do custo segue sua própria origem. |

#### Apuração gerencial por serviço, contrato e obra (F13-05)

A primeira visão de **resultado previsto** mantém a definição da §5.5. Para cada nível, moeda e período, usar faturamento líquido aprovado menos gastos elegíveis de escopo WORK. F06-10 fornece data-base e alocação oficial do faturamento. F06-11/F06-14 fornecem data-base, elegibilidade e deduplicação dos gastos. A emissão fiscal fica em indicador próprio. A futura visão de custo efetivo usa a conciliação da F13-04 e exige fonte de folha quando houver custo de pessoal proveniente dela. Estimativas não completam lacunas da base efetiva. A visão de **caixa** usa recebimentos e pagamentos líquidos de estornos pelas respectivas datas efetivas, com as fontes e cobertura da F06-12.

- Cada fato tem identidade, moeda, data-base e um único nível de atribuição direto. Consolidar serviços em seus contratos e contratos em suas obras usando os fatos e os resíduos de cada nível. Atribuição ausente em serviço ou contrato fica visível no nível comprovado. Custo WORK sem contrato entra na obra. COMPANY fica no corporativo. Um saldo oficial conhecido da obra pode estar completo com detalhamento parcial por serviço. Marcar a cobertura em cada nível.
- Imposto `PAYABLE_BY_COMPANY` pode estar representado em TaxObligation e no Payable obrigatório de mesma origem. Contar a obrigação reconhecida uma vez e usar a baixa apenas na base caixa. Retenções e créditos ajustam as posições oficiais conforme seu tratamento. Descontos legados sem serviço ficam no contrato. Reversões e cancelamentos seguem o evento e a base oficiais.
- Informar período inclusivo, data-base, corte UTC da consulta, código ISO, fontes, cobertura e motivos. Calcular margem somente com resultado coberto e faturamento líquido aprovado positivo da mesma visão. Usar resultado ÷ faturamento × 100. A ausência de permissão, de fonte, de cobertura ou de denominador elegível mantém o indicador indisponível. Manter distintos valor zero verificado, subtotal atribuído e total.
- Exigir permissão financeira das fontes e escopo explícito da obra. A consulta de Diário continua operacional. Agregados gerenciais não projetam remuneração individual, taxa de pessoal ou documentos de RH. Provar isolamento, exclusão de COMPANY, deduplicação e ausência de efeitos oficiais em propostas `202`.

Caso de aceite em BRL, com uma única obra e um contrato. Serviço A tem faturamento aprovado 1.000 e gastos atribuídos 400. B tem 500 e gastos 200. O contrato tem imposto de 80 representado pela mesma TaxObligation e Payable. A obra tem EPI de 40 sem contrato. A despesa COMPANY de 100 fica no corporativo. O subtotal atribuído de A e B é 600 e 300. Com as fontes completas, o contrato tem resultado previsto 820 e a obra 780, com margem de obra de 52%. Os subtotais de serviço preservam a informação de atribuição parcial quando o imposto ou EPI não podem ser distribuídos.

**Contrato executável do Diário.** O relatório seleciona obra, contrato ou serviço e uma moeda. O previsto usa os registros efetivos pela data UTC de aplicação da cobrança e registro do gasto ou tributo. O caixa usa baixas e estornos por data efetiva. Uma origem exata de recebimento permite atribuição a serviço. Gastos sem ligação oficial a serviço permanecem no contrato ou na obra. Quando há resíduos nos níveis superiores, os subtotais atribuídos continuam visíveis e os totais do nível solicitado e sua margem ficam indisponíveis. O custo efetivo de folha depende da origem conciliável definida em F13-04. [Guia da API](FRONTEND-API-ENDPOINTS.md#253-resultado-gerencial-do-diário-f13-05).

### 5.8 Privacidade — recorte atual e decisões pendentes

A listagem de funcionários usa projeção mínima `EmployeeListItemResponse`: identificação operacional, cargo/departamento, matrícula, vínculo/situação, vínculo de usuário e rota autenticada opcional de foto. Não transporta documento, PIS, telefone, nascimento, gênero/etnia ou remuneração e não aceita filtros/ordenações por documento ou remuneração. **Salário e demais informações continuam no perfil/detalhe autorizado**, usando `EmployeeResponse`; retirar da lista não elimina cadastro nem impede a consulta legítima de RH. O PUT completo deve preservar os campos carregados no detalhe, sem reconstruí-los a partir da lista. Permissões granulares para detalhe, edição, exportação e suporte de RH ainda devem ser implementadas (F09-09), sem atribuí-las automaticamente a leitor operacional. Dados de pagamento mantêm seu reveal específico já existente.

Propostas de aprovação precisam do conteúdo necessário para aplicar a alteração, mas sua cópia persistida exige proteção própria e ciclo de retenção (F09-10); não resolver removendo campos e inviabilizando a aprovação, nem registrá-los novamente na auditoria. Cifragem de perfil/endereço e anexos privados permanece F09-02/F09-03. Identidade do responsável pela plataforma, papéis de controlador/operador por finalidade e canal oficial ainda não foram fornecidos: manter como bloqueio de publicação do aviso/abertura ao público, sem inventar contato/empresa nem presumir que apenas pessoa jurídica pode ser controladora. O fluxo de direitos/retenção permanece F08-11/F09-11; não há eliminação automática autorizada.

### 5.9 Mínimo priorizado de vencimentos da equipe — planejamento de 26/10/2026

O prazo relevante inclui **o contrato/vínculo de trabalho do funcionário com a empresa**, além de cursos/certificados e exames periódicos. Não se limita ao contrato comercial de obra nem à lotação. Usar datas explicitamente cadastradas e preservar a fonte/histórico do vínculo correto, inclusive experiência/aviso quando aplicáveis e readmissão; não calcular prazo a partir de lei, cargo ou jornada presumidos.

O mínimo é cadastro dos metadados/datas necessários, painel com próximos, vencidos e sem data e alertas **internos** de antecedência configurável para destinatários autorizados. Sem data não equivale a vencido, irregular ou inapto: pode haver prazo indeterminado ou informação ainda não cadastrada; não fabricar vencimento nem concluir irregularidade. A passagem do tempo só muda a projeção temporal do painel/alerta: não renova, efetiva, desliga, bloqueia acesso laboral ou declara aptidão médica automaticamente.

Contratos funcionais mínimos futuros, ainda não API executável:

- cadastrar/corrigir/consultar datas de contrato de trabalho, curso/certificado e exame, com funcionário/vínculo e tenant coerentes, histórico, autorização RH/saúde, auditoria mínima e alçada/aprovação;
- consultar painel paginado/filtrável por pessoa autorizada, categoria e período/situação temporal, com data de referência e antecedência oficiais, sem projetar para apontadores de horas o conteúdo de RH/saúde;
- consultar/configurar antecedências e destinatários internos dentro das permissões, e consultar alertas idempotentes, com revisão de relevância após correção de data ou alteração do vínculo, sem apagar histórico;
- apresentar somente identificação/metadados administrativos necessários. Exames não recebem diagnóstico, prontuário ou resultado clínico; validade não comprova aptidão. Anexos completos ficam na expansão F10-05 e suas proteções; dados médicos não são liberados por cadastrar validade.

Decisões pendentes: quais datas representam o término de cada tipo de vínculo, tratamento de prazo indeterminado e cadastro incompleto, fuso, dia do vencimento e limites inclusivos, dias corridos/úteis das antecedências, valores padrão e limites de configuração, destinatários e segregação RH/saúde, cadência/repetição/ciência dos alertas, correção/renovação manual e efeito no alerta anterior, retenção/auditoria e projeções mínimas. Nenhum prazo legal, aptidão, periodicidade de exame/curso ou envio externo foi aprovado por esta priorização.

F10-01 a F10-10 permanecem o backlog canônico. O painel inicial por metadados/datas não precisa esperar anexos completos ou e-mail/push/WhatsApp, mas não dispensa proteção dos metadados, cifragem/privacidade aplicáveis, autenticação/tenant e autorização granular. Contratos comerciais e avisos externos continuam expansão separada; não reutilizar dunning de assinatura como política trabalhista. Não implementar nem publicar endpoints/DTOs/enums por esta atualização documental.

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
- NFs registram número, série opcional, chave opcional, emissão, valor total, itens, impostos, situação e anexos. Novos uploads aceitam um PDF por NF, com histórico de PDF/XML preservado.

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

Correção humana de29/10/2026, posterior à ativaçãoV91, substitui a interpretação do relatório: grade Serviço/descrição → Número do contrato → Valor comercial → parcelas documentais sequenciais → Saldo; resumo somente contratado/faturado/saldo; gradeNF parcela/referência/número/emissão/valor/saldo acumulado. Colunas são NFs registradas por emissão/ID; saldoNF é a base contratual líquida atual do relatório menos NFs registradas acumuladas, contando uma vez porNF. Canceladas fora do acumulado. Não é caixa nem posição temporal anterior a aditivos. SnapshotV91 de caixa permanece imutável/capturado e não é exibido como esse saldo documental. Cobranças abertas são seção adicional separada com RECEIVABLES.READ+EXPORT. Sem nova migration. Correção conferida pelo frontend e publicada somente em8081 em29/10/2026 às14:49:55SãoPaulo,JARCF376B05/V91,42testes/Checkstyle/QA aprovados,OpenAPI9831765A...vivo200 e401PDFanônimo; [ativação atual](F07-04-DOCUMENTARY-LOCAL-ACTIVATION-2026-10-29.md); [guia vigente](FRONTEND-CONTRACT-BILLING-PDF.md). Os parágrafos seguintes registram as fontes e a capturaV91 anteriores; esta correção prevalece na apresentação e definição de saldo doPDF.

Relatório de controle de faturamento F07-04/F07-09: `GET /api/v1/company/billing-control/contracts/{contractId}/report/pdf`, tenant da sessão, BILLING.READ+EXPORT e INVOICES.READ+EXPORT. A correção humana de29/10 exige descrição completa, valor comercial líquido oficial, colunas de NFs registradas e saldo por serviço (valor comercial atribuído menos emitido). Reutilizar a regra F05-21 de desconto sobre capacidades não comprometidas; nunca recalcular desconto histórico ou atribuir ajuste legado sem origem. Indicar indisponibilidade quando a posição por serviço não puder ser conciliada. Resumo antes da relação deNFs. NFs canceladas são históricas, fora dos totais atuais.

Com RECEIVABLES.READ+EXPORT adicionais, apresentar caixa CONFIRMED assinado/líquido de estornos e saldo nominal contratado menos dinheiro recebido, explicitamente distinto de dívida dos títulos. Retenções/créditos não são dinheiro. Incluir cobranças com saldo oficial positivo, mesmo semNF, reutilizando a projeção oficial após retenções/créditos/baixas; não somar cobrança e NF como receitas distintas. Sem essas permissões, manter o PDF base e indicar indisponibilidade financeira, sem zeros fictícios.

Novas NFs devem preservar snapshot financeiro imutável por registro efetivo/aprovação: contrato líquido, caixa confirmado, diferença nominal, moeda, instante UTC e base declarada. Capturar atomicamente sob o lock contratual, sem efeito em202. Data fiscal de emissão não é data do snapshot; antigas sem captura mostram “Histórico indisponível”, sem backfill ou inferência. Eventos de auditoria permanecem imutáveis e snapshots não são recalculados após baixas/estornos/alterações/cancelamentos.

Streaming em janelas limitadas, PDFBox com cache temporário, logo privada/fallback, paginação e leitura REPEATABLE_READ no callback; auditoria mínima de exportação. Estado atual: correção publicada somente em8081 em29/10/2026 às14:30:04SãoPaulo após autorização humana confirmada, com V91 aplicada, backup privado verificado foraOneDrive e JAR386004E6... ativo. 47testes distintos/5suítes aprovados emrodadas,Checkstyle e4páginas sintéticasQA; OpenAPIE2845511... validado contra contrato vivo200; ambiente/volume/rede/porta preservados e Flyway/seeds do serviço desabilitados. Sem backfill ou lançamento financeiro real. [Ativação, evidências e recuperação](F07-04-REVISION-LOCAL-ACTIVATION-2026-10-29.md). [Guia](FRONTEND-CONTRACT-BILLING-PDF.md).


### 9.2 Armazenamento de arquivos

- Interface StorageProvider para não acoplar o sistema a um fornecedor.
- Desenvolvimento: armazenamento local controlado. No Docker local, volume privado persistente em /app/var/storage, diretório 0700 pertencente ao usuário não root da API; não usar a camada descartável do container para conservar anexos. A preparação local e sua prova após recriação estão em [F08-23](F08-23-LOCAL-PRIVATE-STORAGE.md). Não remover o volume em um restart comum; backup externo/object storage de produção são responsabilidades separadas.
- Produção: armazenamento de objetos compatível com S3, com URLs temporárias.
- Arquivos permitidos: PDF, XLSX, CSV, DOCX e XML, com limite configurável e verificação de tipo.
- Todo anexo registra empresa, entidade de origem, autor, data, hash e evento de auditoria.
- Anexo PDF/XML de NF passa por validação estrutural de conteúdo antes do storage, além de MIME/extensão e limite de 10 MiB. PDF exige assinatura, leitura válida, páginas e ausência de criptografia; XML exige formação válida, sem DOCTYPE, entidades externas ou XInclude e com limites de profundidade/elementos/atributos. Isso não valida autorização fiscal, assinatura ou esquema de NF e não substitui antimalware. Arquivo inválido falha sem criar anexo/evento de sucesso; não incluir conteúdo/erro bruto do parser no Problem Details.
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

Complemento de prioridade comercial de 26/10/2026: simplificação financeira existente → H.H. manual mínimo → vencimentos da equipe (seção 3.4). A sequência técnica abaixo não exige concluir ponto, diária, DSR ou toda a expansão de F10/F12 para o mínimo, mas os gates de segurança/privacidade e as dependências explicitadas em F10/F12 continuam obrigatórios. Esta atualização só organiza o planejamento, não inicia fases.

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

- `ReceivableSettlementAllocation` preserva a baixa por `BillingAllocation` do mesmo título/parcela/contrato/tenant. No fluxo manual F05-32, `InvoiceSettlementLine` registra a quantia humana exata por NF/item/composição e a tabela de alocações recebe somente sua soma por origem, sem rateio. Estorno registra as mesmas linhas e alocações negativas. A atribuição proporcional anterior permanece exclusivamente histórica no legado, não autoriza associar recebimentos antigos a NFs nem reprocessar suas distribuições.
- Desconto contratual, retenções e créditos são ajustes oficiais do devido, não alterações do preço histórico aceito nem dinheiro recebido. Crédito é lançamento imutável com reversão própria e motivo, restrito ao saldo não pago. Não libera a alocação canônica nem permite reduzir silenciosamente valor comprometido.
- Renegociação reprograma exclusivamente o saldo ainda devido em novos títulos individuais de mesma parcela e grupo financeiro, com origem persistida no título anterior. A soma é exata; não gera novo faturamento, juros, aumento de receita, perdão ou baixa fictícia. O título anterior passa a `RENEGOTIATED` e não aceita novas baixas.
- Cancelamento da renegociação é lógico e exige reversão anterior de recebimentos, ajustes e renegociações descendentes; reabre o saldo original. Título/pagamento original renegociado e retenção que formou os novos títulos não podem ser alterados sem reabrir a renegociação.
- Valores com origem em medição exigem a composição na alocação; valores sem origem podem usar somente a base negociada disponível. Conversões individuais legadas são preservadas, mas não podem compor novamente a mesma raiz no fluxo contínuo.
- Quando houver conversão individual legada, o resumo contínuo indica `LEGACY_ORIGIN_REQUIRES_RECONCILIATION` e não inventa zero de faturamento/recebimento sem atribuição segura. A reconciliação histórica precisa ser explícita; não reinterpretar preços nem atribuir baixas automaticamente por nome. O OpenAPI 3.1 documenta os financeiros indisponíveis como `string | null`, preservando decimal canônico quando disponível.

### 14.3. Revisão financeira F05-14

F05-21 autorizada explicitamente em 16/10/2026: faturamento somente com líquido positivo, sem desconto manual. O [contrato de integração](FRONTEND-NET-BILLING-DECISION.md) define distribuição proporcional apenas da capacidade ainda livre por base negociada e composição estável. Se S é subtotal, D ajuste contratual, G capacidade bruta consumida e A soma líquida ativa, a capacidade livre é S-G, o desconto livre é D-(G-A), e o líquido livre é S-D-A. Compromissos antigos não são recalculados. As participações líquidas são truncadas em duas casas e o resíduo fecha em ordem determinística decrescente de chaves BASE/COMPOSITION e UUIDs, limitado à capacidade. Consumo parcial congela floor(líquido solicitado × bruto livre / líquido livre); ao consumir todo o líquido da origem, consome-se todo o bruto restante. Novos avanços redistribuem somente o saldo livre, nunca o comprometido.

`GET /api/v1/company/billing-control/contracts/{contractId}/net-capacity` publica capacidade oficial versionada e posições paginadas, com BILLING.READ e MEASUREMENTS.READ. `POST /api/v1/company/billing-installments/net` exige alçada BILLING.CREATE, UUID de operação idempotente e capacityVersion; cria somente alocações SERVICE positivas com base/desconto imutáveis, título líquido e auditoria, sob lock contratual. HTTP 201 direto, 200 replay, 202 pendente sem reserva; aprovação revalida a versão da capacidade, ou 409. Migration V85 é aditiva, não modifica registros financeiros anteriores. O resumo de medição conserva aceito documental e reconhece desconto comprometido e livre como ajuste financeiro oficial, evitando falta receber fictícia. Concluído com 135 testes em 30 suítes e 12 execuções focadas adicionais sem falhas/erros/ignorados; Checkstyle e OpenAPI 3.1 (127 paths/205 schemas) validados. [Contrato, hash e evidências](FRONTEND-NET-BILLING.md). Não houve autorização de deploy/reinício/push; V85 permanece sem aplicação ao banco real pelo agente.

`GET /api/v1/company/billing-installments` e `GET /api/v1/company/billing-installments/{installmentId}` oferecem leitura de parcelas oficiais com `BILLING.READ`, tenant da sessão, filtros/ordenações controlados e histórico cancelado. O líquido histórico é a soma canônica das alocações com seus sinais, incluindo desconto contratual negativo; não é saldo devido nem comprometimento ativo. O detalhe preserva IDs de origem e nome do serviço contratual, sem conteúdo de medição. Somente com `RECEIVABLES.READ` expõe referências mínimas dos títulos; sem ela sinaliza indisponibilidade e não vaza IDs. Propostas `202` não se tornam parcelas, e os GETs não alteram situação, auditoria ou cálculo oficial. [Contrato de leitura F05-20](FRONTEND-BILLING-INSTALLMENT-READ.md).

A seleção de origem de faturamento usa `GET /api/v1/company/billing-control/contracts/{contractId}/measurement-origins`, consulta paginada de composições individuais no tenant. Exige `BILLING.READ` e `MEASUREMENTS.READ`, preserva o destino contratual imutável da composição e publica sua capacidade canônica remanescente em texto decimal. Não usa título nem destino atual da identidade contínua para associar valores. Somente origens aplicadas de versões aceitas, não revertidas, sem redução aceita não reconciliada e com saldo positivo aparecem. A leitura não reserva saldo nem altera estado; o comando de faturamento continua sendo a autoridade transacional dos limites por origem, serviço, desconto e contrato. Detalhes/estado em [F05-19](FRONTEND-BILLABLE-MEASUREMENT-ORIGINS.md).

O [registro de revisão](F05-14-FINANCIAL-INVARIANTS-REVIEW.md) acompanha a cadeia composição → posição comercial → faturamento → NF → baixa/estorno com provas nos GETs oficiais, nos modos `FIXED` e `MEASUREMENT_BASED`. As respostas da receita usam strings decimais canônicas de duas casas, mantendo `BigDecimal` no servidor e sem arredondar na serialização. Isso inclui alocações, resumo paginado, NF/itens, títulos/saldos, baixas e impostos.

Alteração comercial de serviço compartilha o lock contratual versionado com composição/faturamento, antes das leituras usadas na decisão, e revalida seu comprometimento próprio, não apenas o teto global do contrato. A base negociada também precisa cobrir a capacidade consumida pelas alocações sem origem: composição aceita não pode encobrir excesso antes atribuído à base. Serviço cancelado não recebe faturamento; serviço com composições aceitas vigentes exige reversão formal antes do cancelamento. Redução/reversão de desconto mantém lastro para os ajustes negativos legados e os descontos congelados nas novas alocações líquidas. NF resolve alocações ativas e capacidade somente após obter o lock. Aprovações repetem as validações; uma proposta `202` não antecipa valores oficiais. F05-21 adiciona base/desconto imutáveis pela V85 em testes isolados, sem alteração de dados reais nesta entrega.

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


### Correção de consulta do estado do desconto contratual —29/10/2026

O detalhe autenticado deContract deve distinguir existência deContractDiscount ativo de seu efeito monetário: percentual ativo pode produzirzero, e o agregado contractDiscountAmount inclui ajustes de medição. hasActiveContractDiscount indica somenteexistência comercial ativa, comCONTRACTS.READ/tenant, sem concederalçada decancelamento. Em listas/mutações o campo énull/nãoavaliado e exige detalhe atualizado;202nãoaltera estadooficial. Semnova migration/regrafinanceira. Correçãopublicada em8081 em29/10/2026 às18:44:52SãoPaulo,JARAF20D2D2/OpenAPI24BE6633...,testes/conferênciafrontend/contratovivo200/401verificados,sem migration. [Contrato](FRONTEND-CONTRACT-DISCOUNT-STATE.md). [Ativação](F03-17-DISCOUNT-STATE-LOCAL-ACTIVATION-2026-10-29.md).


### Posição financeira da cobrança no controle de faturamento —29/10/2026

O status persistido da parcela não substitui a posição oficial de recebimento. Publicar projeção separada do título original associado,comRECEIVABLES.READ adicional aBILLING.READ e disponibilidade explícita/null quando ausente ou nãoautorizado. Reutilizar posiçãooficial líquido de estornos/retenções/créditos; encerramentoporajustes não é recebimentointegral emdinheiro. Preservar cancelado/renegociado e202; não somarsucessores nem atribuir pagamentoindividual a serviços por essa situação de título. Sem migration/reprocessamentohistórico;correçãopublicada em8081 em29/10/2026 às19:22:27SãoPaulo,JAR4A2D4980/OpenAPI0DE96AE5...,testes/frontend/runtimeverificados. [Guia](FRONTEND-BILLING-FINANCIAL-STATUS.md). [Ativação](F05-12-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-10-29.md).


### Apresentação de recebimento com retenção —29/10/2026

Pedido humano posterior permite o rótulo “Recebida — com retenção de imposto” para títulooficialmenteencerrado comdinheiroconfirmadopositivo,retençãopositiva ecrédito zero. Backend indica essa condição pela posição oficial já existente,semconverter retenção emcash/alterarenum,saldos,filtros,202 ouhistórico. Nãoestender àquitaçãoporcrédito/ajustemisto,100%retidosemcash,cancelado ourenegociado. MesmaRECEIVABLES.READ+BILLING.READ,tenant e indisponibilidadenull. Semmigration;recortepublicado em8081 em29/10/2026 às20:01:42SãoPaulo,JARE09835F5/OpenAPI0C36E221...,9testes/Checkstyle/bootJar aprovados,conferênciafrontend/contratovivo200/401verificados. [Guia](FRONTEND-BILLING-WITHHOLDING-LABEL.md). [Ativação](F05-12-WITHHOLDING-LABEL-LOCAL-ACTIVATION-2026-10-29.md).

### Complemento F05-37 — retenção proporcional por InvoiceItem, 29/10/2026

O usuário aprovou explicitamente “Sim, distribuir proporcionalmente entre os serviços”. Substitui somente a recusa anterior de rateio da retenção compartilhada para o recebimento TOTAL. O backend distribui retenção registrada WITHHELD_BY_CUSTOMER por valores brutos dos InvoiceItems exatos da mesma NF/título original, usando ProportionalMoneyAllocation e fechamento determinístico dos centavos. Expõe imposto, líquido e saldo líquido residual por item; a proposta total usa esses líquidos uma única vez. Dinheiro histórico permanece atribuído às suas próprias linhas; parciais continuam manuais, sem rateio de caixa ou entre títulos. Base/escopo não comprovado ou histórico que ultrapasse o novo líquido exige conciliação, sem reescrita. POST/aprovação mantêm tenant, alçada, locks, idempotência e202 sem alteração oficial. Sem migration ou operação financeira real. 50testes/4suítes, Checkstyle e bootJar aprovados; export49F89DB0/148paths/257schemas. Conferido pelo frontend e publicado somente em8081 às23:42:22SãoPaulo, JARE97C650C/schema91 preservado, sem migrations/seeds/opsreais. [Provas](F05-37-WITHHOLDING-ITEMS-LOCAL-ACTIVATION-2026-10-29.md). [Contrato atual](FRONTEND-INVOICE-ITEM-WITHHOLDING.md).

### Correção F03-16 — desconto preservado na edição operacional, 30/10/2026

Reproduzido em banco temporário: o PUT operacional de ContractService apagava o desconto do serviço ao omitir discountType/discountValue, tanto em DIRECT como na aprovação. COMPLETED continua incluído no subtotal; desconto de cabeçalho não é apagado pelo fluxo. Correção publicada em8081 em30/10 às00:47:58SãoPaulo preserva o desconto vigente sob lock, inclusive após uma proposta mais antiga. Par omitido/null não é reversão; eco explícito igual é permitido, intenção divergente exige operação dedicada DISCOUNTS com409controlado. Criação, fórmulas, totais, limites,tenant,alçada e auditoria mantidos. Sem migration, recuperação automática de descontos já perdidos ou alteração de contrato/recebimentos reais. [Contrato](FRONTEND-SERVICE-UPDATE-DISCOUNT-PRESERVATION.md).

Validação F03-16:16testes/4suítes,Checkstyle/bootJar aprovados; export4BFEA6EB/148paths/257schemas, JAR5E4601A1. Candidato conferido pelo frontend, schema91 e ambiente privados preservados, OpenAPI200/401anônimo verificados. Sem migration ou reparo real. [Registro](F03-16-SERVICE-DISCOUNT-PRESERVATION-LOCAL-ACTIVATION-2026-10-30.md).

### Encerramento contratual e desconto — decisões humanas de30/10/2026

A conclusão de serviço exige quitação comprovada da sua própria origem financeira, por dinheiro confirmado e retenção registrada nos itens exatos; crédito/histórico sem identidade não são prova. Líquido zero sem compromissos permite conclusão. DRAFT/ACTIVE impedem concluir contrato; COMPLETED/CANCELLED são terminais. Cancelar contrato exige todos os serviços cancelados e compromissos resolvidos, sem cascata. Vazio pode concluir/cancelar. Reabrir explicitamente antes de criar/reativar serviços ou acrescentar composição em contrato encerrado. Situação isolada não recebe preço/quantidade/desconto do cliente, e a aprovação preserva o perfil corrente. Cancelamento exige alçada CANCEL;202 não altera oficial. Operações gerais e aliases terminais não contornam as regras.

Cobrança com saldo oficial aberto impede dar desconto mesmo semNF; não usar status bruto OPEN da parcela. O novo líquido também não pode ficar abaixo do dinheiro confirmado líquido de estornos, sem considerar imposto/crédito como caixa, mantendo limites mais restritivos de faturamento/capacidade/desconto comprometido. Abrange desconto de serviço/contrato e redução comercial que contorne a regra. Descontos já vigentes não são limpos ao mudar situação.

F03-19 implementada e publicada localmente em05/11/2026: 131 testes de oito suítes, Checkstyle e bootJar aprovados; OpenAPI executável atualizado e runtime8081 verificado, sem migration/seed ou operação real. [Evidências de entrega](F03-19-CONTRACT-LIFECYCLE-LOCAL-ACTIVATION-2026-11-05.md). [Guia do recorte](FRONTEND-CONTRACT-LIFECYCLE.md). A proposta anterior de recuperação de desconto no serviço foi descartada pelo novo contexto humano; nenhum reparo real foi executado.

### Projeção financeira na listagem/detalhe de cobranças — 05/11/2026

Correção de F05-20 implementada e publicada somente em8081 em05/11/2026 às12:08:09SãoPaulo, com16testes/quatro suítes, Checkstyle e bootJar aprovados: GET billing-installments e seu detalhe devem apresentar a mesma situação financeira oficial do título original já usada nas alocações de billing-control. BILLING.READ autoriza a cobrança; RECEIVABLES.READ adicional libera somente status financeiro e indicação segura de recebido com retenção, com false/null/null quando indisponíveis. Status cadastral, filtros, ordenações e netAmount histórico permanecem separados. Carregar por lote dos IDs da página/tenant, sem sucessores ou cálculos no frontend, regravação de estado ou efeito de propostas202. A guarda de F03-19 permanece preservada. ExportADD231B9 e JAR6477462F verificados, sem migration/seed ou operação real; retorno0F247DFF compatível preservado. [Guia](FRONTEND-BILLING-INSTALLMENT-READ.md) e [evidências](F05-20-INSTALLMENT-FINANCIAL-STATUS-LOCAL-ACTIVATION-2026-11-05.md).

### Feedback de encerramento e conciliação — 05/11/2026

Correção complementar F03-19 publicada/verificada em8081 em05/11/2026 às12:54:46SãoPaulo, com21testes distintos/23execuções, Checkstyle e bootJar aprovados: situação histórica COMPLETED não substitui um motivo financeiro impeditivo autorizado na consulta de elegibilidade. A API deve distinguir conciliação de falta de quitação, sem expor esse motivo a quem não possui READs financeiros. Atribuição antiga por parcela/alocação não autoriza inventar vínculo por NF/item nem registrar novamente caixa real. A correção de feedback não inclui workflow ou reparo de conciliação histórica e não altera guardas ou dados oficiais. ExportF42E4998/JARD704704F ativos, schema91 sem migrations/seeds e retorno6477462F preservado. [Contrato](FRONTEND-CONTRACT-LIFECYCLE.md), [provas](F03-19-CLOSURE-RECONCILIATION-FEEDBACK-LOCAL-ACTIVATION-2026-11-05.md).

### Preparação pontual de conciliação de vínculos legados — 05/11/2026

F05-38 preparada/testada e aplicação pontual posteriormente autorizada e concluída em05/11/2026 às17:02:06SãoPaulo: ferramenta operacional separada da API para acrescentar somente vínculos documentais a duas baixas existentes, explicitamente selecionadas em plano revisado. Não representa módulo completo ou endpoint de conciliação. Validação é READ ONLY; eventual aplicação exige aprovação humana específica do plano/estado, transação, locks, integridade e auditoria SYSTEM sem forjar usuário. Não cria caixa nem regrava valores/datas/status/descontos/NFs; não distribui dinheiro ou infere destinos. Confirmação de destinos é distinta de autorização de execução real. 10 testes distintos, Checkstyle/bootJar e recusas CLI isoladas aprovados; prévia READ ONLY sem mudança nos registros originais, novos vínculos ou eventos de conciliação. JAR operacional9BA1AA10, sem alterar a API D704704F/OpenAPI F42E4998, schema ou serviço em execução. A preparação não executou reparo; após autorização humana específica, somente dois vínculos e auditoria SYSTEM foram acrescentados. Verificação READ ONLY posterior confirmou dados financeiros originais inalterados e histórico sem atribuição resolvido. Não houve novo dinheiro/estorno, alteração de regra ou fechamento forçado; conclusão continua no fluxo normal autenticado. A autorização vale somente para o plano revisado, não para outro caso ou módulo genérico. [Escopo e protocolo](HISTORICAL-RECEIPT-LINK-MAINTENANCE.md).
