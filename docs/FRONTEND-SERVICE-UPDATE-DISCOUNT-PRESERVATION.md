# Edição operacional de serviço preserva o desconto vigente

30/10/2026. Correção publicada somente em localhost:8081 às00:47:58SãoPaulo, após testes e conferência do frontend. [Registro da ativação](F03-16-SERVICE-DISCOUNT-PRESERVATION-LOCAL-ACTIVATION-2026-10-30.md).

## Diagnóstico

`ContractService.updateProfile` preserva corretamente o desconto e as composições. O serviço de aplicação, porém, chamava em seguida `withDiscount(null,null)` quando o PUT omitisse os campos de desconto, apagando o desconto do serviço tanto em aplicação direta como na aprovação. `COMPLETED` continua sendo incluído no cálculo; somente `CANCELLED` é excluído. Desconto de cabeçalho de contrato possui persistência separada e não é apagado por esse fluxo. Não afirmar que o caso humano removeu o cabeçalho sem prova.

## Contrato da correção

`PUT /api/v1/company/contracts/{contractId}/services/{serviceId}` continua atualizando perfil/status/preço/quantidade conforme os campos enviados, com200direto e202para aprovação. Se `discountType` e `discountValue` forem omitidos ou ambosnull, preserva o desconto vigente no banco dentro da transação; não é pedido de reversão. Aprovação usa o desconto corrente no momento da aplicação, nunca copia valores financeiros antigos da proposta.

Um par explícito idêntico ao desconto vigente é aceito como eco, sem alteração do desconto. Um par explícito diferente deve usar o endpoint dedicado de desconto, com autorização DISCOUNTS; a edição operacional não transforma SERVICES.UPDATE em permissão de trocar/reverter desconto. Retorna409ProblemDetails/codeBUSINESS_RULE_VIOLATION/reasonSERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION, comcorrelationId. Mensagem sugerida: “Para alterar o desconto, use a ação específica de desconto do serviço.” Par incompleto é400. A criação mantém seu contrato atual. Reversões continuam explícitas, auditadas e sujeitas à alçada própria.

Frontend deve continuar omitindo os campos de desconto no formulário operacional. Não copiar um desconto antigo para o PUT, não recalcular totais localmente e não reaplicar desconto para compensar a falha. Usar os totais oficiais retornados e recarregar a leitura após aplicação;202 continua somente uma proposta.

## Validação e limites

Reprodução e testes usam PostgreSQL temporário. Cenário: serviço300000 comdesconto10% permanece270000 após concluir; comdesconto de cabeçalho5%, contrato permanece256500. Aprovação posterior deve preservar um desconto legítimo mais recente, sem restaurar snapshotantigo. Tenant, permissões, auditoria, composição e limites de faturamento continuam obrigatórios.

Sem migration, seed ou reparo de dados reais. A correção impede perdas futuras; registros já alterados não terão desconto reconstruído ou reaplicado automaticamente. Qualquer recuperação de dado real exige origem histórica comprovada e autorização própria, sem compensar NF ou caixa.

Validação concluída:16 testes distintos/4 suítes, Checkstyle main/test e bootJar aprovados; reprodução anterior dos2 defeitos preservada. OpenAPI4BFEA6EB/148paths/257schemas; JAR5E4601A1 com apenas3classes alteradas em relação ao E97C, sem mudanças de migrations/libs/config. API viva200/contrato compartilhado idêntico,401anônimo, schema91 e ambiente privado preservados. [Pacote](F03-16-SERVICE-DISCOUNT-PRESERVATION-CANDIDATE.md).

Frontend confirmou integração e jornada concluídas:37testes/3arquivos e1E2E em4larguras, lint/build/scanner/typecheck aprovados; preservação dos totais oficiais ePUT sem campos de desconto conferidos. Recuperação de descontos já apagados é uma etapa separada, pendente de identificação/confirmacão do caso real.
