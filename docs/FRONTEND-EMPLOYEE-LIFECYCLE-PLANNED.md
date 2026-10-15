# Funcionários — vínculos, conformidade e documentos privados (planejado)

**Status:** contrato de planejamento da Fase 10. **Nenhuma rota deste arquivo está disponível no OpenAPI ou deve ser consumida pelo frontend ainda.** A integração começa somente após a publicação de `/v3/api-docs` e da cópia `openapi/canteiro.openapi.json` correspondente.

Base futura: `/api/v1/company/employees/{employeeId}`. O tenant sempre vem do JWT; o frontend nunca envia `companyId`.

## Conceito central

`Employee` é o perfil da pessoa. `EmployeeEmployment` é cada período de trabalho dela na empresa. Uma readmissão cria outro vínculo: não altera nem reabre o desligamento anterior.

Estados do vínculo:

- `PROBATION`: experiência em curso; possui `probationEndsOn`.
- `ACTIVE`: vínculo efetivado/em atividade.
- `NOTICE_PERIOD`: aviso prévio aberto; ainda não confirma desligamento.
- `TERMINATED`: vínculo encerrado; preserva data e motivo seguros.

O aviso prévio terá situação própria: `OPEN`, `REVOKED` ou `COMPLETED`. O fim previsto não desliga automaticamente a pessoa; uma ação explícita confirma o desligamento. Revogar o aviso preserva seu histórico e devolve o vínculo ao estado anterior.

## Rotas planejadas de vínculo

```http
GET  /api/v1/company/employees/{employeeId}/employments
POST /api/v1/company/employees/{employeeId}/employments
POST /api/v1/company/employees/{employeeId}/employments/{employmentId}/confirm-probation
POST /api/v1/company/employees/{employeeId}/employments/{employmentId}/notice-period
POST /api/v1/company/employees/{employeeId}/employments/{employmentId}/notice-period/revoke
POST /api/v1/company/employees/{employeeId}/employments/{employmentId}/terminate
```

Criação de vínculo também é a rota de **readmissão**, mas só será aceita quando não existir vínculo aberto para o funcionário.

Formato planejado para um vínculo:

```json
{
  "id": "uuid",
  "employmentType": "EMPLOYEE",
  "status": "PROBATION",
  "startedOn": "2026-10-05",
  "probationEndsOn": "2026-11-19",
  "confirmedOn": null,
  "terminatedOn": null,
  "terminationReason": null,
  "noticePeriod": null
}
```

Início de aviso prévio receberá `startedOn`, `expectedEndsOn` e `reason`. A confirmação de desligamento receberá `terminatedOn` e motivo seguro. Revogação exigirá justificativa. Datas e enumerações finais virão do OpenAPI, não de tipos manuais no frontend.

Uma mutação direta responderá com o vínculo oficial atualizado (`200` ou `201`, conforme a operação). Quando a alçada exigir aprovação, responderá apenas:

```json
{
  "mode": "REQUEST_APPROVAL",
  "changeRequestId": "uuid"
}
```

O frontend não deve alterar a situação local como se fosse oficial após `202`.

## Cursos, certificados e exames

O backend usará registros de conformidade, com `recordType` em `COURSE`, `CERTIFICATE` ou `PERIODIC_EXAM`:

```http
GET    /api/v1/company/employees/{employeeId}/compliance-records
POST   /api/v1/company/employees/{employeeId}/compliance-records
PUT    /api/v1/company/employees/{employeeId}/compliance-records/{recordId}
DELETE /api/v1/company/employees/{employeeId}/compliance-records/{recordId}
POST   /api/v1/company/employees/{employeeId}/compliance-records/{recordId}/attachments
GET    /api/v1/company/employees/{employeeId}/compliance-records/{recordId}/attachments
```

Campos esperados: `recordType`, `title`, `issuer`, `issuedOn`, `expiresOn`, `status` e metadados seguros do anexo. Para cursos/certificados, carga horária pode ser informada. Para exames, o contrato aceitará somente controle administrativo de aptidão e validade: diagnóstico, prontuário e resultado clínico detalhado não devem ser enviados nem exibidos.

## Documentos privados do funcionário

```http
GET    /api/v1/company/employees/{employeeId}/documents
POST   /api/v1/company/employees/{employeeId}/documents
DELETE /api/v1/company/employees/{employeeId}/documents/{documentId}
GET    /api/v1/company/employees/{employeeId}/documents/{documentId}/content
```

Categorias esperadas incluem `IDENTITY_DOCUMENT`, `ADDRESS_PROOF`, `EMPLOYMENT_DOCUMENT` e `OTHER`. O upload será `multipart/form-data`; somente PDF, PNG e JPEG serão aceitos. A resposta de listagem conterá identificação, categoria, nome seguro, tipo, tamanho, datas e situação — nunca `storageKey`, URL pública, conteúdo base64 ou URL permanente.

O download será autenticado, permissionado e responderá com `Cache-Control: no-store`. A UI pode apresentar/visualizar o arquivo, mas não deve transformar a rota em URL pública, salvar cópia em storage do navegador, enviar o conteúdo a analytics ou exibi-lo a usuários sem a permissão final que o OpenAPI definir.

## Alertas de vencimento

Uma agenda idempotente notificará usuários autorizados sobre:

- vencimento de contrato comercial;
- fim de experiência e de aviso prévio;
- curso, certificado, exame ou documento vencendo/vencido.

O alerta é informativo: não efetiva, não desliga e não bloqueia automaticamente o funcionário ou contrato. A futura consulta de alertas será paginada e filtrável; ela devolverá somente título, tipo, data de vencimento, estado e referência segura do funcionário/contrato, nunca documento, endereço, dado bancário ou conteúdo médico.

## Integração futura

Quando a Fase 10 estiver implementada, o frontend deverá:

1. substituir qualquer tipo manual pelos tipos gerados do novo OpenAPI;
2. tratar `202` como pendência de aprovação, sem atualizar o dado oficial localmente;
3. mostrar histórico de vínculos, não apenas um booleano de funcionário ativo;
4. usar a data e situação retornadas pelo backend para alertas, sem decidir efetivação/desligamento no cliente;
5. manter uploads e downloads privados fora de logs, telemetria, analytics e cache persistente.
