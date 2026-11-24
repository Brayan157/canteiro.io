# FRONT-F06-14 — Estado oficial do desconto contratual

29/10/2026. **Correção publicada na API local8081 em29/10/2026 às18:44:52SãoPaulo**, JARAF20D2D2/V91 após conferência dofrontend. [Ativação](F03-17-DISCOUNT-STATE-LOCAL-ACTIVATION-2026-10-29.md). Sem migration, novo módulo, nova permissão ou alteração de desconto/faturamento.

## Consulta e campo

`GET /api/v1/company/contracts/{contractId}` mantém CONTRACTS.READ, tenant da sessão e404 para contrato ausente/fora daempresa. ContractResponse recebe `hasActiveContractDiscount: boolean | null`.

No GET de detalhe, sempre boolean: **true** quando `ContractDiscountRepository.findActiveByContractIdAndCompanyId` encontra desconto comercial ativo; **false** quando ausente ou revertido. Percentual ativo com efeito financeiro zero continua true. Ajuste de medição, mesmo com contractDiscountAmount positivo, não implica true. Não expõe valores/tipos financeiros novos, nem concede permissão para reverter.

Em listas e respostas de mutação: **null = não avaliado**, nunca converter parafalse. Reconsultar o detalhe. Campo explicitamente presente e nullable no OpenAPI. Evita consulta adicional por item da lista e não amplia permissões necessárias à criação/edição.

## Frontend

Mostrar “Reverter desconto” somente com GET de detalhe carregado/semerro e `hasActiveContractDiscount === true`, além da autorização/alçada já existente de DISCOUNTS.CANCEL. Falha, null, ausência ou consulta pendente não comprovam desconto ativo. Nunca usar POST como consulta. Não inferir estado por `contractDiscountAmount`: agrega desconto comercial e ajustes de medição e pode serzero com descontoativo.

Após desconto/reversão diretos, invalidar também detalhe do contrato. 202 mantém estado oficial até aplicação aprovada. Mesmo comtrue, servidor revalida vínculo, descontoativo, permissões, concorrência e compromissos financeiros; indicador não garante que qualquer reversão será aceita.

Serviços já expõem discountType e discountValue: considerar tipo oficial FIXED/PERCENTAGE presente com valor cadastrado positivo e permissões, não discountAmount calculado. Efeito arredondadozero não significa ausência.

## Entrega

Contrato combinado com frontend antes da integração; source e testes preparados. 4testes/2suítes eCheckstyle aprovados; exportexecutável24BE6633...148paths/257schemas atualizado empostman/canteiro-io.openapi.json. PacoteAF20D2D2...preservado/comparado aoCF376B05,2classesalteradas/2adicionadas,libs/config/migrationsidênticos. [Candidato e retorno](F03-17-DISCOUNT-STATE-CANDIDATE.md). Não editar tipos gerados manualmente.

OpenAPI vivo200/compartilhados ecomponentes idênticos aoexport24BE6633...,401anônimo noGETdetalhe ehashativo conferidos. Semmigration/seeds/dadosreais alterados. Frontend integrouotipogeradoeconfirmou testes/build/E2E.
