# SDD — Plataforma SaaS de Gestão Financeira de Obras

**Nome comercial:** a definir  
**Repositório atual:** Canteiro.io  
**Versão do documento:** 0.1  
**Data:** 10 de agosto de 2026  
**Status:** requisitos de negócio consolidados; recomendações técnicas identificadas como tal  

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
| Medição | Conjunto versionado de itens medidos, sempre ligado a uma obra e opcionalmente a um contrato; depois do aceite externo, gera serviços adicionais em um contrato da mesma obra. |
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
   - assinaturas, cobranças, inadimplência e desbloqueios de confiança;
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
   - medições, revisões, planilhas e aceite externo registrado internamente;
   - faturamento, parcelas, NFs, impostos, contas a receber e recebimentos;
   - fornecedores, custos, despesas, contas a pagar e pagamentos;
   - fluxo de caixa e relatórios financeiros.

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
   - exportação de planilhas de medição;
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
| D-06 | Uma medição pode ser criada sem contrato, mas deve pertencer a uma obra. Antes de produzir resultado financeiro, ela deve ser vinculada a um contrato existente da mesma obra ou criar um novo contrato nessa obra. |
| D-07 | Aceite da medição ocorre fora do sistema. A empresa usuária registra o aceite internamente e guarda a evidência/anexo quando houver. |
| D-08 | Toda inclusão, alteração, cancelamento e exclusão lógica é auditada. Somente ações sem alçada direta aguardam aprovação. |
| D-09 | Usuário da empresa pertence a uma única empresa. Usuário de plataforma/suporte é global e pode atuar em várias empresas conforme suas permissões. |
| D-10 | Não haverá exclusão física de dados financeiros, obras, contratos, serviços, NFs ou medições. O sistema usa cancelamento, inativação e histórico. |
| D-11 | Uma NF pertence a um único contrato e pode distribuir seu valor entre vários serviços desse contrato. Um serviço pode ser faturado em várias NFs. |
| D-12 | O sistema não permite que o faturamento acumulado aprovado ultrapasse o valor líquido do contrato. |
| D-13 | Uma redução de valor/desconto do contrato que deixaria o valor líquido abaixo do faturamento líquido já aprovado é bloqueada até haver estorno, crédito ou renegociação correspondente. |
| D-14 | Desconto de contrato é uma linha de ajuste contratual de faturamento, separada dos valores dos serviços; ele não regrava nem rateia os preços dos serviços. |
| D-15 | Controle de faturamento, parcelas, saldo faturável, NFs e contas a receber pertencem ao contrato. A obra somente consolida esses valores entre seus contratos. |
| D-16 | Receita, custos, gastos vinculados e lucro são apurados no nível da obra, somando os resultados dos seus contratos e os gastos diretamente atribuídos à obra. |
| D-17 | Todo gasto possui escopo Obra ou Empresa. Gasto de Empresa não possui obra/contrato, integra caixa e relatórios gerais da empresa, mas não altera lucro de qualquer obra. |

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
- criação direta;
- edição direta;
- solicitação de criação/edição/cancelamento;
- aprovação;
- rejeição;
- exportação;
- envio de relatório;
- gestão de usuários;
- gestão de papéis.

Módulos mínimos: empresa, usuários, clientes, contratos, serviços, descontos, medições, faturamento, NFs, contas a receber, contas a pagar, custos/despesas, relatórios e auditoria.

Perfis iniciais sugeridos:

| Perfil | Uso inicial |
|---|---|
| Empresa master | Controla a empresa, usuários e todos os módulos permitidos pelo plano. |
| Auditor master | Aprova solicitações em todos os módulos permitidos. |
| Auditor financeiro | Aprova somente faturamento, NFs, recebimentos, pagamentos, custos e despesas. |
| Operador de cadastro | Cria ou altera dados, enviando-os para aprovação quando não tiver alçada direta. |
| Relatórios | Consulta e exporta dados autorizados, sem alterar registros. |

### 4.3 Fluxo de aprovação

1. Usuário tenta criar, alterar ou cancelar um registro.
2. A API verifica sua permissão para a ação e módulo.
3. Se houver permissão direta, grava a alteração e cria um evento de auditoria.
4. Caso contrário, cria uma Solicitação de Alteração com a proposta, os valores anteriores, os valores novos e o solicitante.
5. O dado oficial continua inalterado e a proposta não entra em relatórios operacionais.
6. Um auditor com a permissão daquele módulo aprova ou rejeita. O solicitante nunca aprova sua própria solicitação.
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

Uma medição possui cabeçalho, itens, desconto opcional, versão, total de peso, total financeiro, documentos gerados e vínculo opcional ao contrato.

Tipos de cobrança iniciais:

| Tipo | Dados informados | Fórmula |
|---|---|---|
| Metro quadrado | Área em m² e preço por m² | área × preço por m² |
| Metro linear | Metros lineares e preço por metro | metros × preço por metro |
| Quilograma por m² | Quilogramas por m², área e preço por kg | kg por m² × área = kg total; kg total × preço por kg |
| Quilograma por metro linear | Quilogramas por metro, metros e preço por kg | kg por metro × metros = kg total; kg total × preço por kg |

Cada item deve guardar atividade, como desmontagem, montagem, fabricação ou pintura; descrição; tipo de cobrança; quantidades; preço por unidade aplicável; fórmula aplicada; peso total e valor total calculado. O desconto inicial da medição é aplicado no cabeçalho da versão e registrado como ajuste separado, preservando os valores brutos de seus itens.

Fluxo:

1. A empresa cria uma medição vinculada a uma obra, com ou sem contrato.
2. O sistema gera planilha em Excel e/ou PDF.
3. A empresa envia o documento ao cliente final por meio externo ao sistema.
4. Ao receber o aceite externo, a empresa registra o aceite e, se disponível, anexa o documento/evidência.
5. Itens aceitos ficam congelados. Novos itens ou aumentos criam uma nova revisão que exige novo aceite externo.
6. Uma revisão aceita deve ser vinculada a um contrato existente da mesma obra ou usada para criar um novo contrato nessa obra.
7. Cada item aceito gera, no máximo uma vez, um serviço adicional no contrato, preservando o vínculo de origem com a medição e a revisão. A conversão deve ser idempotente; para dividir um serviço, o usuário deve separar os itens antes do aceite.
8. A medição exibe valor faturado e saldo calculados a partir dos serviços gerados e de seus faturamentos.
9. A medição finalizada não aceita novos itens ou revisões.

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
2. Planos são cumulativos. A precificação usa pacotes de combinação configurados pelo proprietário, para que combinações como Plano 1 + Plano 2 tenham preço promocional sem alterar preços individuais.
3. Cada contratação mantém foto do preço, desconto, composição e vigência para preservar histórico.
4. O período de teste é de 30 dias.
5. A integração será encapsulada por uma interface de gateway. A implementação inicial recomendada é Asaas, pois sua documentação oficial suporta cobranças recorrentes por boleto, Pix e cartão de crédito, além de webhooks de cobrança. O cartão pode ser cobrado automaticamente; Pix e boleto têm geração recorrente de cobrança, mas o pagador realiza o pagamento. Veja a documentação oficial do Asaas: https://docs.asaas.com/docs/faq-assinaturas
6. Eventos de webhook devem ser idempotentes, assinados/verificados e reconciliados com consulta periódica ao gateway.
7. A regra de inadimplência será:
   - vencimento não pago: avisos por e-mail;
   - após o vencimento: acesso somente para consulta;
   - a partir de 5 dias: status de inadimplente e manutenção de consulta;
   - aproximadamente a partir de 10 dias: bloqueio total;
   - pagamento confirmado: restaura o acesso conforme a assinatura ativa;
   - desbloqueio de confiança: até dois por cobrança vencida, gravando autor, motivo, início e expiração configurável.
8. A plataforma mantém sua própria tabela de cobranças e eventos; o gateway não será a única fonte de verdade.

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
- PostgreSQL Row Level Security será avaliado como defesa adicional em uma fase posterior; a autorização da aplicação continua obrigatória.

### 6.4 Autenticação e segurança

- Spring Security com tokens de acesso de curta duração e refresh tokens revogáveis.
- Senhas com hash forte, política de senha e recuperação por e-mail.
- Verificação de e-mail na ativação de usuário.
- MFA fica preparado como evolução, mas não entra no MVP.
- Proteção contra força bruta, limitação de requisições em login e bloqueio temporário.
- Validação de entrada, autorização no serviço e não apenas no controlador.
- Logs sem senha, token, dados completos de cartão ou conteúdo fiscal sensível.
- Segredos em variáveis de ambiente ou serviço de segredos, nunca em application.properties versionado.

## 7. Modelo de dados conceitual

### 7.1 Relações principais

~~~mermaid
erDiagram
    COMPANY ||--o{ COMPANY_USER : possui
    COMPANY ||--o{ FINAL_CUSTOMER : cadastra
    FINAL_CUSTOMER ||--o{ WORK : possui
    WORK ||--o{ CONTRACT : possui
    CONTRACT ||--o{ CONTRACT_SERVICE : contem
    COMPANY ||--o{ SERVICE_TEMPLATE : possui
    SERVICE_TEMPLATE ||--o{ CONTRACT_SERVICE : origina
    WORK ||--o{ MEASUREMENT : possui
    MEASUREMENT ||--o{ MEASUREMENT_VERSION : possui
    MEASUREMENT_VERSION ||--o{ MEASUREMENT_ITEM : contem
    MEASUREMENT_ITEM o|--o| CONTRACT_SERVICE : origina
    CONTRACT ||--o{ BILLING_INSTALLMENT : fatura
    BILLING_INSTALLMENT ||--o{ BILLING_ALLOCATION : distribui
    CONTRACT_SERVICE ||--o{ BILLING_ALLOCATION : recebe
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
| Plataforma | Company, Plan, PlanBundle, PlanBundleItem, Subscription, SubscriptionItem, PlatformCharge, PaymentGatewayEvent, TrustUnlock. |
| Identidade | User, CompanyUser, PlatformUser, RefreshToken, PasswordResetToken, Role, Permission, RolePermission, UserRole. |
| Governança | ChangeRequest, ChangeRequestDecision, AuditEvent, AuditPayload, AccessLog. |
| Comercial | FinalCustomer (tabela final_customer), Work (tabela obra), WorkAddress, Contract, ServiceTemplate, ContractService, Discount, ContractRevision. |
| Medição | Measurement (vinculada à obra), MeasurementVersion, MeasurementItem, MeasurementDocument, MeasurementContractLink. |
| Receita | BillingInstallment, BillingAllocation, Invoice, InvoiceItem, Receivable, ReceivableSettlement. |
| Despesa | Supplier, CostCenter, Payable, PayableSettlement, TaxObligation, FinancialCategory. |
| Documentos | Attachment, GeneratedDocument, ReportExport, ReportDelivery. |

### 7.3 Campos financeiros e integridade

- Valores monetários: BigDecimal, moeda BRL, escala de 2 casas para moeda e escala configurada de até 4 casas para quantidades técnicas.
- Datas: data local para emissão/vencimento e timestamp UTC para auditoria.
- FinalCustomer possui company_id obrigatório. Work possui final_customer_id obrigatório. Contract possui work_id obrigatório e não armazena um cliente final independente; o cliente final do contrato é derivado da obra. As chaves estrangeiras e a validação de tenant impedem vínculos entre empresas distintas.
- Work registra o tipo de local de execução e o endereço utilizado naquela obra. Isso permite executar na unidade do cliente final, na sede/unidade da empresa prestadora ou em outro endereço.
- Payable possui financial_scope obrigatório. Para escopo Obra, work_id é obrigatório e contract_id é opcional; quando contract_id existir, ele deve pertencer ao mesmo work_id. Para escopo Empresa, work_id e contract_id devem ser nulos.
- Receita, gastos e lucro de obra são projeções calculadas a partir dos contratos e títulos financeiros da obra; não haverá lançamento direto de receita ou saldo na tabela obra.
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
| Plataforma | empresas, planos, pacotes, assinaturas, cobranças, desbloqueios, suporte. |
| Empresa | dados da empresa, usuários, papéis, permissões, auditoria. |
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

Formatos: Excel, PDF e DOCX. Relatórios devem ser gerados de dados aprovados/efetivos, exceto quando o filtro selecionar explicitamente pendências.

### 9.2 Armazenamento de arquivos

- Interface StorageProvider para não acoplar o sistema a um fornecedor.
- Desenvolvimento: armazenamento local controlado.
- Produção: armazenamento de objetos compatível com S3, com URLs temporárias.
- Arquivos permitidos: PDF, XLSX, CSV, DOCX e XML, com limite configurável e verificação de tipo.
- Todo anexo registra empresa, entidade de origem, autor, data, hash e evento de auditoria.

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
| Segurança | Isolamento entre empresas, papéis, suporte global, bloqueio por inadimplência e auditoria. |
| API | Contratos OpenAPI, validação, erros, paginação e autorização. |
| Integração externa | Webhooks idempotentes, falhas de gateway, e-mails e armazenamento de anexos. |
| Regressão financeira | Estornos, pagamentos parciais, cancelamentos e fechamento de saldo. |

### 10.3 Operação

- ambientes local, homologação e produção;
- backups automáticos do PostgreSQL e política de restauração testada;
- logs estruturados com ID de correlação;
- health checks, métricas e alertas;
- execução de migrations Flyway em pipeline controlado;
- CI com compilação, testes e análise estática;
- Docker com imagens versionadas;
- segredos por ambiente;
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
| **Total API em qualidade de produção** | **Muito alta / XL** | **aprox. 185–295 dias ideais** |

O intervalo não representa prazo de calendário: uma equipe pode paralelizar partes independentes, mas segurança, governança, contratos e regras financeiras formam o caminho crítico. Para uma pessoa, é um produto de vários meses; para uma equipe pequena, ainda exige fases e validações de negócio.

### 11.2 Ordem recomendada de entrega

1. Fundação, identidade, isolamento, papéis e auditoria.
2. Clientes finais, obras, contratos, serviços, descontos e limite de faturamento.
3. Faturamento, parcelas, NFs, contas a receber e primeiro relatório de faturamento.
4. Medições, revisões, planilhas e conversão em serviço de contrato.
5. Contas a pagar, custos, despesas, impostos e fluxo de caixa.
6. Planos, assinaturas, gateway de pagamento, dunning e suporte global. Esta fase pode ser desenvolvida parcialmente em paralelo após identidade.
7. Relatórios restantes, documentos, endurecimento, testes e operação.

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

## 14. Próximos passos de implementação

1. Tratar [AGENTS.md](../AGENTS.md) como regras obrigatórias de implementação e preservar seus invariantes.
2. Executar [CODEX-IMPLEMENTATION-BACKLOG.md](CODEX-IMPLEMENTATION-BACKLOG.md) em tarefas pequenas, respeitando dependências e perfil Codex recomendado.
3. Validar este SDD como referência do MVP.
4. Escolher e habilitar a conta de gateway de pagamento; o adaptador inicial recomendado é Asaas.
5. Definir provedor de e-mail e armazenamento de arquivos para produção.
6. Corrigir a fundação atual antes de criar novos CRUDs.
7. Implementar primeiro os invariantes de segurança, auditoria e saldo; somente depois as telas ou endpoints de operação.
