# Instruções executáveis — Plataforma SaaS de Gestão Financeira de Obras

Estas regras são obrigatórias para qualquer agente que analisar, modificar ou implementar este repositório.

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
- Desconto de serviço altera o valor líquido do serviço e o subtotal do contrato.
- Desconto de contrato é ajuste negativo separado, sem ContractService associado; não regrava, altera ou rateia o preço dos serviços.
- Não permitir redução de contrato para valor líquido inferior ao faturamento líquido aprovado sem estorno, crédito ou renegociação auditada.
- Não fazer exclusão física: use status, cancelamento, inativação ou estorno.
- Contract possui `pricing_mode`: `FIXED` preserva o valor-base negociado; `MEASUREMENT_BASED` forma o valor comercial progressivamente por composições de versões de medição aceitas.
- Composição de medição é imutável, idempotente por grupo estável de serviço/versão e auditável. Ela não substitui o valor-base de `ContractService`; a posição efetiva do serviço soma valor-base líquido e composições aceitas. `serviceTitle` não é chave financeira.
- Correção posterior de origem já composta exige reversão/substituição rastreável. Redução que alcance valor faturado exige estorno, crédito ou renegociação antes de reduzir saldo.

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
3. Faturamento é alocado a serviços do mesmo Contract. Uma linha de desconto contratual é negativa e não possui serviço.
4. A soma líquida de alocações aprovadas/ativas nunca pode ultrapassar valor líquido do Contract, inclusive em requisições concorrentes.
5. Invoice pertence a exatamente um Contract, pode ter vários InvoiceItem e cada item aponta para serviço/alocação do mesmo Contract.
6. Registrar NF não equivale a receber. Recebimento é baixa separada e pode ser parcial.
7. Uma parcela aprovada gera um título de Receivable. Títulos parcelados são registros individuais agrupados por grupo financeiro.
8. Cancelar NF, parcela, recebimento ou pagamento gera cancelamento/estorno rastreável; não apagar histórico.
9. Work consolida receita faturada, emitida em NF e recebida a partir de seus Contracts, sem possuir saldo, NF ou parcela própria.
10. Uma alocação originada em medição preserva a composição específica, do mesmo tenant/contrato/serviço, ao longo de NF, recebível, recebimento parcial e estorno. A referência pode ser ausente para valores sem origem em medição, mas não pode ser omitida para ocultar/rastrear incorretamente uma origem de medição. Limite por composição e limite contratual são validados dentro da mesma transação e sob concorrência.

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
- Storage deve ser abstraído por StorageProvider; desenvolvimento pode usar disco local controlado e produção usa object storage.
- Relatórios operacionais usam apenas dados aprovados/efetivos, salvo filtro explícito de pendências.
- Relatórios mínimos: clientes finais ativos; valores a receber; contratos abertos/finalizados/a receber; controle de faturamento; gastos e resultado por obra; gastos gerais da empresa.
- Não implementar emissão/consulta de NF municipal/SEFAZ sem autorização.

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

