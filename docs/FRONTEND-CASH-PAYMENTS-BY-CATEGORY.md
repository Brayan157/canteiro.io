# Pagamentos por categoria

| Consulta | Rota | Permissões efetivas |
|---|---|---|
| Empresa | `GET /api/v1/company/cash-flow/payments/comparison/by-category` | `PAYABLES.READ` e `COSTS_EXPENSES.READ` |
| Obra | `GET /api/v1/company/cash-flow/works/{workId}/payments/comparison/by-category` | As duas anteriores e `WORKS.READ` |

As rotas exigem `from` e `to` no formato `YYYY-MM-DD`, com limites inclusivos até hoje UTC. O período anterior termina na véspera de `from` e tem a mesma duração. Erros usam Problem Details. Intervalo inválido retorna `400`, sessão ausente `401`, falta de permissão `403` e obra ausente no tenant `404`. O tenant vem da sessão.

`CashPaymentsByCategoryResponse` retorna `workId` (nulo para empresa), `currentPeriod`, `previousPeriod`, `calculatedAt` e `categories`. Cada linha em `categories` representa uma categoria e uma moeda:

- `categoryId`, `categoryCode`, `categoryName`, `categoryActive`, `currency`.
- `payments`, `workPayments` e, na consulta de empresa, `companyPayments`. Cada comparação usa o mesmo contrato de [comparação geral](FRONTEND-CASH-PAYMENTS-COMPARISON.md), com valores e percentuais em strings decimais.

A linha usa o ID do catálogo do tenant. Categorias inativas continuam no histórico com seu ID e nome atual. A linha `Sem categoria` tem `categoryId`, `categoryCode` e `categoryActive` nulos. As linhas aparecem por moeda e código, com `Sem categoria` ao fim de cada moeda. Só entram grupos com movimentos em pelo menos um dos dois períodos. Sem movimentos em ambos, `categories` é vazio.

A fonte é o valor assinado de cada baixa ou estorno oficial, na data efetiva do próprio movimento. A consulta por obra inclui títulos `WORK` da obra, inclusive sem contrato. Na empresa, os dois escopos são apresentados separadamente e também no total por categoria e moeda. Um título em aberto sem baixa ou uma proposta `202` sem efeito oficial fica fora do agregado. A cobertura descreve os movimentos registrados. O frontend usa os valores e percentuais retornados.

O [OpenAPI exportado](../postman/canteiro-io.openapi.json) contém `compareCompanyCashPaymentsByCategory` e `compareWorkCashPaymentsByCategory`. O contrato usa o schema existente. Em 13/11/2026, passaram 27 testes com PostgreSQL descartável e o Checkstyle. A publicação local das rotas por categoria está pendente.

## Proposta para o catálogo inicial

| Código estável | Nome inicial | Classificação proposta |
|---|---|---|
| `EPI` | EPI | `WORK_COST` |
| `MATERIAIS_OBRA` | Materiais de obra | `WORK_COST` |
| `LOCACAO_EQUIPAMENTOS` | Locação de equipamentos | `WORK_COST` |
| `SERVICOS_TERCEIRIZADOS` | Serviços terceirizados | `WORK_COST` |
| `DESPESAS_ADMINISTRATIVAS` | Despesas administrativas | `COMPANY_EXPENSE` |
| `IMPOSTOS` | Impostos | `TAX` |

Para empresas novas, criar as categorias na mesma transação do onboarding, após criar a empresa e antes do envio do e-mail de ativação. O provisionamento interno recebe a empresa recém-criada, registra evento `SYSTEM` com origem de onboarding e usa as regras do catálogo. O nome e a descrição permanecem editáveis. Código e classificação continuam imutáveis. A categoria continua independente do escopo financeiro de cada título.

Para empresas existentes, oferecer adesão explícita com prévia dos códigos que seriam criados ou preservados. Usar a alçada de criação de `COSTS_EXPENSES`, com `202` para solicitação e revalidação na aprovação. Criar apenas códigos ausentes, inclusive ao repetir ou concorrer com um cadastro manual. A constraint única por empresa e código protege o catálogo. Preservar registros ativos e inativos e sinalizar conflitos de classificação. Categorias de mesmo nome e códigos distintos continuam independentes. Auditar somente as criações efetivas, com ator, empresa e origem.

O provisionamento e a adesão são propostas para uma entrega posterior. Dependem de implementar o serviço transacional, a integração com onboarding e o fluxo de adesão e aprovação, com testes de repetição, concorrência, colisão com código existente e isolamento. Os registros existentes continuam com suas categorias atuais.
