# Backlog executável para Codex — API de Gestão Financeira de Obras

Este backlog deve ser executado em ordem de dependência. As regras de [AGENTS.md](../AGENTS.md) são obrigatórias para cada tarefa.

Cada linha é o limite saudável de uma tarefa Codex: não delegue uma fase inteira a um único agente.

## Perfis de Codex

| Rótulo | Configuração recomendada | Use para |
|---|---|---|
| Terra leve | gpt-5.6-terra / low | Edição mecânica, enums, DTOs, documentação e testes simples. |
| Terra médio | gpt-5.6-terra / medium | CRUD delimitado, migration simples e integração local. |
| Terra alto | gpt-5.6-terra / high | Módulo com regras, autorização, testes de integração e vários arquivos. |
| Terra xhigh | gpt-5.6-terra / xhigh | Alteração transversal, domínio sensível, concorrência e integrações. |
| Terra ultra | gpt-5.6-terra / ultra | Fluxo financeiro crítico, aprovação genérica, segurança complexa ou correção difícil. |
| Sol xhigh | gpt-5.6-sol / xhigh | Revisão de arquitetura, segurança e modelo de dados antes de implementar. |
| Sol ultra | gpt-5.6-sol / ultra | Auditoria independente de invariantes financeiros/multiempresa antes de merge. |

Regras para escolher o perfil:

- Não usar Terra leve para alterar autorização, auditoria, migrations financeiras, saldo, descontos, cobranças ou webhooks.
- Tarefas marcadas Sol são revisões críticas; não substituem os testes.
- Se apenas Terra estiver disponível, execute tarefas Sol com Terra ultra e exija revisão humana antes de integrar.
- Todo item que cria/modifica dados deve respeitar AGENTS.md, migration, autorização, auditoria e testes aplicáveis.

## Fase 0 — Fundação técnica e migração do esqueleto

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F00-00 | Registrar ADRs de tenancy, UUID, dinheiro/arredondamento, datas UTC, soft delete e aprovação. | — | Terra alto |
| F00-01 | Ler AGENTS.md e SDD; registrar decisões/invariantes impactados pela tarefa. | F00-00 | Terra médio |
| F00-02 | Criar o esqueleto de pacotes modular sem mover comportamento ainda. | F00-00 | Terra médio |
| F00-03 | Corrigir nome e metadados OpenAPI para o produto atual, sem manter referências ObraFlow. | F00-01 | Terra leve |
| F00-04 | Criar configuração por perfil local/test/prod e mover credenciais para variáveis de ambiente. | F00-01 | Terra médio |
| F00-05 | Criar Dockerfile da API e completar Docker Compose de desenvolvimento. | F00-04 | Terra médio |
| F00-06 | Padronizar Problem Details, códigos de erro, paginação, filtros e ID de correlação. | F00-02 | Terra alto |
| F00-07 | Corrigir o update/soft delete de Company preservando ID e validar com teste de integração. | F00-02 | Terra alto |
| F00-08 | Alinhar constraints/timestamps da entidade Company e migration Flyway por nova migration. | F00-07 | Terra alto |
| F00-09 | Configurar base de testes PostgreSQL com Testcontainers e execução de migrations. | F00-04 | Terra alto |
| F00-10 | Configurar lint/análise estática, cobertura mínima e pipeline de testes. | F00-09 | Terra médio |
| F00-11 | Revisar estrutura modular, migrations e configuração de segredos antes da próxima fase. | F00-02 a F00-10 | Sol xhigh |

**Gate da fase:** aplicação sobe via Docker, migrations funcionam em banco vazio, Company não duplica em update e testes de integração rodam com PostgreSQL real.

## Fase 1 — Identidade, tenant, papéis, aprovação e auditoria

**Progresso atual:** F01-01 a F01-18 concluídas. F01-19 permanece pendente.

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F01-01 | Concluído | Criar migration e domínio de User, CompanyUser e PlatformUser. | F00-09 | Terra alto |
| F01-02 | Concluído | Implementar hash de senha, ativação de conta e política de senha. | F01-01 | Terra alto |
| F01-03 | Concluído | Implementar login, access token, refresh token, logout e revogação. | F01-02 | Terra alto |
| F01-04 | Concluído | Implementar recuperação e redefinição de senha por token/e-mail. | F01-02 | Terra médio |
| F01-05 | Concluído | Criar TenantContext confiável a partir da autenticação. | F01-03 | Sol xhigh |
| F01-06 | Concluído | Criar teste negativo de leitura/mutação entre duas empresas. | F01-05 | Terra xhigh |
| F01-07 | Concluído | Criar Role, Permission, RolePermission e UserRole com permissões por módulo/ação. | F01-01 | Terra xhigh |
| F01-08 | Concluído | Criar catálogo inicial de permissões: consulta; criação, edição e cancelamento diretos; solicitação, aprovação, rejeição, exportação, envio e gestão. | F01-07 | Terra alto |
| F01-09 | Concluído | Criar endpoints para empresa gerir funcionários, perfis e atribuições permitidas. | F01-07 | Terra alto |
| F01-10 | Concluído | Implementar autorização de caso de uso, não apenas anotações de controller, incluindo a alçada direta efetiva por módulo/operação. | F01-05, F01-07 | Sol xhigh |
| F01-11 | Concluído | Criar usuário global de suporte e contexto seguro de empresa-alvo. | F01-10 | Terra xhigh |
| F01-12 | Concluído | Bloquear suporte de aprovar, excluir, alterar planos, dados estruturais e papéis. | F01-11 | Terra xhigh |
| F01-13 | Concluído | Criar migration/domínio de AuditEvent imutável. | F01-01 | Terra xhigh |
| F01-14 | Concluído | Criar interceptador/serviço para auditoria de ações diretas, antes/depois e ator. | F01-13 | Terra ultra |
| F01-15 | Concluído | Criar ChangeRequest genérica com snapshot, versão, solicitante e estado. | F01-10, F01-13 | Sol xhigh |
| F01-16 | Concluído | Implementar aprovação/rejeição atômica, motivo obrigatório e bloqueio de autoaprovação. | F01-15 | Sol xhigh |
| F01-17 | Concluído | Garantir que pendências não aparecem em consultas/relatórios operacionais. | F01-16 | Terra xhigh |
| F01-18 | Concluído | Auditar atuação de suporte e envio de relatório. | F01-11, F01-14 | Terra alto |
| F01-19 | Pendente | Revisar isolamento, permissões, autoaprovação e imutabilidade de auditoria. | F01-05 a F01-18 | Sol ultra |

**Gate da fase:** usuário só vê seu tenant; suporte tem alçada limitada; alteração sem alçada direta efetiva não muda dado oficial; autoaprovação de solicitação é impossível; todos os testes de segurança passam.

## Fase 2 — Plataforma, planos, trial e cobrança SaaS

**Progresso atual:** F02-01 a F02-05 concluídas. F02-06 a F02-14 permanecem pendentes.

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F02-01 | Concluído | Modelar Plan, módulo/feature, preço e vigência. | F01-10 | Terra alto |
| F02-02 | Concluído | Modelar PlanBundle e regras de preço para combinações cumulativas. | F02-01 | Terra xhigh |
| F02-03 | Concluído | Criar administração de planos e pacotes apenas para proprietário da plataforma. | F02-01 | Terra alto |
| F02-04 | Concluído | Criar onboarding público da Company com seleção obrigatória de plano, proprietário pendente, papel `Company Administrator`, permissões ativas, ativação por token e auditoria imutável. | F01-03, F01-07, F01-14, F02-01 | Terra xhigh |
| F02-05 | Concluído | Criar Subscription, SubscriptionItem e snapshot imutável de preço/composição. | F02-02, F02-04 | Terra xhigh |
| F02-06 | Pendente | Implementar trial de 30 dias e transições locais de assinatura. | F02-05 | Terra alto |
| F02-07 | Pendente | Definir PaymentGateway como porta de aplicação e contratos de webhook. | F02-05 | Terra xhigh |
| F02-08 | Pendente | Implementar adapter Asaas em sandbox, sem vazar SDK para domínio. | F02-07 | Sol xhigh |
| F02-09 | Pendente | Persistir PlatformCharge e PaymentGatewayEvent com idempotência. | F02-08 | Sol xhigh |
| F02-10 | Pendente | Verificar assinatura, reprocessamento e reconciliação periódica de webhooks. | F02-09 | Sol xhigh |
| F02-11 | Pendente | Implementar avisos de vencimento, acesso de consulta, inadimplência em D+5 e bloqueio em D+10. | F02-09 | Terra ultra |
| F02-12 | Pendente | Implementar TrustUnlock: máximo de dois por cobrança vencida, com expiração e auditoria. | F02-11 | Terra xhigh |
| F02-13 | Pendente | Implementar e-mails de cobrança/aviso com NotificationPort. | F02-11 | Terra alto |
| F02-14 | Pendente | Revisar precificação cumulativa, webhooks, dunning e acesso bloqueado. | F02-01 a F02-13 | Sol ultra |

**Gate da fase:** uma empresa inicia trial, possui snapshot correto de planos, recebe eventos idempotentes e tem acesso limitado/bloqueado conforme cobrança.

## Fase 3 — Clientes finais, obras, contratos e serviços

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F03-01 | Criar FinalCustomer e migration final_customer com company_id e unicidades por tenant. | F01-10 | Terra alto |
| F03-02 | Criar API de cliente final, contatos e endereços com autorização/auditoria. | F03-01, F01-14 | Terra alto |
| F03-03 | Criar Work e migration obra com final_customer_id, company_id, status e datas. | F03-01 | Terra alto |
| F03-04 | Modelar local de execução: cliente final, unidade da prestadora ou outro endereço. | F03-03 | Terra médio |
| F03-05 | Criar API de obra e validar que o cliente final pertence ao mesmo tenant. | F03-03, F01-10 | Terra alto |
| F03-06 | Criar Contract com work_id obrigatório e status; impedir final_customer_id independente. | F03-03 | Terra xhigh |
| F03-07 | Criar API de contrato, permitindo contrato sem serviço. | F03-06, F01-14 | Terra alto |
| F03-08 | Criar ServiceTemplate e cópia explícita para ContractService. | F03-06 | Terra alto |
| F03-09 | Criar ContractService, status e valores monetários. | F03-08 | Terra alto |
| F03-10 | Implementar desconto fixo/percentual no serviço. | F03-09 | Terra alto |
| F03-11 | Implementar desconto fixo/percentual no contrato como ajuste separado. | F03-09 | Terra xhigh |
| F03-12 | Implementar cálculo de valor líquido do contrato e bloqueio de valor negativo. | F03-10, F03-11 | Terra xhigh |
| F03-13 | Implementar revisão de contrato que bloqueia redução abaixo de faturamento aprovado. | F03-12 | Sol xhigh |
| F03-14 | Testar hierarquia FinalCustomer → Work → Contract → ContractService e soft delete/status. | F03-01 a F03-13 | Terra xhigh |
| F03-15 | Revisar modelo, descontos, tenant e constraints antes de faturamento. | F03-01 a F03-14 | Sol xhigh |

**Gate da fase:** obra é entidade própria; contrato pertence a uma obra; serviço pertence a um contrato; valores e descontos preservam os invariantes.

## Fase 4 — Medições e documentos de medição

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F04-01 | Criar Measurement vinculada obrigatoriamente a Work e Contract opcional da mesma Work. | F03-06 | Terra xhigh |
| F04-02 | Criar MeasurementVersion, MeasurementItem e estados de negócio. | F04-01 | Terra alto |
| F04-03 | Implementar tipo m² e fórmula área × preço por m². | F04-02 | Terra médio |
| F04-04 | Implementar tipo metro linear e fórmula metragem × preço por metro. | F04-02 | Terra médio |
| F04-05 | Implementar tipo kg/m² e cálculo de kg total/valor total. | F04-02 | Terra alto |
| F04-06 | Implementar tipo kg/metro linear e cálculo de kg total/valor total. | F04-02 | Terra alto |
| F04-07 | Implementar desconto de cabeçalho da medição como ajuste separado e auditado. | F04-03 a F04-06 | Terra xhigh |
| F04-08 | Criar fluxo rascunho → enviada → aceite externo registrado → aceita/finalizada. | F04-02 | Terra xhigh |
| F04-09 | Criar revisão para acréscimo após aceite, congelando itens aceitos. | F04-08 | Terra ultra |
| F04-10 | Criar StorageProvider e upload validado de evidência/planilha. | F00-04 | Terra alto |
| F04-11 | Gerar planilha XLSX de medição. | F04-02 | Terra alto |
| F04-12 | Gerar PDF de medição. | F04-02 | Terra alto |
| F04-13 | Converter item aceito em ContractService uma única vez, de forma idempotente. | F04-09, F03-09 | Sol xhigh |
| F04-14 | Calcular faturado/saldo de medição apenas pelos serviços que ela originou. | F04-13 | Terra xhigh |
| F04-15 | Testar fórmulas, revisões, aceites, idempotência e tenant. | F04-01 a F04-14 | Terra ultra |
| F04-16 | Revisar cálculo e conversão medição → serviço antes de merge. | F04-01 a F04-15 | Sol ultra |

**Gate da fase:** a medição é rastreável, aceita externamente, versionada e gera serviços sem duplicidade.

## Fase 5 — Faturamento, NF, contas a receber e recebimentos

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F05-01 | Criar BillingInstallment e BillingAllocation por Contract. | F03-12 | Terra xhigh |
| F05-02 | Separar alocação de serviço positiva e ajuste de desconto contratual negativo. | F05-01 | Terra xhigh |
| F05-03 | Implementar cálculo de saldo faturável canônico pelo conjunto de BillingAllocation. | F05-01 | Sol ultra |
| F05-04 | Bloquear excesso de faturamento sob concorrência com versão/transação. | F05-03 | Sol ultra |
| F05-05 | Criar parcela aprovada e título Receivable individual/grupo financeiro. | F05-01 | Terra xhigh |
| F05-06 | Criar Invoice e InvoiceItem, exigindo um único Contract e alocações compatíveis. | F05-02 | Terra xhigh |
| F05-07 | Permitir NF de serviço parcialmente faturado em várias NFs sem dupla contagem. | F05-06 | Sol xhigh |
| F05-08 | Criar TaxObligation com tratamento a pagar, retido ou informativo. | F05-06 | Terra xhigh |
| F05-09 | Criar anexos PDF/XML de NF com auditoria. | F05-06, F04-10 | Terra alto |
| F05-10 | Criar ReceivableSettlement para recebimento parcial, total e atraso. | F05-05 | Terra xhigh |
| F05-11 | Implementar cancelamento/estorno de parcela, NF e recebimento com efeitos atômicos. | F05-03, F05-10 | Sol xhigh |
| F05-12 | Criar filtros/API de controle de faturamento exclusivamente por Contract. | F05-03 | Terra alto |
| F05-13 | Testar saldo, concorrência, item de NF, recebimento parcial e estornos. | F05-01 a F05-12 | Terra ultra |
| F05-14 | Revisar invariantes de saldo, desconto, NF e recebimento. | F05-01 a F05-13 | Sol ultra |

**Gate da fase:** nenhuma combinação de parcelas/NFs/recebimentos ultrapassa contrato ou conta duas vezes o mesmo valor.

## Fase 6 — Gastos, contas a pagar, impostos e resultado por obra

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F06-01 | Criar Supplier e categorias financeiras. | F01-10 | Terra médio |
| F06-02 | Criar Payable com financial_scope WORK/COMPANY e grupo financeiro. | F06-01, F03-03 | Terra xhigh |
| F06-03 | Validar WORK: work_id obrigatório e contract_id opcional da mesma obra. | F06-02 | Terra xhigh |
| F06-04 | Validar COMPANY: work_id e contract_id obrigatoriamente nulos. | F06-02 | Terra alto |
| F06-05 | Criar API de gasto de obra, incluindo EPI sem Contract. | F06-03 | Terra alto |
| F06-06 | Criar API de gasto geral da empresa, excluído do lucro de obra. | F06-04 | Terra alto |
| F06-07 | Criar PayableSettlement para pagamento parcial/total e atraso. | F06-02 | Terra xhigh |
| F06-08 | Criar contas a pagar automáticas de imposto oriundo de NF quando aplicável. | F05-08, F06-02 | Terra xhigh |
| F06-09 | Implementar estorno/cancelamento de pagamento e reabertura do título. | F06-07 | Terra xhigh |
| F06-10 | Criar projeção de receita faturada, emitida e recebida da Work a partir de Contracts. | F05-10 | Terra xhigh |
| F06-11 | Calcular resultado previsto da Work sem incluir gastos COMPANY. | F06-05, F06-06, F06-10 | Sol xhigh |
| F06-12 | Calcular resultado de caixa da Work e fluxo de caixa geral da Company. | F06-07, F06-10 | Sol xhigh |
| F06-13 | Testar EPI por obra, gasto empresa, impostos, lucro e fluxo de caixa. | F06-01 a F06-12 | Terra ultra |
| F06-14 | Revisar segregação Work/Company e fórmulas financeiras. | F06-01 a F06-13 | Sol ultra |

**Gate da fase:** EPI pode pertencer apenas à obra; gasto geral não entra em lucro de obra; receitas vêm dos contratos; saldos de contrato permanecem independentes.

## Fase 7 — Relatórios, exportações e envio

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F07-01 | Criar consultas paginadas e filtráveis de clientes finais ativos. | F03-02 | Terra médio |
| F07-02 | Criar relatório de valores a receber por cliente/contrato. | F05-10 | Terra alto |
| F07-03 | Criar relatório de contratos abertos, finalizados e a receber. | F05-12 | Terra alto |
| F07-04 | Criar relatório de controle de faturamento por contrato/serviço. | F05-12 | Terra xhigh |
| F07-05 | Criar relatório de gastos e resultado por Work. | F06-11 | Terra xhigh |
| F07-06 | Criar relatório de gastos gerais da Company, separado de Works. | F06-12 | Terra alto |
| F07-07 | Garantir que consultas normais omitam ChangeRequest pendente. | F01-17 | Terra alto |
| F07-08 | Implementar exportador XLSX reutilizável e seguro. | F07-01 a F07-06 | Terra alto |
| F07-09 | Implementar exportador PDF reutilizável e seguro. | F07-01 a F07-06 | Terra alto |
| F07-10 | Implementar exportador DOCX reutilizável e seguro. | F07-01 a F07-06 | Terra alto |
| F07-11 | Criar envio de relatório por e-mail, registrando destinatário e AuditEvent. | F07-08 a F07-10, F01-18 | Terra xhigh |
| F07-12 | Testar permissões, tenant, filtros e consistência dos relatórios. | F07-01 a F07-11 | Terra ultra |

**Gate da fase:** cada relatório separa claramente contrato de obra, obra de empresa e dado efetivo de pendência.

## Fase 8 — Robustez, operação e aceite de produção

| ID | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|
| F08-01 | Criar testes de regressão para todos os invariantes de AGENTS.md. | Todas | Terra ultra |
| F08-02 | Criar testes end-to-end dos fluxos onboarding → contrato → medição → faturamento → resultado de obra. | F02 a F07 | Terra ultra |
| F08-03 | Criar testes de segurança para enumeração de UUID, tenant e suporte global. | F01 | Terra ultra |
| F08-04 | Criar testes de webhook duplicado, fora de ordem e reprocessamento. | F02 | Terra ultra |
| F08-05 | Criar métricas, health checks e logs estruturados sem dados sensíveis. | F00 | Terra alto |
| F08-06 | Definir backup/restore de PostgreSQL e retenção de anexos/auditoria. | F00, F04 | Terra alto |
| F08-07 | Criar documentação de operação para webhook, estorno, recuperação de anexo e bloqueio. | F02, F05, F06 | Terra alto |
| F08-08 | Revisar OpenAPI, exemplos de erro e manual de integração do frontend. | Todas | Terra alto |
| F08-09 | Executar revisão final de arquitetura, tenant, segurança e financeiro. | Todas | Sol ultra |
| F08-10 | Validar critérios de aceite do SDD e preparar release candidate. | F08-01 a F08-09 | Sol xhigh |

## Caminho crítico recomendado

1. Fase 0 inteira.
2. Fase 1 inteira.
3. Fase 3 até F03-15.
4. Fase 5 até F05-14.
5. Fase 6 até F06-14.
6. Fase 7 e Fase 8.

Fase 2 pode começar após identidade básica e avançar em paralelo. Fase 4 pode iniciar após F03-09, mas a conversão final depende das regras de contrato/serviço.

## Primeira sequência para começar sem risco

1. F00-01 a F00-11.
2. F01-01 a F01-06.
3. F01-07 a F01-19.
4. F03-01 a F03-07.

Não iniciar CRUDs financeiros antes de concluir TenantContext, autorização, auditoria e a hierarquia FinalCustomer → Work → Contract.
