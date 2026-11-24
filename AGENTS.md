# Instruções executáveis — Plataforma SaaS de Gestão Financeira de Obras

Estas regras são obrigatórias para qualquer agente que analisar, modificar ou implementar este repositório.

Comece pelo [Mapa do projeto](docs/MAPA-DO-PROJETO.md) para localizar o domínio, as classes e os trechos aplicáveis do SDD e deste arquivo. A autoridade e as invariantes continuam nas fontes indicadas na seção 1.

## 1. Fonte de verdade e autoridade

1. Leia este arquivo e o documento [docs/SDD-MVP-Gestao-Financeira.md](docs/SDD-MVP-Gestao-Financeira.md) antes de iniciar tarefas de domínio.
2. O SDD define o produto. Este arquivo transforma suas decisões em guardrails de implementação.
3. As regras marcadas como **INVARIANTE** não podem ser alteradas, removidas, contornadas ou reinterpretadas sem autorização explícita do usuário.
4. Quando um pedido ou código existente conflitar com um invariante, pare a implementação dessa parte, apresente o conflito e peça orientação. Não crie uma solução alternativa silenciosa.
5. Não ampliar escopo por conta própria para frontend, aplicativo móvel, emissão fiscal, integração bancária, ERP, contabilidade, IA ou portal externo de aceite.
6. O escopo atual é uma API REST backend. O frontend futuro será uma aplicação separada que consome a API; ele nunca acessará banco, storage ou gateway diretamente.

## 2. Stack obrigatória

- Java 21;
- Spring Boot, Spring Web MVC, Spring Data JPA, Spring Security e Bean Validation;
- PostgreSQL;
- Flyway para todo schema e dado inicial;
- Docker/Docker Compose;
- OpenAPI/Swagger;
- Testcontainers + PostgreSQL para testes de integração.

Não trocar stack, ORM, banco, mecanismo de migration ou arquitetura de monólito modular sem autorização explícita.

## 3. Arquitetura e organização de pacotes

Use um monólito modular, orientado a funcionalidade. Para código novo, não crie pacotes genéricos de controller, service, repository ou entity na raiz.

~~~text
com.renovar.canteiro.io
├── bootstrap/                 # configuração de inicialização e seed controlado
├── shared/
│   ├── api/                   # erros, paginação, filtros e convenções HTTP compartilhadas
│   ├── domain/                # Value Objects, exceções e abstrações comuns
│   └── infrastructure/        # clock, storage, e-mail, serialização e adapters comuns
├── platform/
│   ├── company/               # empresa assinante
│   ├── catalog/               # planos, módulos e pacotes promocionais
│   ├── subscription/          # trial, assinatura, cobrança e inadimplência
│   └── support/               # usuários e atuação de suporte global
├── identity/                  # usuário, senha, token, login e recuperação
├── tenancy/                   # contexto confiável da empresa e isolamento
├── access/                    # papéis, permissões e alçadas
├── governance/                # ChangeRequest, aprovação e auditoria
├── customers/                 # FinalCustomer
├── works/                     # Work, tabela obra e local de execução
├── contracts/                 # Contract, serviço, desconto e limite de faturamento
├── measurements/              # medição, revisões, fórmulas e aceite
├── receivables/               # faturamento, parcelas, NF, imposto e contas a receber
├── payables/                  # fornecedores, gastos, contas a pagar e pagamentos
├── reporting/                 # consultas, projeções, exportações e envio
├── documents/                 # anexos, PDF, XLSX e DOCX
└── notifications/             # e-mail e notificações de domínio
~~~

Cada módulo novo deve usar esta estrutura interna:

~~~text
<modulo>/
├── api/                       # Controller, requests e responses
├── application/               # casos de uso e transações
├── domain/                    # entidade, enum, VO, regra e porta de repositório
└── infrastructure/            # JPA entity/repository, adapter externo e configuração
~~~

Regras arquiteturais:

- Controllers recebem requests, chamam um caso de uso e devolvem responses. Não contêm regra de negócio, regra de tenant ou autorização complexa.
- Casos de uso ficam em application e são transacionais quando alteram estado.
- Regras e invariantes financeiros ficam em domain/application, nunca somente no controller ou frontend.
- Interfaces de integrações externas ficam no módulo de domínio/aplicação; implementações de Asaas, e-mail e storage ficam em infrastructure/adapters.
- Não expor entidades JPA diretamente na API.
- Não criar DTO Model mutável para transportar regra de domínio. Preferir records imutáveis para request/response e Value Objects para valores de domínio.
- Refatore gradualmente o esqueleto legado de Company antes de usá-lo como padrão para módulos novos.

## 4. Convenções de nomes e persistência

### 4.1 Código Java

- Classes e enums em inglês, PascalCase; métodos e campos em camelCase.
- Entidades principais: Company, User, FinalCustomer, Work, Contract, ContractService, ServiceTemplate, Measurement, BillingInstallment, Invoice, Receivable e Payable.
- A entidade Java Work mapeia obrigatoriamente para a tabela **obra**.
- Use nomes específicos: FinalCustomer, nunca Customer genérico; Work, nunca reutilizar Contract para representar obra.
- Commands/requests terminam em CreateRequest, UpdateRequest, ApproveRequest ou FilterRequest.
- Responses terminam em Response; casos de uso terminam em UseCase ou Service somente quando representam uma aplicação clara.
- IDs são UUID. Valores monetários usam BigDecimal, nunca double/float.
- Moedas de contrato/medição: BRL, USD ou EUR, duas casas. Persistir código ISO imutável desde a criação; legado é BRL sem conversão. Medição/contrato vinculados exigem a mesma moeda. Descendentes herdam a unidade pela cadeia imutável. Não somar moedas diferentes em consolidações, nem converter silenciosamente; separar por código. Formatação monetária recebe BigDecimal diretamente e símbolos são apenas apresentação, nunca parte do decimal JSON.
- Datas de negócio usam LocalDate; data/hora de auditoria usa Instant/UTC.
- Use enums para status, tipo de cobrança, escopo financeiro e tratamento tributário.

### 4.2 Banco de dados

- Tabelas e colunas em snake_case e nomes no singular.
- Chaves estrangeiras terminam em _id; toda entidade de tenant possui company_id.
- Tabelas centrais obrigatórias: company, final_customer, obra, contract, contract_service, service_template, measurement, measurement_version, measurement_item, billing_installment, billing_allocation, invoice, invoice_item, receivable, payable, change_request e audit_event.
- Toda mudança de schema usa nova migration Flyway com nome descritivo: V<numero>__<acao_em_snake_case>.sql.
- Nunca edite, renomeie ou apague migration já aplicada. Para corrigir dados/schema, crie nova migration compatível.
- Não use ddl-auto para criar schema. JPA deve validar o schema gerado por Flyway.
- Use constraints, índices e foreign keys para reforçar invariantes, além da validação na aplicação.
- Registros que afetam saldo/faturamento devem usar controle de concorrência otimista e validação transacional.

## 5. Hierarquia de domínio — INVARIANTE

~~~text
Company
  └── FinalCustomer
        └── Work (obra)
              └── Contract
                    └── ContractService
~~~

Regras obrigatórias:

1. FinalCustomer possui company_id obrigatório.
2. Uma Company possui vários FinalCustomer.
3. Uma Work/obra pertence obrigatoriamente a um único FinalCustomer.
4. Uma Work pode possuir vários Contract.
5. Todo Contract possui work_id obrigatório. O cliente final do contrato é derivado da obra; não criar um final_customer_id independente em Contract.
6. ContractService pertence a exatamente um Contract.
7. Um Contract pode existir sem serviços.
8. ServiceTemplate é reutilizável somente por cópia. Ao aplicar um modelo em contrato, criar novo ContractService independente.
9. Uma Work possui tipo de local de execução: local do cliente final, sede/unidade da empresa prestadora ou outro endereço informado.

Nunca substitua Work por Contract, nem mova faturamento de Contract para Work.

## 6. Tenancy, autenticação e autorização — INVARIANTE

1. Todo dado operacional da empresa deve ter company_id, direta ou indiretamente com validação de cadeia.
2. O company_id do tenant vem do usuário autenticado/contexto confiável; nunca aceite company_id livre no payload de usuário de empresa.
3. Usuário de empresa pertence a exatamente uma Company.
4. Consultas, filtros, exportações, anexos, unicidades internas e mutações devem respeitar company_id.
5. Um tenant não pode listar, inferir, adivinhar IDs, modificar ou exportar dados de outro tenant.
6. Usuários globais de plataforma/suporte usam rota/contexto separado para selecionar empresa-alvo, após autorização de papel global. Endpoints de plataforma e de empresa não se misturam.
7. Suporte da plataforma pode consultar, cadastrar/editar dados operacionais autorizados e gerar/enviar relatórios.
8. Suporte da plataforma não pode excluir registros, aprovar alterações de clientes, alterar planos/assinaturas, dados estruturais da empresa ou papéis de funcionários.
9. Toda rota protegida valida autenticação, tenant e permissão no backend. Segurança de UI não é suficiente.
10. Todo caso de uso de suporte exige `SupportTargetContext` e chama `SupportAuthorizationService`; operações proibidas não podem receber bypass, inclusive para `PLATFORM_OWNER`.
11. Cada módulo novo deve ter teste negativo provando isolamento entre duas empresas.
12. No onboarding público, o proprietário inicial da Company é criado como usuário pendente, vinculado à nova Company e recebe automaticamente o papel `Company Administrator` com todas as permissões ativas do catálogo controlado naquele momento. Após ativar a própria conta pelo token de convite, ele possui alçada direta efetiva; isso não dispensa autorização nem auditoria nas ações futuras.

### 6.1 Privacidade de funcionário e dados de RH

1. Listagens operacionais de funcionário usam projeção mínima. Não incluir documento/PIS, telefone, nascimento, gênero, etnia ou remuneração; não aceitar nem ordenar por documento ou remuneração quando a rota for uma lista operacional.
2. Salário e perfil completo permanecem em endpoint de detalhe autenticado; redução da lista não autoriza limpar, mascarar ou sobrescrever campos do cadastro no PUT. Até haver permissão granular, não alegar que `EMPLOYEES.READ` separa RH de operação.
3. Auditoria é imutável: não apagar eventos históricos. Novos snapshots e respostas de auditoria não devem copiar documentos, remuneração ou outros dados sensíveis sem necessidade explícita.
4. Propostas de aprovação que precisem guardar dados para aplicação devem ter proteção/retenção própria; não remova campos de modo a quebrar aprovação. Cifragem adicional, descarte autorizado, proteção de documentos e antimalware continuam requisitos próprios, não efeitos de uma lista mínima.
5. Antes da abertura pública, a identidade real do responsável, o canal de contato e a matriz de privacidade/retenção devem estar definidos. Nunca inventar contato, empresa responsável ou supor que somente pessoa jurídica possa ser controladora.

## 7. Papéis, alçadas, aprovação e auditoria — INVARIANTE

1. A empresa monta perfis a partir de permissões granulares por módulo e ação; não usa permissões livres em texto.
2. Separe permissões de consultar, criar/editar/cancelar diretamente, solicitar alteração, aprovar, rejeitar, exportar, enviar relatório, gerir usuários e gerir papéis.
3. Há alçada direta efetiva quando o usuário possui a permissão direta da ação ou, para o mesmo módulo e operação, possui a permissão de solicitação e a de aprovação. A ação é aplicada imediatamente e sempre gera AuditEvent.
4. Ação sem alçada direta efetiva cria ChangeRequest com entidade, operação, versão, valores antes/depois, proposta, solicitante e justificativa quando necessária.
5. ChangeRequest não altera dado oficial e não entra em relatório operacional até aprovação.
6. O solicitante nunca pode aprovar sua própria solicitação. A combinação de solicitação e aprovação não é autoaprovação: ela concede alçada direta efetiva e, portanto, não cria ChangeRequest.
7. Um auditor autorizado para o módulo aprova ou rejeita; rejeição exige motivo.
8. Aprovação aplica a proposta em transação atômica e cria evento de auditoria.
9. Status de aprovação e status de negócio são separados.
10. Toda inclusão, edição, cancelamento, exclusão lógica, aprovação, rejeição, login relevante e atuação do suporte é auditável.
11. AuditEvent é imutável e retido tecnicamente por pelo menos cinco anos. A tela pode abrir nos últimos 30 dias, mas não pode eliminar o histórico.
12. Evento de suporte registra operador, empresa-alvo, módulo, ação e, para relatório enviado, destinatário/artefato.
13. O onboarding registra um AuditEvent imutável para a criação da Company, incluindo proprietário inicial, planos selecionados, preço cotado e o papel/permissões iniciais concedidos.
14. Ações automáticas internas que alteram estado devem gerar AuditEvent com ator `SYSTEM`, sem forjar um usuário humano e com a origem identificável nos metadados.

## 8. Obras, contratos, serviços e medições — INVARIANTE

### 8.1 Obra e contrato

- A obra concentra receita, custos, gastos vinculados e lucro de todos os seus contratos.
- Contract mantém somente seu próprio valor, serviços, faturamento, saldo, parcelas, NFs e contas a receber.
- O valor líquido de um Contract é o limite de faturamento líquido desse Contract; valores consolidados da Work nunca alteram esse limite.
- Desconto de serviço altera o valor líquido do serviço e o subtotal do contrato. Edição operacional do serviço, inclusive mudança para COMPLETED, preserva o desconto vigente quando os campos forem omitidos ou ambos null, também na aprovação. Eco explícito igual é permitido; troca/reversão usa operação dedicada DISCOUNTS, nunca limpeza implícita pelo PUT de perfil.
- Desconto de contrato permanece ajuste comercial separado e nunca regrava preços dos serviços ou aceites. Conforme autorização F05-21, o novo faturamento recebe apenas valores líquidos positivos: o backend distribui proporcionalmente o desconto ainda não consumido sobre capacidades ainda não comprometidas, por base/origem exata, e congela base bruta/desconto em cada alocação. Ajustes negativos legados são preservados; não redistribuir compromissos históricos.
- Não permitir redução de contrato para valor líquido inferior ao faturamento líquido aprovado sem estorno, crédito ou renegociação auditada.
- Não fazer exclusão física: use status, cancelamento, inativação ou estorno.
- Contract possui `pricing_mode`: `FIXED` preserva o valor-base negociado; `MEASUREMENT_BASED` forma o valor comercial progressivamente por composições de versões de medição aceitas.
- Composição de medição é imutável, idempotente por grupo estável de serviço/versão e auditável. Ela não substitui o valor-base de `ContractService`; a posição efetiva do serviço soma valor-base líquido e composições aceitas. `serviceTitle` não é chave financeira.
- Correção posterior de origem já composta exige reversão/substituição rastreável. Redução que alcance valor faturado exige estorno, crédito ou renegociação antes de reduzir saldo.

### Encerramento e desconto — decisão humana de30/10/2026

- Concluir serviço exige quitação do líquido comercial da sua própria origem, com dinheiro e retenção oficial comprovados por item; crédito ou legado sem identidade requer conciliação. Líquido zero sem compromissos pode concluir.
- DRAFT/ACTIVE impedem concluir contrato; COMPLETED/FINALIZED usam a mesma guarda. Cancelar exige todos os serviços CANCELLED e compromissos resolvidos, sem cascata. Contrato vazio pode encerrar. Reabrir explicitamente antes de criar/reativar serviços ou acrescentar composição em contrato encerrado.
- Transição de cancelamento exige alçada CANCEL, também no PUT geral; aprovação revalida sob lock e202 não altera estado oficial. Alteração só de situação usa dados correntes no servidor, sem snapshots de preço/desconto do cliente.
- Cobrança com saldo oficial aberto bloqueia novo desconto, mesmo semNF; parcela OPEN já recebida não é cobrança aberta. Líquido não pode ser menor que o caixa confirmado líquido de estornos, preservando limites anteriores mais restritivos. Não converter crédito/retenção em caixa nem limpar desconto vigente por atualização operacional. [Escopo e validação](docs/FRONTEND-CONTRACT-LIFECYCLE.md).

### 8.2 Medição

1. Measurement pode ser criada sem Work e sem Contract. Se Contract for informado, Work é obrigatório e ambos devem pertencer à mesma cadeia e Company.
2. O vínculo posterior com Work e, opcionalmente, Contract é uma operação explícita, auditada e sujeita à mesma alçada direta ou aprovação das demais alterações. Uma Work já vinculada não pode ser trocada; um Contract pode ser acrescentado depois, desde que pertença à Work vinculada, e não pode ser trocado silenciosamente.
3. Antes do vínculo, são permitidos cadastro, consulta, itens, desconto, documentos, exportações e registro do aceite externo. Finalização, conversão em ContractService e qualquer efeito financeiro exigem Work e Contract válidos.
4. Aceite é externo ao sistema. A empresa registra internamente o resultado e anexa a evidência quando houver.
5. Itens aceitos ficam congelados. Acréscimo/alteração cria MeasurementVersion nova e exige novo aceite externo.
6. A composição de um grupo estável de serviço de uma versão aceita em ContractService é idempotente e ocorre no máximo uma vez por grupo/revisão. A origem registra medição, versão, grupo, itens, serviço de destino, usuário e justificativa.
7. Medição exibe uma projeção financeira derivada de suas composições/alocações de origem, títulos, baixas e estornos; não possui controle de faturamento independente. Duas medições que complementam o mesmo ContractService não podem compartilhar integralmente o mesmo faturamento/recebimento.
8. Fórmulas iniciais:
   - m²: área × preço por m²;
   - metro linear: metragem × preço por metro;
   - kg/m²: kg por m² × área = kg total; kg total × preço por kg;
   - kg/metro linear: kg por metro × metros = kg total; kg total × preço por kg.
   - unidade: quantidade de unidades × preço por unidade; não produz peso.
   - Nos quatro tipos não-UNIT, `unitQuantity` pode registrar opcionalmente peças/unidades executadas. É informativa, positiva com até quatro casas e não altera fórmula, peso, valor, prévias ou totais oficiais.
9. A exclusão de Measurement é sempre lógica, por status CANCELLED, preservando versões, itens, documentos e auditoria. Só é permitida enquanto não houver Contract vinculado; vínculo apenas com Work não impede o cancelamento.
10. A medição é contínua: preserva `measurementId`, título e identidade de serviço durante vários avanços. A consulta, grade e relatórios acumulados exibem os itens aceitos anteriores no corpo principal junto dos itens do avanço atual, inclusive quando a revisão nova estiver vazia. Histórico isolado não atende a essa visão.
11. Identidade contínua do serviço é independente do grupo aceito por versão. `serviceTitle` nunca é chave de continuidade, destino ou vínculo financeiro. O destino contratual novo/existente deve ser persistido e sobreviver à recarga, validado pelo ID e pela cadeia do tenant.
12. Somente itens da versão `DRAFT` atual podem ser criados, corrigidos, removidos, reorganizados ou movidos por operação comum. Itens aceitos mantêm identidade, preço, ordem histórica e origem congelados. Acrescentar uma atividade com preço diferente não reavalia itens anteriores.
13. Preparar destino, editar/enviar rascunho ou rejeitar aceite não aumenta valor comercial. Aceite com destino/contrato oficial ou vínculo posterior de origens aceitas coordena a composição autorizada do incremento novo, de forma transacional e idempotente. Não reaplicar o total acumulado em cada avanço nem exigir que o frontend crie serviços/atualize valores para reconstruir o efeito.
14. Permissões de medição não concedem permissões de serviço. Aceite externo e aprovação interna da composição são fatos distintos e seus estados/pedidos devem ser expostos separadamente. Uma proposta `202` não altera o dado oficial que pretende aplicar; repetição não duplica serviço, incremento nem pedido pendente para a mesma origem. Reassociar origem aceita/composta exige fluxo formal auditado.
15. A projeção acumulada resolve cópias corretivas, substituições/anulações e reversões antes de calcular totais. Nunca somar indiscriminadamente snapshots de versões corretivas. Itens novos/pendentes têm total separado do aceito; nenhuma pendência produz saldo contratual, NF, recebível ou baixa.
16. O relatório acumulado apresenta total aceito, já recebido e falta receber. `remainingToReceive` é calculado no backend e inclui a parte aceita ainda não faturada, além do faturado ainda devido, com ajustes/retenções/créditos/estornos oficiais. Publicar disponibilidade financeira e instante UTC; sem contrato ou permissão financeira, indicar indisponibilidade sem zeros fictícios, mantendo a exportação dos itens/totais autorizados. O frontend não calcula esse saldo.
17. Exportações acumuladas mantêm streaming, tenant, auditoria, marca privada, título e origem/situação de cada linha, e sua prévia representa o mesmo conteúdo. Relatório por versão preserva o contrato histórico. Importação/reordenação atua somente no avanço editável; linhas aceitas não podem ser inativadas pela ausência em XLSX de rascunho.

## 9. Receita, faturamento e NF — INVARIANTE

1. Faturamento, parcelas, saldo faturável, NFs e contas a receber pertencem exclusivamente a Contract.
2. BillingInstallment/BillingAllocation é a fonte canônica de comprometimento do saldo do contrato. NF e Receivable referenciam a alocação; não podem consumi-lo novamente.
3. Faturamento é alocado a serviços do mesmo Contract. Linhas legadas de desconto contratual são negativas e não possuem serviço. No fluxo líquido F05-21 não há linha negativa: alocações positivas preservam, como metadados imutáveis, a capacidade bruta consumida e o desconto contratual aplicado pelo backend. Limite global, NF e recebíveis usam o líquido; limites de serviço/origem usam a capacidade consumida, sem dupla contagem. Propostas 202 não reservam nem alteram capacidades.
4. A soma líquida de alocações aprovadas/ativas nunca pode ultrapassar valor líquido do Contract, inclusive em requisições concorrentes.
5. Invoice pertence a exatamente um Contract, pode ter vários InvoiceItem e cada item aponta para serviço/alocação do mesmo Contract.
6. Registrar NF não equivale a receber. Recebimento é baixa separada e pode ser parcial.
7. Uma parcela aprovada gera um título de Receivable. Títulos parcelados são registros individuais agrupados por grupo financeiro.
8. Cancelar NF, parcela, recebimento ou pagamento gera cancelamento/estorno rastreável; não apagar histórico.
9. Work consolida receita faturada, emitida em NF e recebida a partir de seus Contracts, sem possuir saldo, NF ou parcela própria.
10. Uma alocação originada em medição preserva a composição específica, do mesmo tenant/contrato/serviço, ao longo de NF, recebível, recebimento parcial e estorno. A referência pode ser ausente para valores sem origem em medição, mas não pode ser omitida para ocultar/rastrear incorretamente uma origem de medição. Limite por composição e limite contratual são validados dentro da mesma transação e sob concorrência.

### Guardrail de recebimentos confirmado em 26/10/2026

- Novas baixas não aceitam `receivedOn` futura: comparar com o calendário UTC do Clock oficial, inclusive solicitação, aprovação e aplicação transacional. Expor `maxReceivedOn` no saldo para orientação do frontend, sem converter a data de negócio em timestamp nem alterar lançamentos antigos.
- Contenção temporária autorizada: bloquear TODOS os novos recebimentos no fluxo legado, mesmo com NF, em aplicação direta, solicitação e aprovação. Usar `409 BUSINESS_RULE_VIOLATION` com motivo controlado `SETTLEMENT_INVOICE_LINK_REQUIRED`; não criar `202`, reserva ou lançamento. Consultas e estornos históricos autorizados continuam disponíveis. Nenhum bypass por configuração ou mera presença de NF no contrato.
- O controle de faturamento pode expor recebido contratual oficial, confirmado e líquido de estornos, apenas com `RECEIVABLES.READ` adicional a `BILLING.READ`; sem a permissão, indisponível/null, não zero. Retenções, créditos, valor de NF e títulos de renegociação não são dinheiro recebido.
- O futuro vínculo manual por NF/item/alocação/origem não está entregue pela contenção. Não ratear automaticamente recebimento entre itens/serviços/NFs, não reconstruir vínculo histórico fictício e não aplicar migration no banco real por autorização limitada a criar/testar. Registrar/cancelar NF não registra baixa nem cancela cobrança automaticamente; não orientar estorno fictício de recebimento verdadeiro para trocar documento.

### Retenção por item de NF — decisão humana de 29/10/2026

O usuário autorizou distribuir **imposto retido** proporcionalmente entre os InvoiceItems positivos da mesma NF, pelos valores brutos oficiais e com centavos determinísticos no backend. Essa autorização específica não permite ratear dinheiro parcial, distribuir entre títulos/cobranças, alterar atribuições históricas ou converter retenção em recebido de caixa. A proposta TOTAL usa os líquidos residuais oficiais e é confirmada explicitamente; POST e aprovação revalidam sob locks. Escopo de título ou base não comprovados e histórico incompatível exigem conciliação controlada, sem zeros fictícios. Não muda a contenção do POST genérico. [Contrato](docs/FRONTEND-INVOICE-ITEM-WITHHOLDING.md).

### Prévia XML e cobertura de serviço cobrado — decisão humana/entrega de 06/11/2026

- Novos registros de NF cobrem um ou mais serviços completos daquela cobrança; parcialidade é entre serviços, nunca fração livre de um serviço já cobrado. Recebimento parcial por NF/item continua permitido. A guarda é comum ao POST manual e à aprovação, revalidada sob lock;202 não reserva nem modifica dado oficial. Não criar bypass no frontend/XML.
- O grupo projetado usa empresa/contrato/cobrança/serviço e TODAS as origens ativas oficiais de base/composição; identidade determinística não é chave financeira substituta. Não agrupar por nome/ContractService isolado entre cobranças nem deixar o cliente inventar subgrupos. GET não congela capacidade; registrar exige IDs/valores exatos atuais. Grupo antigo parcial exige conferência, sem reparo/reassociação/estorno ou reescrita do histórico.
- Cobertura por cobrança considera somente seus InvoiceItems/BillingAllocations registrados, não o total integral da NF replicado. Uma NF pode abranger grupos de cobranças diferentes do mesmo contrato. Valores são comerciais antes de retenções, preservando desconto congelado; não converter imposto em caixa ou somar retenção novamente ao bruto.
- O cliente assistido envia documentTotalAmount revisado em decimal textual; o backend compara com os grupos completos e preserva a confirmação na proposta. O campo é opcional para o cadastro manual anterior e não constitui prova criptográfica de origem do XML. Não calcular/ratear valores no frontend ou alterar dado fiscal para acomodar seleção.
- F05-39/40/41 implementam prévia stateless e cobertura oficial. Ler XML não emite/consulta NF nem valida XSD/assinatura/autenticidade fiscal. Perfil nacional1.01, parser limitado/seguro sem DTD/XXE/XInclude/rede, BigDecimal e proveniência. Somente fixtures sintéticas; modelo real não entra em logs, documentação, fixtures ou repositório.
- DPS tem número/série/emissão próprios. Ausência de dado da NF não autoriza copiá-lo da DPS. Assinatura presente não é assinatura validada. Referência de substituição gera aviso e mantém campos legíveis para conferência; não aciona cancelamento/substituição. Casos fiscais/IBSCBS não suportados e totais/moeda/partes incompatíveis são explicitamente sinalizados, sem confirmação fictícia.
- Base/alíquota/montante/retenção/líquido são apresentados com proveniência quando disponíveis. Ausência não é zero/não retido. Total retido sem componentes não é rateado; percentual informativo não cria dívida. Prévia não cria NF/anexo/TaxObligation/Payable/baixa/reserva/ChangeRequest, apenas auditoria mínima segura de consulta.
- Criação201/202, anexação e tributos permanecem operações separadas e permissionadas; REQUEST_CREATE não ganha upload direto. Pacote atômico/idempotente NF+XML+decisões tributárias F05-42 segue pendente, sem staging/persistência nova ou vários POSTs simulando transação.
- Contrato executável e limites: [guia](docs/FRONTEND-INVOICE-XML-AND-COVERAGE.md). Código testado não significa runtime publicado. Este recorte não criou migration; V92/chave/ativação da outra entrega permanecem separadas, sem autorização implícita de banco persistente.

## 10. Gastos, impostos, lucro e fluxo de caixa — INVARIANTE

1. Todo Payable/gasto possui financial_scope obrigatório: **WORK** ou **COMPANY**.
2. Escopo WORK exige work_id. contract_id é opcional e, se preenchido, deve pertencer à mesma Work.
3. Escopo COMPANY exige work_id e contract_id nulos.
4. Exemplo obrigatório: EPI comprado para a obra usa escopo WORK e pode não informar Contract.
5. Gastos COMPANY, como impostos gerais, despesas administrativas ou custos sem obra, entram em fluxo de caixa e relatórios gerais da empresa, mas nunca no lucro de uma Work.
6. Impostos/retenções de NF herdam a Work pelo Contract. Cada imposto informa tratamento: PAYABLE_BY_COMPANY, WITHHELD_BY_CUSTOMER ou INFORMATIONAL.
7. Apenas imposto PAYABLE_BY_COMPANY cria Payable de escopo WORK. WITHHELD_BY_CUSTOMER ajusta o valor a receber sem criar duplicidade.
8. Resultado previsto de Work = faturamento líquido aprovado dos seus Contracts − impostos, custos e despesas de escopo WORK.
9. Resultado de caixa de Work = recebimentos dos seus Contracts − pagamentos de escopo WORK.
10. Fluxo de caixa geral da empresa inclui títulos WORK e COMPANY; relatórios devem distingui-los.
11. Não criar campo manualmente editável de saldo, receita ou lucro quando ele puder ser calculado de dados aprovados/efetivos.
12. Resultado de Work e resultado corporativo são indicadores distintos. Gastos e tributos de escopo COMPANY entram somente no corporativo; não ratear implicitamente entre obras. Retenção, crédito ou compensação não é despesa definitiva universal e a mesma origem TaxObligation/Payable nunca pode reduzir resultado duas vezes.

### Sequência da Fase 6 alinhada com o frontend — 12/11/2026

- Usar a baixa e o estorno oficiais do `Payable` como fonte dos pagamentos. Entregar primeiro o caixa da empresa com detalhamento por obra, mantendo os escopos `WORK` e `COMPANY` explícitos. Calcular o realizado pela data efetiva de cada baixa e estorno e o previsto pelo vencimento dos saldos oficiais em aberto.
- Comparar o pago líquido de estornos com o intervalo imediatamente anterior de igual duração, usando a mesma moeda, escopo e filtros. Se a cobertura dos períodos não for comparável, sinalizar a indisponibilidade. A sequência completa, o estado da ativação e as decisões em aberto sobre resultado gerencial e margem estão no [backlog da Fase 6](docs/CODEX-IMPLEMENTATION-BACKLOG.md#ordem-de-entrega-alinhada-com-a-fase-9-do-frontend-em-12112026).

### Contatos de fornecedores e instruções de pagamento — 06/11/2026

- Supplier possui um contato principal opcional (nome, telefone, e-mail), independente de RH. Auditoria não copia os dados do contato; o PUT de fornecedor é integral e deve receber os campos atuais para preservá-los.
- Instruções de pagamento pertencem ao Payable individual e são distintas do meio efetivo da baixa. Cadastrar BOLETO/PIX/BANK_TRANSFER ou anexar PDF não altera saldo, valor, status, moeda, escopo ou pagamentos. Não emitir cobrança, resolver favorecido, gerar Pix/boleto ou executar pagamento a partir desse recurso.
- Tenant vem da sessão. Leitura de metadados exige PAYABLES.READ; leitura completa/download/escrita exigem adicionalmente PAYABLES.VIEW_PAYMENT_DETAILS. Mutações seguem alçada UPDATE e aprovação. Aprovação revalida tenant, versão própria da instrução e título aberto; exigir também READ/VIEW. Não autorizar edição cega a partir de metadados.
- Revisões de instrução são imutáveis e protegidas contra UPDATE/DELETE no banco; mudança é nova revisão, sob lock do título e versão esperada. Proposta 202 preserva todo dado oficial. Não levar PDF de favorecido anterior para uma substituição integral; novo anexo exige operação explícita.
- Conteúdo/propostas/PDF usam chave independente de JWT/RH, cifragem autenticada e contexto tenant/título. Auditoria nunca armazena os dados bancários/Pix/códigos/conteúdo do PDF. Storage privado, download autenticado com no-store e sem URL pública. Inspeção pendente só para solicitante/approver autorizado, no título/tenant correto.
- BOLETO pode ser registrado provisoriamente sem linha/barras para permitir anexo PDF-only. Sem código e sem PDF ainda não há referência utilizável; isso não cria estado financeiro novo. A validação estrutural de PDF não equivale a autenticidade bancária nem antimalware.
- Criação de título e gravação de instrução são etapas separadas. Não presumir herança entre fornecedor/lote/títulos, nem pagamento ao anexar documento. V92 pode ser criada/testada em PostgreSQL descartável; sua aplicação no banco persistente depende de autorização específica, conforme 14.1.

## 11. Assinaturas, cobrança e acesso — INVARIANTE

1. A Company precisa escolher ao menos um plano para criar/usar conta e possui teste de 30 dias. O onboarding cria o proprietário inicial pendente, seu papel administrativo inicial e o token de ativação; não há aprovação manual da Company pela plataforma. O trial começa na ativação desse proprietário e, terminado o prazo, a assinatura fica aguardando pagamento até a implementação da cobrança.
2. Planos são cumulativos. Combinações promocionais são configuradas como pacotes; manter snapshot de preço, composição e vigência da contratação.
3. Integração de pagamento usa a porta `PaymentGateway` e adapter. O adapter inicial usa HTTP contra o sandbox do Asaas e permanece isolado em `infrastructure/asaas`; domínio e aplicação não podem depender de DTO, SDK, status ou nome do provedor. A porta recebe solicitações de cobrança com chave de idempotência e traduz o resultado externo para contrato neutro.
4. Webhook chega como payload bruto e cabeçalhos; apenas o adapter do gateway pode autenticá-lo e traduzi-lo em evento neutro. Somente eventos já autenticados podem ser persistidos em `PaymentGatewayEvent`. A idempotência usa código neutro do provedor + identificador externo do evento, com bloqueio transacional e constraint única; repetição conflitante deve falhar. `PlatformCharge` usa código neutro do provedor + chave de idempotência com a mesma proteção e mantém `company_id` coerente com a assinatura por foreign key composta. Eventos persistidos são reprocessáveis, cobranças abertas são reconciliadas periodicamente, e a ordenação temporal impede regressão por evento antigo; o gateway não é a única fonte de verdade.
5. Cartão pode ser cobrado automaticamente; Pix/boleto geram cobranças recorrentes para pagamento do cliente.
6. Regra de inadimplência: no vencimento não pago é criada uma intenção idempotente de aviso; no dia seguinte o acesso da Company vira consulta; em D+5 fica inadimplente em consulta; em D+10 ocorre bloqueio total. A decisão usa a cobrança aberta mais antiga (`PENDING` ou `OVERDUE`), persiste `CompanySubscriptionAccess` e `PlatformChargeNotice` por cobrança/tipo, e é aplicada globalmente apenas nas rotas `/api/v1/company` (GET/HEAD/OPTIONS são consulta). O job diário bloqueia as cobranças da Company antes de recalcular. A entrega usa `NotificationPort`, sem acoplar SMTP ao domínio de assinaturas, e registra `PENDING_DELIVERY`, `DELIVERING`, `DELIVERED`, `DELIVERY_FAILED` ou `CANCELLED`, tentativas e auditoria `SYSTEM` desde o claim; avisos de cobrança já paga são cancelados e falhas só podem ser tentadas novamente após o intervalo configurado, sem vazar mensagem do provedor.
7. Há no máximo dois `TrustUnlock` por cobrança vencida ao longo do histórico. Somente o proprietário da plataforma pode concedê-lo, sempre para uma cobrança `PENDING` ou `OVERDUE` após o vencimento, registrando autor, motivo, início imediato e expiração futura. Enquanto ativo, ele ignora apenas aquela cobrança no cálculo de acesso; outra cobrança vencida sem desbloqueio continua restringindo a Company. A expiração deve ser avaliada em cada requisição protegida, sem aguardar o scheduler diário.

## 12. Documentos, relatórios e integrações

- Geração de Excel, PDF e DOCX usa adapters testáveis.
- Anexos permitidos incluem PDF, XLSX, CSV, DOCX e XML, com validação de tipo/tamanho, hash e auditoria.
- Anexos PDF/XML de NF devem ser validados por conteúdo antes do storage, além de MIME/extensão/tamanho: PDF legível, com página e sem criptografia; XML sem DOCTYPE, entidades externas ou XInclude, com parser seguro e limites estruturais. Isso não substitui antimalware, validação fiscal, assinatura ou schema de NF.
- Storage deve ser abstraído por StorageProvider; desenvolvimento pode usar disco local controlado e produção usa object storage.
- Relatórios operacionais usam apenas dados aprovados/efetivos, salvo filtro explícito de pendências.
- Relatórios mínimos: clientes finais ativos; valores a receber; contratos abertos/finalizados/a receber; controle de faturamento; gastos e resultado por obra; gastos gerais da empresa.
- Não implementar emissão/consulta de NF municipal/SEFAZ sem autorização.

### Planejamento de H.H., lotação e ponto

- H.H., lotação por obra, transferência temporária, acesso por obra, apontamento e integração de ponto permanecem planejamento até tarefa explícita. Não criar endpoints, DTOs, migrations ou cálculos de faturamento fictícios a partir desse planejamento.
- Priorização humana de 26/10/2026: simplificar as telas financeiras existentes, depois H.H. manual simples e vencimentos da equipe. É autorização de planejamento/prioridade, não de implementação ou publicação; a API backend não implementa telas. Referenciar SDD 3.4/5.7.1/5.9 e F10/F12 existentes, sem duplicar fases ou tarefas nem contornar gates de segurança.
- O recorte manual planejado registra funcionário e vínculo histórico, obra, serviço, dia e horas explicitamente informadas; permite correção rastreável e resumo oficial por funcionário/período/serviço. Não depende de ponto, espelho, folha, diária ou fórmula de DSR. Mantém tenant, identidade estável, histórico, alçada/aprovação e autorização por obra; reduzir o recorte não concede acesso a RH/saúde nem dispensa a proteção necessária.
- Horas apontadas, corrigidas ou resumidas não geram automaticamente aceite comercial, faturamento, folha ou pagamento. Não inventar intervalos, jornada, divisor salarial ou conversão de dia em horas. Quantidades pendentes não entram como efetivas; serviço/contrato devem ter cadeia validada quando vinculados, sem criar hierarquia financeira paralela.
- Serviço por diária também permanece planejamento (SDD 5.7/F12-12), como modalidade comercial distinta de H.H. por horas efetivamente trabalhadas e da diária corrida de locação. Não presumir oito horas por diária, converter automaticamente em H.H. ou cobrar fins de semana de serviço sem regra definida.
- H.H. comercial, custo trabalhista, preço de venda, marcação de ponto e quantidade aprovada/faturável são fatos distintos. O faturamento continua em Contract → ContractService; não alterar a hierarquia, o empregador ou criar título financeiro paralelo.
- A intenção comercial relativa a DSR não autoriza fórmula: não presumir 1/6, semana, dia, elegibilidade, rateio, tratamento de semana parcial, múltiplos tomadores, atestado ou feriado. DSR de folha não se torna automaticamente item contratual nem descanso vira hora trabalhada.
- Sistema de ponto futuro preserva marcações originais e trata ausência de cadastro/alocação como divergência conciliável, não como descarte/bloqueio silencioso. Atestado não é hora trabalhada/faturável. Validar CLT, Portaria 671 e CCT/ACT aplicável antes de anunciar conformidade.

### Planejamento do Diário de Obra

- Aplicar o [contrato funcional F13-01](docs/SDD-MVP-Gestao-Financeira.md#contrato-funcional-f13-01) nos recortes de implementação da [Fase 13 do backlog](docs/CODEX-IMPLEMENTATION-BACKLOG.md#fase-13--diário-de-obra). Reaproveitar lotação, autorização por obra e a fonte de apontamentos manuais de H.H. da Fase 12. O contrato funcional é documental. A publicação de contratos HTTP depende da implementação correspondente.
- A lotação funcionário–obra é opcional na empresa. Quando usada, validar tenant, obra, vigência e vínculo de trabalho histórico, preservando readmissão e correções. A participação avulsa no dia exige validação da mesma cadeia e registro explícito. Nenhuma associação operacional concede acesso ao usuário.
- Exigir permissão por operação e atribuição explícita à obra para preencher, consultar, corrigir, decidir e exportar. Aplicar o escopo no backend, inclusive nas seleções, pendências e auditoria. Revalidar a autorização nas mutações e decisões. Manter RH, saúde e custos com permissões próprias e projeções mínimas. Não conceder `EMPLOYEES.READ` ao apontador apenas para selecionar a equipe.
- Suportar vários funcionários na mesma atividade/serviço e contribuições distintas por pessoa. Validar o serviço contratado pela cadeia `ContractService → Contract → Work` da empresa. Atividade geral pode ter referência operacional sem atribuição contratual. Preservar planejado e realizado com origens próprias, motivos de atraso e impacto quando comprovado. Não duplicar horas por presença, serviço ou ocorrência, nem inventar intervalos, jornada ou plano.
- Corrigir de forma rastreável com versão, alçada, idempotência e proteção contra concorrência. Proposta `202` não modifica execução ou resumo efetivo. Registros do Diário não geram aceite comercial, faturamento, folha, títulos ou baixas.
- Apuração por serviço, contrato e obra depende de fonte, data-base, moeda, atribuição comprovada, cobertura e permissão financeira. Preservar as fórmulas de obra da §10. Contar cada origem econômica uma vez na base aprovada, inclusive quando estimativa H.H., folha e conta a pagar representarem o mesmo custo. Deduplicar TaxObligation/Payable por vínculo oficial. Manter custos sem atribuição no nível comprovado, valores COMPANY no corporativo, e lacunas de cobertura explícitas. Não inferir custo-hora de salário nem ratear por horas sem regra autorizada.

### Planejamento de vencimentos da equipe

- O mínimo priorizado em 26/10/2026 abrange contrato/vínculo de trabalho do funcionário com a Company, cursos/certificados e exames periódicos, não apenas contrato comercial ou lotação em obra. Reutilizar F10-01 a F10-10, com metadados/datas primeiro; documentos privados completos e canais externos são expansões, sem dispensar proteção de RH/saúde.
- Usar somente datas explicitamente cadastradas. Planejar painel de próximos, vencidos e sem data, com antecedência configurável e alertas internos idempotentes para destinatários autorizados. Não presumir prazo legal, periodicidade, renovação, aptidão médica, efetivação, desligamento ou bloqueio automático. Sem data não significa vencido, irregular ou inapto.
- Não incluir diagnóstico, prontuário ou resultado clínico. Conhecer/apontar horas de um funcionário não concede acesso a seus dados de RH/saúde, inclusive no painel/alerta. Definir granularidade e projeções mínimas antes da implementação; não alegar que EMPLOYEES.READ já separa esses acessos.
- Precisão das horas, identidade do serviço, critérios de duplicidade/correção, fuso, limites dos períodos/vencimentos, tratamento de sem data, destinatários e cadência interna permanecem decisões a detalhar. Não publicar endpoint/enum/DTO fictício, enviar notificações externas, iniciar módulos/infraestrutura ou aplicar migrations por este planejamento.

### Planejamento de locações e lembretes

- Locações mensais fixas ou por diária/quantidade de equipamentos e lembretes de contas de aluguel permanecem somente planejamento (SDD 5.5.1/F06-15 a F06-19). Não criar rotas, DTOs, enums, migrations, recorrência, pagamentos ou envios a partir desse registro.
- Diária de ferramenta/equipamento considera dias corridos durante a locação, inclusive fins de semana. Inclusão de entrega/devolução, frações, proporcionalidade mensal, fechamento e devoluções parciais exigem definição antes de implementar. Essa regra não se transfere automaticamente ao serviço por diária.
- Encerrar locação não quita nem apaga títulos anteriores. Geração e lembretes futuros exigem regras oficiais, tenant, alçada/aprovação, idempotência e auditoria; canais, destinatários, antecedências, cadência e fuso ainda devem ser definidos. Não reutilizar dunning da assinatura SaaS como política de contas de aluguel.
- No planejamento de aluguel/serviço e contas correspondentes, forma prevista de cobrança/pagamento inclui boleto e outras formas ainda a definir; ela é distinta da unidade/periodicidade e do meio efetivo da baixa. Catálogo, obrigatoriedade, herança/alteração no título, instruções/anexos e pagamentos parciais por meios distintos continuam pendentes. Registrar boleto não autoriza emissão, integração bancária, pagamento ou confirmação automática, nem reutilização do enum de recebimentos como contrato fictício de Payable.

## 13. Qualidade, migrations e entrega

Antes de considerar uma tarefa concluída:

1. criar/atualizar migration Flyway;
2. implementar validações, autorização e auditoria aplicáveis;
3. incluir testes unitários de regra de negócio;
4. incluir testes de integração com PostgreSQL/Testcontainers quando houver persistência, tenant, saldo ou transação;
5. testar caso permitido e caso negado;
6. documentar endpoint no OpenAPI quando houver API nova;
7. manter paginação, filtros, ordenação, erro padronizado e correlação de logs;
8. não expor segredo, senha, token, cartão ou conteúdo fiscal sensível em resposta/log;
9. preservar alterações não relacionadas já existentes no repositório.
10. para medição contínua, entregar conjuntamente visão acumulada, destino persistido, composição autorizada/idempotente, resumo financeiro correto e relatórios acumulados com testes focados. Um resumo financeiro isolado não encerra a integração; documentar recortes parciais no backlog e somente publicar rotas/DTOs existentes no OpenAPI gerado da aplicação.

## 14. Exigir autorização explícita do usuário antes de

- alterar a hierarquia Company → FinalCustomer → Work → Contract → ContractService;
- trocar o nível dono de faturamento/saldo de Contract para Work;
- mudar fórmula de medição, descontos, limite de faturamento ou escopo de gasto;
- permitir exclusão física de domínio financeiro, contratos, obras, medições ou auditoria;
- alterar retenção de auditoria;
- permitir usuário de empresa em mais de uma Company;
- enfraquecer isolamento multiempresa, regra de aprovação ou auditoria;
- conceder a suporte poder de aprovar, excluir, mudar planos, dados estruturais ou papéis da empresa;
- criar emissão fiscal, integração bancária, frontend, app móvel ou portal de aceite;
- trocar gateway, banco, stack, arquitetura modular ou estratégia Flyway;
- fazer migration destrutiva, reset de dados ou alteração irreversível.

### 14.1 Atualização local necessária ao pedido humano — decisão de 29/10/2026

O usuário determinou: **“pode mudar isso, se foi eu quem pediu atualize”**. Pedidos humanos de implementação ou correção também autorizam atualizar/reiniciar os componentes **locais** necessários para disponibilizar o mesmo recorte, inclusive a API em `localhost:8081`, após implementação, testes adequados, pacote concreto revisado, conferência da referência ativa, preservação de versão compatível para retorno e verificação das evidências. Não pedir nova confirmação apenas porque o pacote mudou dentro do trabalho solicitado.

Essa autorização não se aplica a pedidos somente de análise/documentação, mudanças não relacionadas, aplicação de migrations/seeds ou alteração do banco persistente do usuário, operações reais de negócio, publicação remota/produção ou infraestrutura não incluída explicitamente. Criar/testar uma migration em banco temporário não autoriza aplicá-la no banco real. Restrições humanas específicas continuam prevalecendo, assim como as invariantes e os demais limites da seção14. Se a referência ativa divergir da revisada, diagnosticar e revisar o escopo antes de substituir; não trocar uma versão desconhecida por inferência. Permissões técnicas do ambiente não substituem autorização de domínio.

