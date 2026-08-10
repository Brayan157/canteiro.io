# ADR 0001 — Tenancy e isolamento por empresa

- Estado: Aceito
- Data: 2026-08-10

## Contexto

O Cantail.io é uma plataforma SaaS: cada empresa assinante é um tenant e seus
dados operacionais são confidenciais perante as demais empresas.

## Decisão

1. A `Company` é o limite de tenant.
2. Todo dado operacional possui `company_id` diretamente ou tem sua cadeia até
   a empresa validada na aplicação e no banco.
3. O `company_id` de rotas de empresa é obtido exclusivamente do usuário
   autenticado e do `TenantContext`; nunca vem livremente no payload.
4. Consultas, exportações, anexos, unicidades e mutações são sempre filtrados
   pelo tenant. IDs UUID não dispensam essa validação.
5. Um usuário de empresa pertence a exatamente uma `Company`.
6. Rotas de suporte/plataforma usam contexto separado para a empresa-alvo,
   exigem papel global e registram auditoria. Elas não se misturam às rotas de
   empresa.
7. Cada módulo persistente deve incluir ao menos um teste negativo de acesso
   entre duas empresas.

## Consequências

O isolamento é aplicado no backend, inclusive quando o frontend ocultar dados.
Qualquer repositório, serviço, exportador ou storage que ignore o tenant é um
defeito de segurança.
