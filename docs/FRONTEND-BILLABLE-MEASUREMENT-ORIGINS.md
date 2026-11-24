# F05-19 — Origens de medição disponíveis para faturamento

Data: 15/10/2026. Contrato do seletor de origem para `FRONT-F08-03`.

## Consulta

```http
GET /api/v1/company/billing-control/contracts/{contractId}/measurement-origins?page=0&size=20&sort=createdAt,desc
Authorization: Bearer <accessToken>
```

Exige **BILLING.READ e MEASUREMENTS.READ**. A segunda permissão protege títulos/referências da medição. Não exige `RECEIVABLES.READ`, pois não consulta recebimentos. Empresa vem exclusivamente da sessão, sem parâmetro de tenant.

Resposta `200`: `PageResponse<BillableMeasurementOriginResponse>` (`items`, `page`, `size`, `totalItems`, `totalPages`). Filtros exatos opcionais: `measurementId` e `contractServiceId`. Filtro inexistente, de outro contrato/tenant: página vazia, sem revelar sua existência. Contrato da rota inexistente/fora do tenant: `404`.

Paginação: `page >= 0`, `1 <= size <= 100`, padrão `0/20`. Único campo ordenável: `createdAt`, com direção `asc`/`desc`; desempate por UUID da composição na mesma direção. Padrão `createdAt desc, id desc`. Parâmetro/UUID/ordenação inválido ou offset não suportado: `400 Problem Details`. Sem autenticação: `401`; sem qualquer uma das duas permissões: `403`. Erros preservam `correlationId`.

## Cada linha é uma composição, não o total do serviço

Exemplo ilustrativo de item de `items`:

```json
{
  "measurementContractCompositionId": "adf1c9bf-d354-48cd-8d57-859b6e314f83",
  "contractId": "126a1900-502b-4985-8360-e3ee8565c530",
  "contractServiceId": "d31b499a-efc0-4685-9147-797bde22d17a",
  "measurementId": "93570d7d-bd2b-46c6-bbbb-697002c299c7",
  "measurementVersionId": "13ad64e2-c1e0-4842-9834-14fe503f984a",
  "measurementServiceId": "8b04d5e7-54ad-4c6e-8fbd-a50b796e724a",
  "continuousServiceId": "794b4b83-059f-4f7b-91ec-6c1f5829fb33",
  "measurementReference": "MED-001",
  "measurementTitle": "Serviços da fábrica",
  "serviceTitle": "Linha de vinhaça",
  "contractServiceName": "Tubulação contratual",
  "versionNumber": 2,
  "originType": "MEASUREMENT_PRICING",
  "status": "AVAILABLE",
  "currency": "BRL",
  "acceptedAmount": "100.00",
  "activeAllocatedAmount": "40.00",
  "availableToBillAmount": "60.00",
  "createdAt": "2026-10-15T15:00:00Z"
}
```

`measurementReference` e `continuousServiceId` podem ser nulos em origens antigas compatíveis. `serviceTitle` é o título do grupo congelado, não uma chave. O destino `contractServiceId` vem da composição **imutável**, nunca do destino atual do serviço contínuo. Vários avanços/medições podem retornar o mesmo serviço contratual em linhas diferentes; não agrupá-los nem escolher origem por nome.

`status = AVAILABLE` significa: composição aplicada, versão `ACCEPTED`, sem reversão/redução aceita não reconciliada, serviço não cancelado e capacidade positiva. Rascunhos, enviados, pendentes, rejeitados, origens aguardando aprovação, revertidas, reduzidas sem reconciliação e esgotadas não aparecem. A substituição corretiva aparece apenas após sua composição oficial.

Valores são strings decimais canônicas de duas casas, na moeda imutável do contrato. `acceptedAmount` é o líquido da composição; `activeAllocatedAmount` vem somente das alocações canônicas ativas dessa origem; `availableToBillAmount` é sua capacidade remanescente. NF/recebimento não a consomem novamente. Cancelamento oficial de alocação pode reabrir a capacidade; proposta `202` não a altera.

## Uso no formulário

1. Consultar a rota do contrato selecionado e paginar no servidor.
2. Exibir título/referência da medição, versão, serviço e capacidade retornada.
3. Manter juntos `measurementContractCompositionId` e `contractServiceId` da linha selecionada.
4. Enviar esses IDs em `POST /api/v1/company/billing-installments` para alocação `SERVICE` de origem de medição. Envio e conclusão desse formulário são `FRONT-F08-04`.

Este GET não reserva saldo e não substitui o controle contratual. O POST continua validando origem, serviço, desconto e limite global do contrato sob lock/transação. Outra ação pode tornar a consulta desatualizada: tratar `409` e recarregar. Não usar totais consolidados de `financial-summary`, títulos ou destinos atuais para inventar capacidade por composição. Base negociada sem origem continua pelo contrato existente, não por uma origem fictícia nesta lista.

Leitura não altera entidades, preços, aprovação, alocações ou auditoria. Não houve alteração de schema nem de fórmula financeira.

## Verificação e publicação

Implementação e contrato concluídos. `postman/canteiro-io.openapi.json` foi gerado diretamente de `GET /v3/api-docs` da aplicação de integração com o gateway desabilitado: OpenAPI **3.1.0**, **124 paths**, **195 schemas**. Contém `BillableMeasurementOriginResponse`, `PageResponseBillableMeasurementOriginResponse`, resposta `200 application/json`, erros Problem Details e `bearerAuth`. O teste contratual geral também validou o perfil com gateway habilitado, em artefato separado de validação.

SHA-256 do snapshot entregue em F05-19 (o arquivo exportado evolui; para o contrato mais recente com leitura de parcelas, veja [F05-20](FRONTEND-BILLING-INSTALLMENT-READ.md)):

```text
A1C255D0CD1C187ED0DA4DA06CBD66B88367BBA3EBAB72BF55FAA1D28F3A1E12
```

Validação: **44 testes em cinco suítes**, zero falhas/erros/ignorados; Checkstyle main/test sem erros. Inclui a regressão completa de medição contínua, consulta por origem, limites/paginação, separação de composições no mesmo serviço, capacidade parcial/esgotamento/cancelamento, ausência de efeito `202`, correções pendentes/revertidas, autorização/tenant e bloqueio de assinatura. A evidência XML local está em `tmp/validation-billable-origins/final` (ignorada pelo Git).

O teste de parâmetros revelou que UUID inválido era tratado como `500`; passou a `400 Problem Details`, sem ecoar seu conteúdo. O cenário anônimo também corrigiu o filtro de assinatura para delegar à autenticação antes de exigir tenant, retornando `401` e mantendo bloqueios `403` para empresas restritas. A validação em servidor HTTP real desse recorte continua registrada em F09-08.

Não foi feito deploy, reinício da API local, recriação de seus containers nem mudança de schema/dados reais. Os testes usam PostgreSQL/Testcontainers isolados. O export permite regenerar tipos e validar o frontend; a disponibilidade da rota na instância antiga de `localhost:8081` depende de publicação posterior separada. Não houve edição manual do JSON para simular a rota.
