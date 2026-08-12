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

## 8. Obras, contratos, serviços e medições — INVARIANTE

### 8.1 Obra e contrato

- A obra concentra receita, custos, gastos vinculados e lucro de todos os seus contratos.
- Contract mantém somente seu próprio valor, serviços, faturamento, saldo, parcelas, NFs e contas a receber.
- O valor líquido de um Contract é o limite de faturamento líquido desse Contract; valores consolidados da Work nunca alteram esse limite.
- Desconto de serviço altera o valor líquido do serviço e o subtotal do contrato.
- Desconto de contrato é ajuste negativo separado, sem ContractService associado; não regrava, altera ou rateia o preço dos serviços.
- Não permitir redução de contrato para valor líquido inferior ao faturamento líquido aprovado sem estorno, crédito ou renegociação auditada.
- Não fazer exclusão física: use status, cancelamento, inativação ou estorno.

### 8.2 Medição

1. Measurement pertence sempre a uma Work e pode não ter Contract inicialmente.
2. Antes de produzir efeito financeiro, ela deve se ligar a um Contract existente da mesma Work ou criar Contract novo nessa Work.
3. Aceite é externo ao sistema. A empresa registra internamente o resultado e anexa a evidência quando houver.
4. Itens aceitos ficam congelados. Acréscimo/alteração cria MeasurementVersion nova e exige novo aceite externo.
5. A conversão de MeasurementItem aceito em ContractService é idempotente e ocorre no máximo uma vez por item/revisão.
6. Medição exibe faturado e saldo derivados dos serviços que originou; não possui controle de faturamento independente.
7. Fórmulas iniciais:
   - m²: área × preço por m²;
   - metro linear: metragem × preço por metro;
   - kg/m²: kg por m² × área = kg total; kg total × preço por kg;
   - kg/metro linear: kg por metro × metros = kg total; kg total × preço por kg.

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

1. A Company precisa escolher ao menos um plano para criar/usar conta e possui teste de 30 dias. O onboarding cria o proprietário inicial pendente, seu papel administrativo inicial e o token de ativação; não há aprovação manual da Company pela plataforma.
2. Planos são cumulativos. Combinações promocionais são configuradas como pacotes; manter snapshot de preço, composição e vigência da contratação.
3. Integração de pagamento usa porta/adaptador. Asaas é a recomendação inicial, mas o domínio não pode ficar acoplado ao SDK/provedor.
4. Eventos de webhook são autenticados, idempotentes, persistidos e reconciliados; o gateway não é a única fonte de verdade.
5. Cartão pode ser cobrado automaticamente; Pix/boleto geram cobranças recorrentes para pagamento do cliente.
6. Regra de inadimplência: cobrança vencida envia e-mail; acesso vira consulta; a partir de 5 dias fica inadimplente em consulta; aproximadamente a partir de 10 dias ocorre bloqueio total.
7. Há no máximo dois desbloqueios de confiança por cobrança vencida. Cada um registra autor, motivo, início e expiração.

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

