# Backlog executável para Codex — API de Gestão Financeira de Obras

Este backlog deve ser executado em ordem de dependência. As regras de [AGENTS.md](../AGENTS.md) são obrigatórias para cada tarefa.

Cada linha é o limite saudável de uma tarefa Codex: não delegue uma fase inteira a um único agente.

## Convenção de status

- **Concluído:** entrega presente no backend, incluindo os artefatos técnicos aplicáveis.
- **Parcial:** há recorte implementado, mas faltam partes necessárias para encerrar a tarefa original.
- **Pendente:** ainda não foi implementado ou exige a revisão independente indicada.

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

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F00-00 | Concluído | Registrar ADRs de tenancy, UUID, dinheiro/arredondamento, datas UTC, soft delete e aprovação. Os registros aceitos estão em `docs/adr/0001` a `0006`; a ADR 0007 complementa a alçada direta efetiva. | — | Terra alto |
| F00-01 | Concluído | Ler AGENTS.md e SDD; registrar decisões/invariantes impactados pela tarefa. | F00-00 | Terra médio |
| F00-02 | Concluído | Criar o esqueleto de pacotes modular sem mover comportamento ainda. | F00-00 | Terra médio |
| F00-03 | Concluído | Corrigir nome e metadados OpenAPI para o produto atual, sem manter referências ObraFlow. | F00-01 | Terra leve |
| F00-04 | Concluído | Criar configuração por perfil local/test/prod e mover credenciais para variáveis de ambiente. Perfis existem em `application-local.properties`, `application-test.properties` e `application-prod.properties`; `.env` e suas variantes são ignorados pelo Git. Inventário, rotação e chaves segregadas permanecem na F09. | F00-01 | Terra médio |
| F00-05 | Concluído | Criar Dockerfile da API e completar Docker Compose de desenvolvimento. | F00-04 | Terra médio |
| F00-06 | Concluído | Padronizar Problem Details, códigos de erro, paginação, filtros e ID de correlação. | F00-02 | Terra alto |
| F00-07 | Concluído | Corrigir o update/soft delete de Company preservando ID e validar com teste de integração. | F00-02 | Terra alto |
| F00-08 | Concluído | Alinhar constraints/timestamps da entidade Company e migration Flyway por nova migration. | F00-07 | Terra alto |
| F00-09 | Concluído | Configurar base de testes PostgreSQL com Testcontainers e execução de migrations. | F00-04 | Terra alto |
| F00-10 | Concluído | Configurar lint/análise estática, cobertura mínima e pipeline de testes. O Gradle executa Checkstyle e JaCoCo, com mínimo de 15% de instruções cobertas; o workflow `.github/workflows/ci.yml` executa `check` e `jacocoTestReport` em push e pull request. | F00-09 | Terra médio |
| F00-11 | Concluído | Revisar estrutura modular, migrations e configuração de segredos antes da próxima fase. A estrutura por módulos, Flyway e Testcontainers estão presentes; os segredos estão inventariados sem valores e a chave de pagamento agora é obrigatória e independente de `JWT_SECRET`. | F00-02 a F00-10 | Sol xhigh |

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

**Progresso atual:** F02-01 a F02-14 concluídas.

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F02-01 | Concluído | Modelar Plan, módulo/feature, preço e vigência. | F01-10 | Terra alto |
| F02-02 | Concluído | Modelar PlanBundle e regras de preço para combinações cumulativas. | F02-01 | Terra xhigh |
| F02-03 | Concluído | Criar administração de planos e pacotes apenas para proprietário da plataforma. | F02-01 | Terra alto |
| F02-04 | Concluído | Criar onboarding público da Company com seleção obrigatória de plano, proprietário pendente, papel `Company Administrator`, permissões ativas, ativação por token e auditoria imutável. | F01-03, F01-07, F01-14, F02-01 | Terra xhigh |
| F02-05 | Concluído | Criar Subscription, SubscriptionItem e snapshot imutável de preço/composição. | F02-02, F02-04 | Terra xhigh |
| F02-06 | Concluído | Implementar trial de 30 dias e transições locais de assinatura. | F02-05 | Terra alto |
| F02-07 | Concluído | Definir PaymentGateway como porta de aplicação e contratos de webhook. | F02-05 | Terra xhigh |
| F02-08 | Concluído | Implementar adapter Asaas em sandbox, sem vazar SDK para domínio. | F02-07 | Sol xhigh |
| F02-09 | Concluído | Persistir PlatformCharge e PaymentGatewayEvent com idempotência por provedor/chave, proteção concorrente, vínculo seguro ao tenant e testes de persistência. | F02-08 | Sol xhigh |
| F02-10 | Concluído | Autenticar e persistir webhook idempotente, reprocessar eventos recebidos/falhos e reconciliar periodicamente cobranças abertas com o gateway. A atualização usa lock, versão otimista, ordenação temporal e auditoria `SYSTEM`. | F02-09 | Sol xhigh |
| F02-11 | Concluído | Implementar dunning diário com avisos idempotentes persistidos, consulta em D+1/D+5, bloqueio em D+10, auditoria `SYSTEM`, bloqueio de concorrência e filtro global das rotas da Company. | F02-09 | Terra ultra |
| F02-12 | Concluído | Implementar `TrustUnlock` de proprietário da plataforma: máximo histórico de dois por cobrança vencida, expiração imediata no filtro, recálculo do acesso, auditoria com autor e endpoint OpenAPI. | F02-11 | Terra xhigh |
| F02-13 | Concluído | Implementar e-mails de cobrança/aviso com `NotificationPort`, adaptador SMTP, fila persistida com tentativa/retry/cancelamento e auditoria automática. | F02-11 | Terra alto |
| F02-14 | Concluído | Revisar precificação cumulativa, contratos/autenticação/idempotência de webhooks, dunning, notificações e acesso bloqueado. A revisão reforçou combinação exata de pacote, isolamento de rota, retry respeitando intervalo e auditoria `SYSTEM` desde o claim. | F02-01 a F02-13 | Sol ultra |

**Gate da fase:** uma empresa inicia trial, possui snapshot correto de planos, recebe eventos idempotentes e tem acesso limitado/bloqueado conforme cobrança.

## Fase 3 — Clientes finais, obras, contratos e serviços

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F03-01 | Concluído | Criar FinalCustomer e migration final_customer com company_id e unicidades por tenant. | F01-10 | Terra alto |
| F03-02 | Concluído | Criar API de cliente final, contatos e endereços com autorização/auditoria. | F03-01, F01-14 | Terra alto |
| F03-03 | Concluído | Criar Work e migration obra com final_customer_id, company_id, status e datas. | F03-01 | Terra alto |
| F03-04 | Concluído | Modelar local de execução: cliente final, unidade da prestadora ou outro endereço. | F03-03 | Terra médio |
| F03-05 | Concluído | Criar API de obra e validar que o cliente final pertence ao mesmo tenant. | F03-03, F01-10 | Terra alto |
| F03-06 | Concluído | Criar Contract com work_id obrigatório e status; impedir final_customer_id independente. | F03-03 | Terra xhigh |
| F03-07 | Concluído | Criar API de contrato, permitindo contrato sem serviço. | F03-06, F01-14 | Terra alto |
| F03-08 | Concluído | Criar ServiceTemplate e cópia explícita para ContractService. | F03-06 | Terra alto |
| F03-09 | Concluído | Criar ContractService, status e valores monetários. | F03-08 | Terra alto |
| F03-10 | Concluído | Implementar desconto fixo/percentual no serviço. | F03-09 | Terra alto |
| F03-11 | Concluído | Implementar desconto fixo/percentual no contrato como ajuste separado. | F03-09 | Terra xhigh |
| F03-12 | Concluído | Implementar cálculo de valor líquido do contrato e bloqueio de valor negativo. | F03-10, F03-11 | Terra xhigh |
| F03-13 | Concluído | Implementar revisão de contrato que bloqueia redução abaixo de faturamento aprovado. | F03-12 | Sol xhigh |
| F03-14 | Concluído | Testar hierarquia FinalCustomer → Work → Contract → ContractService e soft delete/status. | F03-01 a F03-13 | Terra xhigh |
| F03-15 | Pendente | Revisar modelo, descontos, tenant e constraints antes de faturamento. | F03-01 a F03-14 | Sol xhigh |
| F03-16 | Concluído | Expor CRUD aprovado/direto de serviços, descontos explícitos, modelos de seleção e contrato decimal canônico para o frontend. | F03-08 a F03-13 | Sol xhigh |
| F03-17 | Concluído | Permitir alteração e reversão explícita de desconto de serviço e ajuste de contrato. Reversões exigem justificativa, respeitam alçada/aprovação, atualizam os totais oficiais e preservam auditoria; o ajuste de contrato é desativado logicamente, sem exclusão física. | F03-16 | Terra alto |
| F03-18 | Concluído | Persistir moeda imutável BRL/USD/EUR em Contract e Measurement, com BRL legado na nova V84, catálogo autenticado e 409 para troca/vínculo incompatível. Criação/proposta/aprovação, legado e fluxo financeiro nas três moedas validados; regressão de 192 testes/54 suítes e Checkstyle aprovados. Publicação local autorizada em 14/10/2026, V84 aplicada e OpenAPI vivo exportado; validação da integração em F04-41. | F03-07, F04-18, F05-14 | Sol ultra |

**Gate da fase:** obra é entidade própria; contrato pertence a uma obra; serviço pertence a um contrato; valores e descontos preservam os invariantes.

## Fase 4 — Medições e documentos de medição

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F04-01 | Concluído | Criar Measurement com título obrigatório legível e referência opcional; Work e Contract podem estar ausentes na criação, mas Contract informado exige Work da mesma empresa e cadeia. | F03-06 | Terra xhigh |
| F04-02 | Concluído | Criar `MeasurementVersion`, `MeasurementItem` e estados de negócio. | F04-01 | Terra alto |
| F04-03 | Concluído | Implementar tipo m² e fórmula área × preço por m². | F04-02 | Terra médio |
| F04-04 | Concluído | Implementar tipo metro linear e fórmula metragem × preço por metro. | F04-02 | Terra médio |
| F04-05 | Concluído | Implementar tipo kg/m² e cálculo de kg total/valor total. | F04-02 | Terra alto |
| F04-06 | Concluído | Implementar tipo kg/metro linear e cálculo de kg total/valor total. | F04-02 | Terra alto |
| F04-07 | Concluído | Implementar desconto de cabeçalho da medição como ajuste separado e auditado. | F04-03 a F04-06 | Terra xhigh |
| F04-08 | Concluído | Criar fluxo rascunho → enviada → aceite externo registrado → aceita/finalizada. | F04-02 | Terra xhigh |
| F04-09 | Concluído | Criar revisão para acréscimo após aceite, congelando itens aceitos. | F04-08 | Terra ultra |
| F04-10 | Concluído | Criar `StorageProvider` e upload validado de evidência/planilha. | F00-04 | Terra alto |
| F04-11 | Concluído | Gerar planilha XLSX de medição. | F04-02 | Terra alto |
| F04-12 | Concluído | Gerar PDF de medição. | F04-02 | Terra alto |
| F04-13 | Legado | Converter item aceito em `ContractService` uma única vez, de forma idempotente. Mantido para histórico; novas integrações usam composição por grupo estável em F04-28. | F04-09, F03-09 | Sol xhigh |
| F04-14 | Legado | Calcular faturado/saldo por serviços convertidos individualmente. O recorte antigo existe; a medição contínua exige origem por composição para não atribuir o mesmo recebimento a medições diferentes do mesmo serviço, conforme F05-15 a F05-17 e F04-34. | F04-13 | Terra xhigh |
| F04-15 | Concluído | Testar fórmulas, revisões, aceites, idempotência e tenant. | F04-01 a F04-14 | Terra ultra |
| F04-16 | Pendente | Revisar medição contínua, grade e relatórios acumulados, identidade/destino dos serviços, prévias, aceite/aprovação independentes, correções e origem financeira sem cálculos oficiais locais. | F04-01 a F04-15, F04-17 a F04-38 | Sol ultra |
| F04-17 | Concluído | Alinhar itens ao modelo operacional com título de serviço, data da atividade, prévias calculadas no frontend validadas pelo backend, peso total oficial da versão e totais oficiais em API/PDF/XLSX. | F04-03 a F04-12 | Sol xhigh |
| F04-18 | Concluído | Permitir medição inicialmente sem obra/contrato e vinculá-la depois por operação explícita, auditada e sujeita a alçada/aprovação. A obra/contrato não podem ser trocados silenciosamente; finalização, conversão e efeitos financeiros permanecem bloqueados até haver ambos os vínculos válidos. | F04-01, F01-07, F01-14 | Sol xhigh |
| F04-19 | Concluído | Cancelar logicamente medição sem contrato por operação direta ou solicitação de aprovação, preservando versões, itens, documentos e auditoria. Medição vinculada apenas à obra pode ser cancelada; vínculo com contrato bloqueia a operação com conflito. | F04-18, F01-07, F01-14 | Sol alto |
| F04-20 | Concluído | Corrigir itens de medição em rascunho, removê-los logicamente e criar revisão corretiva para item aceito sem alterar o original. A revisão copia as linhas, preserva `sourceItemId`, recalcula totais oficiais no backend e usa alçada/aprovação, auditoria, tenant e OpenAPI com `201`/`202`. | F04-09, F04-17 | Sol xhigh |
| F04-21 | Concluído | Expor no detalhe da medição o resumo oficial de serviços agrupado por `serviceTitle`, com itens ativos, quantidade, bruto e peso quando aplicável. O desconto de cabeçalho não é rateado sem regra de domínio explícita. | F04-17, F04-20 | Terra alto |
| F04-22 | Concluído | Adicionar cobrança `UNIT` aos itens de medição, com quantidade decimal positiva, cálculo oficial quantidade × preço, peso nulo, persistência, auditoria, correções/revisões, conversão, PDF/XLSX e contrato OpenAPI. | F04-17, F04-20 | Terra alto |
| F04-23 | Concluído | Inserir item em posição após linha ativa de versão `DRAFT` numa única transação, renumerando somente a sequência ativa sem colisão de unicidade, com alçada/aprovação, auditoria, totais oficiais e concorrência da versão. | F04-20 | Sol xhigh |
| F04-24 | Concluído | Criar reordenação completa e atômica de todos os itens ativos de versão `DRAFT`, permitindo alterar o agrupamento por `serviceTitle`. A API valida conjunto exato sem repetição, renumera internamente sem colisão, preserva inativos, usa alçada/aprovação, auditoria, tenant, concorrência e totais oficiais. | F04-23 | Sol xhigh |
| F04-25 | Concluído | Permitir `unitQuantity` opcional como registro operacional de peças/unidades nos tipos m², metro linear, kg/m² e kg/metro linear. Fora de `UNIT`, ela é validada e preservada no histórico, API e PDF/XLSX, sem alterar fórmulas, peso, valor, prévias ou totais oficiais. | F04-22, F04-20, F04-23 | Terra alto |
| F04-26 | Concluído | Importar XLSX de itens somente em versão `DRAFT` por prévia hashada e confirmação explícita. A coluna oculta `Item ID` é a única chave estável; o fluxo cria, atualiza, move, reordena e inativa logicamente em transação única, recalcula totais oficiais e usa alçada/aprovação, auditoria e OpenAPI. Versões aceitas exigem revisão nova. | F04-11, F04-20, F04-24 | Sol xhigh |
| F04-27 | Concluído | Rejeição de aceite externo exige descrição das correções e pode marcar itens ativos; a versão rejeitada permanece imutável. A revisão corretiva pode nascer de versão `REJECTED`, copia os itens ativos com origem preservada e expõe motivo/itens marcados sem alterar totais ou histórico da versão anterior. | F04-08, F04-09, F04-20 | Sol xhigh |
| F04-28 | Concluído | Criar `ContractPricingMode` (`FIXED`/`MEASUREMENT_BASED`), grupos estáveis `MeasurementService` por versão aceita e `MeasurementContractComposition` imutável. A API cria serviço novo ou complementa serviço existente com idempotência por grupo, tenant, alçada, auditoria e rateio oficial de desconto. `BillingAllocation` preserva a composição e limita faturamento ao líquido aceito. A reversão/substituição corretiva antes pendente foi entregue em F04-29; continuidade e financeiro oficial em F04-31 a F04-38/F05-15 a F05-17. Revisão integrada dos valores oficiais em F05-14. | F04-09, F03-09, F05-04 | Sol ultra |
| F04-29 | Concluído | Reconciliar revisão corretiva por raiz reconhecida, incremento/reversão/substituição imutável; resolver cópias/VOID sem duplicação. Redução com alocação ativa é bloqueada até estorno/cancelamento, e origem reduzida sem reconciliação comercial não admite novo faturamento. Testes de substituição, VOID e redução faturada passaram. | F04-28, F05-11 | Sol ultra |
| F04-30 | Concluído | Consulta acumulada implementada em GET /measurements/{measurementId}/accumulated: cabeçalho, revisão atual, serviços/destinos e aceitos/propostas no mesmo corpo, origem/status/editabilidade/referência de leitura, totais separados e financeiro protegido. Revisão vazia preserva os aceitos na grade. | F04-09, F04-29, F04-31 | Sol ultra |
| F04-31 | Concluído | MeasurementContinuousService e vínculo de item persistidos pela V78, independentes de título/grupo aceito; cadastro/consulta e tenant implementados. Legados preservados sem deduzir continuidade financeira entre meses por nome. Cópias corretivas herdam a identidade de origem. | F04-02, F04-28 | Sol xhigh |
| F04-32 | Concluído | Destino novo/existente persistido na identidade contínua, consultável e auditado; PUT exige expectedVersion. Valida contrato/obra/tenant/serviço ativo e alçada; 202 contém somente proposta. Reassociação comum de destino aceito é bloqueada. | F04-18, F04-31 | Sol xhigh |
| F04-33 | Concluído | Aceite, vínculo tardio e destino coordenam somente incrementos aceitos autorizados. Mantidos FIXED/MEASUREMENT_BASED e base negociada; SERVICES possui alçada própria. Estados/pedidos consultáveis, repetição idempotente por origem e teste concorrente sem composição duplicada. | F04-28, F04-29, F04-32 | Sol ultra |
| F04-34 | Concluído | Resumo oficial por medição/identidade contínua com cinco valores, remainingToReceive, ajustes, aceito não composto, disponibilidade e asOf UTC. Resolve raízes/correções, créditos, retenções e estornos; indisponível é null. Redução não reconciliada bloqueia disponibilidade obsoleta. | F04-29, F04-31, F05-15, F05-16 | Sol ultra |
| F04-35 | Concluído | PDF/XLSX acumulados e preview implementados em /accumulated/pdf, /spreadsheet e /spreadsheet/preview, com leitura paginada, SXSSF/cache PDF em disco, snapshot, marca privada, título, serviços e todos os itens identificados. Testes de logo/fallback, múltiplos serviços e PDF multipágina; conferência visual realizada. | F04-11, F04-12, F04-30, F04-34 | Sol xhigh |
| F04-36 | Concluído | Importação, inserção, correção e reorder restringem-se à DRAFT atual; V80 reforça congelamento e origem no banco. XLSX acumulado é explicitamente não importável. Planilha de avanço não inclui/inativa aceitos de versões anteriores; identidades de linhas salvas e cópias são preservadas. | F04-23, F04-24, F04-26, F04-31 | Sol xhigh |
| F04-37 | Concluído | Testes de continuidade, revisão vazia, preços congelados, destino, incrementos, vínculo tardio, aprovação/202, tenant/permissões, duas medições no serviço, correções/VOID, limite concorrente e composição idempotente passaram. Regressão ampliada de medições/recebíveis e OpenAPI em 14/10/2026: 150 testes, zero falhas. | F04-29 a F04-36, F05-17 | Sol ultra |
| F04-38 | Concluído | SDD 0.8, diagrama, backlog, catálogo e FRONTEND-MEASUREMENT-CONTINUOUS.md atualizados com caminhos/DTOs existentes. Planejamento anterior aponta para o guia implementado. OpenAPI executável validado e exportado; análise estática passou. Sem implementar telas no frontend. | F04-30 a F04-37 | Sol xhigh |
| F04-39 | Concluído | Corrigir sobras órfãs da V78 por nova V83 e ledger de retirada operacional imutável, sem exclusão física, união por título ou alteração de origens. Consultas/lista/relatórios usam filtro comum e preservam vazios intencionais, destinos, referências e propostas pendentes aninhadas. PDF/XLSX apresentam subtotais oficiais por serviço; upgrade 77→83, revisão vazia, novos avanços e correções testados, com conferência visual. Regressão em 14/10/2026: 153 testes, zero falhas, Checkstyle aprovado. | F04-29 a F04-38 | Sol ultra |
| F04-40 | Concluído | Unificar layout acumulado/por versão com dez colunas visíveis, marca privada validada/proporcional, título/faixas centralizados, descrições com continuação, rótulos humanos e cifra BRL/USD/EUR em todo dinheiro. XLSX mantém IDs de item/serviço, decimais exatos, fórmulas de prévia e metadados de origem/moeda; vínculos carregados em lotes, sem juntar identidades pelo título. Reimporta somente DRAFT atual. Regressão de 192 testes/54 suítes e inspeção visual de PDFs completos/planilhas aprovadas; [guia](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md). | F04-35, F04-36, F03-18 | Sol ultra |
| F04-41 | Concluído | Publicação local V84, OpenAPI vivo sincronizado por hash e tipos regenerados. Em 15/10/2026, frontend confirmou em sessão real catálogo BRL/USD/EUR, seletor USD e downloads servicos-fabrica-de-acucar.pdf (3.411 bytes)/.xlsx (5.949 bytes) com Content-Disposition, sem fallback fabricado; dois serviços/cinco aceitos, dez colunas, títulos/cifras preservados após recriação. Testes focados, lint/build e E2E informados como aprovados pelo frontend; [evidências/limites](F04-41-LOCAL-PUBLICATION.md). CORS F04-42 e storage F08-23 concluídos com autorizações separadas. Logo real aguarda usuário/interface do frontend; avisos de segurança seguem em fases próprias, sem alterar dados comerciais. | F03-18, F04-40, F05-18 | Sol alto |
| F04-42 | Concluído | Content-Disposition e X-Correlation-Id expostos somente às origens autorizadas. Dez testes/Checkstyle aprovados; publicação local autorizada em 15/10/2026, com comparação de 1.371 entradas dos JARs (somente classe CORS mudou), GET/preflight reais e origem rejeitada validados, V84 e OpenAPI preservados. [Registro](F04-42-DOWNLOAD-CORS-AND-BRANDING.md). Frontend confirmou PDF/XLSX com filename oficial no navegador. Emitente do exemplo sem referência de logo; storage tratado separadamente na F08-23 antes do upload pelo usuário. | F04-40, F04-41 | Terra alto |
| F04-43 | Concluído | PDF acumulado/por versão e prévias mostram sempre nome/CNPJ da Company emitente junto da logo privada, sem usar cliente final ou número inventado. Cabeçalho separado/repetido sem colisão; XLSX/rotas/DTOs/financeiro preservados. Vinte testes unitários/de geração, doze de integração, Checkstyle e nove páginas finais de QA aprovados. [Guia/provas](FRONTEND-MEASUREMENT-PDF-EMITTER.md). Publicação local autorizada em 15/10/2026: exatamente três classes alteradas entre 1.371 entradas do JAR; API pronta, OpenAPI/V84, dados e arquivo privado preservados, sem migration ou alteração comercial. Frontend confere a identificação real no download autenticado. | F04-40, F04-42, F08-23 | Terra alto |

**Gate da fase:** a mesma medição permanece visível com serviços/itens aceitos e avanços atuais no corpo principal e nos relatórios acumulados; somente incrementos aceitos/autorizados compõem o contrato, sem duplicidade, e os três valores financeiros vêm da API. Histórico por versão e resumo isolado não encerram a medição contínua. Especificação: [FRONTEND-MEASUREMENT-CONTINUOUS-PLANNED.md](FRONTEND-MEASUREMENT-CONTINUOUS-PLANNED.md).

## Fase 5 — Faturamento, NF, contas a receber e recebimentos

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F05-01 | Concluído | Criar BillingInstallment e BillingAllocation por Contract. | F03-12 | Terra xhigh |
| F05-02 | Concluído | Separar alocação de serviço positiva e ajuste de desconto contratual negativo. | F05-01 | Terra xhigh |
| F05-03 | Concluído | Implementar cálculo de saldo faturável canônico pelo conjunto de BillingAllocation. | F05-01 | Sol ultra |
| F05-04 | Concluído | Bloquear excesso de faturamento sob concorrência com versão/transação. | F05-03 | Sol ultra |
| F05-05 | Concluído | Criar parcela aprovada e título Receivable individual/grupo financeiro. | F05-01 | Terra xhigh |
| F05-06 | Concluído | Criar Invoice e InvoiceItem, exigindo um único Contract e alocações compatíveis. | F05-02 | Terra xhigh |
| F05-07 | Concluído | Permitir NF de serviço parcialmente faturado em várias NFs sem dupla contagem. | F05-06 | Sol xhigh |
| F05-08 | Concluído | Criar TaxObligation com tratamento a pagar, retido ou informativo. | F05-06 | Terra xhigh |
| F05-09 | Concluído | Criar anexos PDF/XML de NF com auditoria. | F05-06, F04-10 | Terra alto |
| F05-10 | Concluído | Criar ReceivableSettlement para recebimento parcial, total e atraso. | F05-05 | Terra xhigh |
| F05-11 | Concluído | Implementar cancelamento/estorno de parcela, NF e recebimento com efeitos atômicos. | F05-03, F05-10 | Sol xhigh |
| F05-12 | Concluído | Criar filtros/API de controle de faturamento exclusivamente por Contract. | F05-03 | Terra alto |
| F05-13 | Concluído | Testar saldo, concorrência, item de NF, recebimento parcial e estornos. | F05-01 a F05-12 | Terra ultra |
| F05-14 | Concluído | Revisar cadeia aceita → posição comercial → faturamento → NF → título → baixa/estorno, com provas pelos GETs oficiais nos dois modos de contrato e destinos novo/existente. Corrigir decimal JSON, serviço cancelado, redução abaixo de compromissos próprios/base, lastro do desconto negativo e leituras anteriores ao lock em serviço/faturamento/NF. [Registro da revisão](F05-14-FINANCIAL-INVARIANTS-REVIEW.md): 184 testes em 51 suítes, zero falhas/erros/ignorados e Checkstyle aprovado em 14/10/2026. Publicação local autorizada e concluída em localhost:8081; OpenAPI 3.1 exportado da instância, 15 campos monetários e status validados. Banco permaneceu na V83, sem migration nova ou alteração comercial manual. | F05-01 a F05-13, F04-29 a F04-38, F05-15 a F05-17 | Sol ultra |
| F05-15 | Concluído | Origem de composição exigida acima da base negociada, com validação de cadeia e limites por origem/contrato sob lock com versão. V79/V82 reforçam tenant e cadeia; NF preserva alocação e título/baixas usam atribuição imutável. Legados não são associados automaticamente; fluxos antigo/contínuo não duplicam raiz. | F04-28, F05-03, F05-04 | Sol ultra |
| F05-16 | Concluído | Saldo por origem inclui aceito ainda não faturado, desconto contratual atribuído, retenções, créditos/reversões e renegociação apenas do saldo não pago. V81 registra parcelas imutáveis de baixa/estorno; V82 mantém linhagem de títulos, sem novo faturamento/caixa. Rateio exato e determinístico limitado à capacidade. | F05-08, F05-10, F05-11, F05-15 | Sol ultra |
| F05-17 | Concluído | Testes de duas origens, baixa parcial, NF/retenção, centavos, crédito/reversão, renegociação/baixa descendente/estorno, cancelamento, aprovação sem efeito e limite concorrente passaram. Histórico com atribuição inconsistente permanece legível com financeiro indisponível e nova baixa incompatível recebe 409. Regressão ampliada em 14/10/2026: 150 testes, zero falhas; OpenAPI e Checkstyle validados. | F05-15, F05-16 | Sol ultra |
| F05-18 | Concluído | Projetar currency na cadeia de receita, herdada do contrato imutável: parcela, NF, imposto, título, saldo, baixa, crédito, renegociação e resumo da medição. BRL/USD/EUR validados sem conversão ou alteração dos cálculos; listagem preserva moeda por contrato, sem soma nominal entre unidades. Regressão de 192 testes/54 suítes, Checkstyle e OpenAPI 3.1 aprovados. Consolidados futuros continuam em F07-13; publicação em F04-41. | F03-18, F05-15 a F05-17 | Sol ultra |

**Gate da fase:** nenhuma combinação de parcelas/NFs/recebimentos ultrapassa contrato ou conta duas vezes o mesmo valor.

## Fase 6 — Gastos, contas a pagar, impostos e resultado por obra

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F06-01 | Pendente | Criar Supplier e categorias financeiras. | F01-10 | Terra médio |
| F06-02 | Parcial | Criar Payable com financial_scope WORK/COMPANY e grupo financeiro. Hoje há somente o recorte interno de conta a pagar tributária de escopo Work. | F06-01, F03-03 | Terra xhigh |
| F06-03 | Parcial | Validar WORK: work_id obrigatório e contract_id opcional da mesma obra. Coberto no recorte tributário; falta o lançamento operacional geral. | F06-02 | Terra xhigh |
| F06-04 | Pendente | Validar COMPANY: work_id e contract_id obrigatoriamente nulos. | F06-02 | Terra alto |
| F06-05 | Pendente | Criar API de gasto de obra, incluindo EPI sem Contract. | F06-03 | Terra alto |
| F06-06 | Pendente | Criar API de gasto geral da empresa, excluído do lucro de obra. | F06-04 | Terra alto |
| F06-07 | Pendente | Criar PayableSettlement para pagamento parcial/total e atraso. | F06-02 | Terra xhigh |
| F06-08 | Concluído | Criar contas a pagar automáticas de imposto oriundo de NF quando aplicável. | F05-08, F06-02 | Terra xhigh |
| F06-09 | Pendente | Implementar estorno/cancelamento de pagamento e reabertura do título. | F06-07 | Terra xhigh |
| F06-10 | Pendente | Criar projeção de receita faturada, emitida e recebida da Work a partir de Contracts. | F05-10 | Terra xhigh |
| F06-11 | Pendente | Calcular resultado previsto da Work sem incluir gastos COMPANY. | F06-05, F06-06, F06-10 | Sol xhigh |
| F06-12 | Pendente | Calcular resultado de caixa da Work e fluxo de caixa geral da Company. | F06-07, F06-10 | Sol xhigh |
| F06-13 | Pendente | Testar EPI por obra, gasto empresa, impostos, lucro e fluxo de caixa. | F06-01 a F06-12 | Terra ultra |
| F06-14 | Pendente | Revisar segregação Work/Company e fórmulas financeiras. | F06-01 a F06-13 | Sol ultra |

**Gate da fase:** EPI pode pertencer apenas à obra; gasto geral não entra em lucro de obra; receitas vêm dos contratos; saldos de contrato permanecem independentes.

## Fase 7 — Relatórios, exportações e envio

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F07-01 | Pendente | Criar consultas paginadas e filtráveis de clientes finais ativos. | F03-02 | Terra médio |
| F07-02 | Pendente | Criar relatório de valores a receber por cliente/contrato. | F05-10 | Terra alto |
| F07-03 | Pendente | Criar relatório de contratos abertos, finalizados e a receber. | F05-12 | Terra alto |
| F07-04 | Pendente | Criar relatório de controle de faturamento por contrato/serviço. | F05-12 | Terra xhigh |
| F07-05 | Pendente | Criar relatório de gastos e resultado por Work. | F06-11 | Terra xhigh |
| F07-06 | Pendente | Criar relatório de gastos gerais da Company, separado de Works. | F06-12 | Terra alto |
| F07-07 | Concluído | Garantir que consultas normais omitam ChangeRequest pendente. | F01-17 | Terra alto |
| F07-08 | Pendente | Implementar exportador XLSX reutilizável e seguro. A exportação de medição existe, mas não substitui o exportador de relatórios desta fase. | F07-01 a F07-06 | Terra alto |
| F07-09 | Pendente | Implementar exportador PDF reutilizável e seguro. A exportação de medição existe, mas não substitui o exportador de relatórios desta fase. | F07-01 a F07-06 | Terra alto |
| F07-10 | Pendente | Implementar exportador DOCX reutilizável e seguro. | F07-01 a F07-06 | Terra alto |
| F07-11 | Pendente | Criar envio de relatório por e-mail, registrando destinatário e AuditEvent. | F07-08 a F07-10, F01-18 | Terra xhigh |
| F07-12 | Pendente | Testar permissões, tenant, filtros e consistência dos relatórios. | F07-01 a F07-11 | Terra ultra |
| F07-13 | Pendente | Nas futuras consolidações de Work/Company, separar receitas, gastos, caixa e resultado por código ISO; explicitar também moeda de gastos sem contrato antes de consolidar. Nunca somar BRL/USD/EUR nominalmente ou introduzir câmbio implícito. Os GETs atuais por contrato não substituem esses relatórios futuros. | F03-18, F05-18, F06-11, F06-12 | Sol ultra |

**Gate da fase:** cada relatório separa claramente contrato de obra, obra de empresa e dado efetivo de pendência.

## Fase 8 — Robustez, operação e aceite de produção

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F08-01 | Pendente | Criar testes de regressão para todos os invariantes de AGENTS.md. | Todas | Terra ultra |
| F08-02 | Pendente | Criar testes end-to-end dos fluxos onboarding → contrato → medição → faturamento → resultado de obra. | F02 a F07 | Terra ultra |
| F08-03 | Parcial | Criar testes de segurança para enumeração de UUID, tenant e suporte global. Há testes de isolamento por módulos; falta a suíte consolidada de segurança. | F01 | Terra ultra |
| F08-04 | Parcial | Criar testes de webhook duplicado, fora de ordem e reprocessamento. Há cobertura do ciclo de gateway; falta a suíte de regressão operacional completa. | F02 | Terra ultra |
| F08-05 | Parcial | Criar métricas, health checks e logs estruturados sem dados sensíveis. Correlação e erros padronizados existem; métricas e operação completa permanecem pendentes. | F00 | Terra alto |
| F08-06 | Pendente | Definir backup/restore de PostgreSQL e retenção de anexos/auditoria. | F00, F04 | Terra alto |
| F08-07 | Pendente | Criar documentação de operação para webhook, estorno, recuperação de anexo e bloqueio. | F02, F05, F06 | Terra alto |
| F08-08 | Parcial | Revisar OpenAPI, exemplos de erro e manual de integração do frontend. O contrato executável e guias pontuais existem; falta a revisão integral. | Todas | Terra alto |
| F08-09 | Pendente | Executar revisão final de arquitetura, tenant, segurança e financeiro. | Todas | Sol ultra |
| F08-10 | Pendente | Validar critérios de aceite do SDD e preparar release candidate. | F08-01 a F08-09 | Sol xhigh |
| F08-11 | Pendente | Implementar fluxo de privacidade para eliminação/anonimização definitiva de dados pessoais de funcionário. O `DELETE` operacional permanece como desativação; o fluxo separado deve avaliar retenção, exigir autorização, apagar dados de endereço e o ciphertext de conta/Pix quando permitido, preservar apenas auditoria mínima sem valores pessoais e ter prazo configurável/testes. | F01-14, F08-06 | Terra xhigh |
| F08-12 | Concluído | Referências de pagamento de funcionário incluem dígito de conta cifrado. A rota pontual de revelação de conta/Pix para pagamento manual exige `EMPLOYEES.VIEW_PAYMENT_DETAILS`, usa tenant da sessão e gera `AuditEvent` imutável sem valores. Para chave Pix com nome/cidade confirmados do titular, gera BR Code e QR estático efêmeros, sem valor/cobrança/vencimento; não infere titular pelos dados do funcionário e não persiste/audita o payload/QR. | F01-07, F01-14, F08-03 | Terra xhigh |
| F08-13 | Concluído | Proteger o proprietário inicial da empresa e seu papel de onboarding. Listagens expõem `companyOwner`/`systemProtected`, enquanto o backend bloqueia desativação, troca de papéis, alteração/desativação do papel, troca de permissões e atribuição desse papel a outro usuário. | F01-02, F01-07, F08-03 | Terra alto |
| F08-14 | Concluído | Central de aprovações paginada com filtros por busca segura, módulo, operação, solicitante e período. A resposta traz projeções legíveis e sanitizadas de solicitante/entidade e resumo de proposta de funcionário, sem dados pessoais ou financeiros. | F01-07, F01-14, F08-03 | Terra alto |
| F08-15 | Concluído | Projeção de ator da auditoria separa nome legível seguro (`actorDisplayName`) e e-mail (`actorEmail`). O nome usa o perfil de funcionário quando vinculado, preserva “Sistema” para ações automáticas e aplica fallback histórico sem expor e-mail ou UUID como identidade de tela. | F01-13, F01-14 | Terra alto |
| F08-16 | Concluído | Foto opcional de funcionário para identificação visual interna, com upload/consulta/remoção autenticados por tenant, PNG/JPEG validado, storage privado, sem reconhecimento facial ou uso biométrico. Auditoria retém somente `hasPhoto`. | F01-07, F01-14, F04-10 | Terra alto |
| F08-17 | Concluído | A central de aprovações projeta `requester.employeeId` e `requester.photoUrl` somente para funcionário vinculado no tenant e com foto. A resposta nunca expõe chave de storage nem URL pública; o download continua sujeito a `EMPLOYEES.READ`. | F08-14, F08-16 | Terra alto |
| F08-18 | Concluído | Edição de cliente final e listagens operacionais filtráveis de clientes/funcionários. Cliente final aceita filtro por nome, CPF/CNPJ normalizado, tipo e situação; funcionário por nome, documento, cargo, departamento, vínculo, modalidade/remuneração e situações. | F01-10, F01-14, F03-02 | Terra alto |
| F08-19 | Concluído | Edição e listagens operacionais filtráveis de obras/contratos. Obra pode ser filtrada por nome, referência, cliente final, situação, local de execução e períodos; contrato, por nome, referência, obra, situação e períodos. | F01-10, F01-14, F03-05, F03-07 | Terra alto |
| F08-20 | Concluído | Exportações existentes de medição em XLSX/PDF usam leitura paginada, geração com armazenamento temporário e resposta HTTP em streaming. O XLSX usa SXSSF e o PDF usa cache temporário do PDFBox. | F04-11, F04-12, F08-05 | Terra alto |
| F08-21 | Concluído | CRUD de contatos e endereços do cliente final, com leitura por tenant, criação/edição/definição de principal/desativação lógica. Cada alteração usa alçada direta ou `ChangeRequest` sem alterar o dado oficial até aprovação. | F01-07, F01-14, F03-02 | Terra alto |
| F08-22 | Concluído | Filtros relacionais sem UUID no frontend: obras podem ser localizadas por nome ou CPF/CNPJ do cliente final; contratos por nome da obra e do cliente final. | F08-19 | Terra alto |
| F08-23 | Concluído | Storage privado local autorizado/publicado em 15/10/2026: volume novo canteiroio-api-private-storage em /app/var/storage, usuário não root 100, diretório 0700. Inventário/backup e configuração preservados; JAR idêntico ao CORS validado. Artefato técnico 0600 manteve hash após recriação somente da API e foi removido por caminho exato; outro UID sem acesso. Banco V84, referências e PostgreSQL/Mailpit intactos; sem logo/Company alterada. [Registro](F08-23-LOCAL-PRIVATE-STORAGE.md). Backup externo/object storage/criptografia/retenção permanecem nas fases próprias. | F04-10 | Terra alto |

## Fase 9 — Endurecimento de segurança e privacidade

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F09-01 | Parcial | Inventariar segredos por ambiente, exigir chave independente para criptografia de dados de funcionário, remover fallbacks entre chave JWT e chave de dados, documentar rotação e validar falha segura sem segredo obrigatório. O inventário não contém valores; Compose e a aplicação rejeitam chave ausente, curta ou igual à JWT. A implementação e o teste unitário foram adicionados, mas a execução do Gradle está bloqueada antes de produzir resultado neste ambiente. | F00-04, F08-05 | Sol xhigh |
| F09-02 | Pendente | Cifrar em repouso documento, PIS, endereço e remuneração de funcionário; criar índices cegos/HMAC separados para as pesquisas exatas autorizadas, sem persistir dados pessoais em texto claro somente para filtro. | F09-01, F01-10 | Sol ultra |
| F09-03 | Pendente | Criar política de anexos privados de funcionário: validação de PDF/PNG/JPEG, tamanho, hash, verificação antimalware, storage cifrado, download autenticado `no-store` e auditoria sem conteúdo. | F09-01, F04-10, F08-16 | Terra xhigh |
| F09-04 | Pendente | Implementar rate limit distribuído para login, ativação, reenvio e recuperação de senha, com limites por origem e identidade normalizada, respostas neutras e métricas sem dados pessoais. | F01-03, F08-05 | Sol xhigh |
| F09-05 | Pendente | Persistir falhas de autenticação e bloqueio temporário progressivo por conta/origem, com expiração, auditoria segura, resposta anti-enumeração e testes de concorrência. | F09-04, F01-14 | Terra xhigh |
| F09-06 | Pendente | Implementar MFA TOTP para proprietário da plataforma, códigos de recuperação de uso único e fator opcional/exigível para administradores de empresa. Segredos TOTP ficam cifrados; configuração, desafio, recuperação e desativação são auditáveis. | F09-01, F01-03, F01-14 | Sol ultra |
| F09-07 | Pendente | Aplicar RLS nas tabelas operacionais de tenant, estabelecer tenant local por transação, restringir o papel de banco da aplicação e cobrir acesso cruzado, background jobs e suporte com testes PostgreSQL reais. | F01-05, F09-02 | Sol ultra |
| F09-08 | Pendente | Revisar logs, OpenAPI, CORS, cookies, documentação de operação e testes de segurança de ponta a ponta, incluindo rotação/revogação de tokens e respostas Problem Details. Na publicação F04-41, requisição anônima a /company/currencies retornou 401, mas SubscriptionAccessFilter registrou exceção por exigir tenant antes da autorização; corrigir a sequência sem contornar tenant/dunning e testar em servidor HTTP real. Aviso de interpolação de segredo no Compose deve ser saneado sem expor o valor, com avaliação de rotação em F09-01. | F09-01 a F09-07 | Sol ultra |

**Gate da fase:** tentativas abusivas são limitadas e bloqueadas sem enumeração de conta; MFA protege o proprietário da plataforma; segredos e dados pessoais usam chaves separadas; RLS e a aplicação impedem acesso entre empresas.

## Fase 10 — Vínculos de trabalho, conformidade e alertas

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F10-01 | Pendente | Criar `EmployeeEmployment` com migration, estados `PROBATION`, `ACTIVE`, `NOTICE_PERIOD` e `TERMINATED`, versão otimista e constraint de no máximo um vínculo aberto por funcionário/empresa. Migrar os campos atuais de situação de trabalho sem perder histórico. | F01-10, F09-02 | Sol xhigh |
| F10-02 | Pendente | Criar admissão e readmissão explícitas. Readmissão deve criar novo vínculo após desligamento, preservar o anterior e impedir dois vínculos abertos. | F10-01, F01-14 | Terra xhigh |
| F10-03 | Pendente | Implementar período de experiência, efetivação explícita e consultas/listagens seguras por situação e prazo. O vencimento da experiência gera alerta, nunca efetivação ou desligamento automático. | F10-01, F01-14 | Terra alto |
| F10-04 | Pendente | Criar aviso prévio com início, término previsto, motivo, confirmação de desligamento e revogação. Revogar preserva o registro; concluir ou confirmar desligamento encerra somente o vínculo atual. | F10-01, F10-03, F01-14 | Sol xhigh |
| F10-05 | Pendente | Criar anexos privados de funcionário para documento pessoal, comprovante de endereço e outros documentos administrativos, reutilizando a política segura de storage e o fluxo de privacidade. | F09-03, F10-01 | Terra xhigh |
| F10-06 | Pendente | Criar registro de curso/certificado com tipo, título seguro, emissor, emissão, validade, situação e anexos privados; manter histórico e exclusão lógica. | F10-05, F01-14 | Terra alto |
| F10-07 | Pendente | Criar registro de exame periódico com tipo, aptidão administrativa, emissão, validade e comprovante privado. Proibir diagnóstico, prontuário, resultado clínico detalhado e inferência médica. | F10-05, F01-14 | Sol xhigh |
| F10-08 | Pendente | Criar agenda/notificação idempotente de vencimentos para contrato comercial, experiência, aviso prévio, curso, certificado e exame; permitir antecedências e destinatários autorizados, sem alterar dados de negócio automaticamente. | F02-13, F10-03, F10-04, F10-06, F10-07 | Sol xhigh |
| F10-09 | Pendente | Expor API paginada e OpenAPI para histórico de vínculos, avisos, documentos, cursos, exames e alertas, com alçada direta/solicitação de aprovação, isolamento de tenant, `403`/`404` seguros e DTOs sem URLs públicas/chaves de storage. | F10-02 a F10-08 | Terra xhigh |
| F10-10 | Pendente | Testar migração, readmissão, aviso revogado, desligamento, isolamento entre empresas, autorização, anexos privados, ausência de dados clínicos, alertas idempotentes e contratos OpenAPI. | F10-01 a F10-09 | Sol ultra |

**Gate da fase:** o histórico de trabalho não é perdido; um funcionário pode ser readmitido sem reabrir vínculo antigo; documentos e conformidade são privados; alertas de prazo são confiáveis e nunca alteram automaticamente o estado laboral ou comercial.

## Fase 11 — Cortesias temporárias de assinatura

| ID | Status | Tarefa pequena e verificável | Depende de | Perfil |
|---|---|---|---|---|
| F11-01 | Pendente | Criar `SubscriptionCourtesyGrant`, migration, tipos `TEMPORARY` e `LIFETIME` e estados `SCHEDULED`, `ACTIVE`, `REVOKED` e `EXPIRED`. Cortesia temporária exige início/fim inclusivos; vitalícia não possui fim e deve ser declarada explicitamente, nunca inferida de data nula. Registrar `company_id`, `subscription_id`, motivo obrigatório, concedente, versão otimista e constraint contra sobreposição. | F02-05, F02-11 | Terra xhigh |
| F11-02 | Pendente | Criar API de plataforma para listar, conceder, agendar e revogar cortesia temporária ou vitalícia. Somente `PLATFORM_OWNER` pode mutar; a empresa nunca informa ou escolhe seu próprio `companyId`. | F11-01, F01-10 | Terra alto |
| F11-03 | Pendente | Integrar a cortesia ativa ao cálculo de `CompanySubscriptionAccess`: durante o período, liberar acesso integral antes do dunning, sem alterar trial, plano, preço ou histórico de assinatura. | F11-01, F02-11, F02-12 | Sol xhigh |
| F11-04 | Pendente | Pausar criação de novas cobranças, avisos e restrições para a assinatura durante a cortesia; tratar cobranças abertas prévias por operação explícita e auditável, sem exclusão física, baixa fictícia ou bloqueio surpresa no término. | F11-03, F02-09, F02-13 | Sol ultra |
| F11-05 | Pendente | Definir retomada após expiração ou revogação de cortesia, inclusive vitalícia: próxima cobrança e vencimento são calculados/registrados de modo idempotente, e o dunning volta a avaliar somente obrigações válidas. | F11-04, F02-10 | Sol xhigh |
| F11-06 | Pendente | Registrar `AuditEvent` para concessão, agendamento, ativação, expiração, revogação e tratamento de cobrança, sem expor dados de pagamento. | F11-01, F01-14 | Terra alto |
| F11-07 | Pendente | Publicar OpenAPI e guia para frontend: listagem de cortesias, datas, situação, motivo permitido e histórico; usar Problem Details para `400`, `403`, `404` e `409`. | F11-02 a F11-06 | Terra alto |
| F11-08 | Pendente | Testar autorização de proprietário, isolamento entre empresas, sobreposição, concorrência, acesso durante cortesia temporária e vitalícia, suspensão/retomada de cobrança, revogação e imutabilidade do histórico. | F11-01 a F11-07 | Terra ultra |
| F11-09 | Pendente | Revisar trial, cortesia, cobrança, dunning e desbloqueio de confiança para garantir que uma regra não silencie ou prolongue outra sem evento auditável. | F11-01 a F11-08 | Sol ultra |

**Gate da fase:** uma empresa pode receber período gratuito explícito, temporário ou vitalício, sem alterar o trial ou o contrato comercial, sem cobrança indevida, sem perda de histórico e sem conceder à própria empresa poder para criar, prolongar ou ocultar a cortesia.

## Caminho crítico recomendado

1. Fase 0 inteira.
2. Fase 1 inteira.
3. Fase 3 até F03-15.
4. Fase 5 até F05-14.
5. Fase 6 até F06-14.
6. Fase 7 e Fase 8.
7. Fase 9 antes de expor os novos dados pessoais da Fase 10.
8. Fase 10.
9. Fase 11.

Fase 2 pode começar após identidade básica e avançar em paralelo. Fase 4 pode iniciar após F03-09, mas a conversão final depende das regras de contrato/serviço.

## Primeira sequência para começar sem risco

1. F00-01 a F00-11.
2. F01-01 a F01-06.
3. F01-07 a F01-19.
4. F03-01 a F03-07.

Não iniciar CRUDs financeiros antes de concluir TenantContext, autorização, auditoria e a hierarquia FinalCustomer → Work → Contract.
