# Funcionários — cadastro completo

Base: `/api/v1/company/employees`. O tenant é sempre derivado do JWT.

## Alteração de contrato: documento do funcionário

`EmployeeResponse` não possui mais `documentNumberMasked`. O backend agora devolve o documento completo e normalizado em `documentNumber`. Atualize o tipo/mapeador do frontend e as telas de listagem e detalhe para usarem `employee.documentNumber`.

```json
{
  "id": "e7d8a8fb-f4f4-4d36-9536-2d9178f753f6",
  "fullName": "Ana da Silva",
  "documentType": "CPF",
  "documentNumber": "52998224725",
  "registrationNumber": "REN-001",
  "active": true
}
```

O valor é retornado sem pontuação. Para CPF, se a interface precisar de apresentação pontuada, ela deve apenas formatar o valor para exibição; não substitua o valor recebido nem envie logs, analytics ou telemetria com ele.

Para consumir a alteração:

1. Atualize a cópia `openapi/canteiro.openapi.json` e execute `npm run generate:api-types`.
2. Remova `documentNumberMasked` do modelo local de funcionário e inclua `documentNumber?: string`.
3. Nas telas de listagem e detalhe, troque a condição e a renderização de `documentNumberMasked` por `documentNumber`.
4. Atualize fixtures e testes que ainda esperam o valor mascarado.

## Perfil obrigatório no cadastro

`POST /api/v1/company/employees` exige `fullName`, `jobTitle`, `documentType`, `documentNumber`, `birthDate`, `employmentType`, `employmentStartDate`, `currentlyEmployed`, `gender`, `compensationAmount` e `compensationFrequency`. O PIS continua opcional e **não** substitui o documento de identificação.

```json
{
  "fullName": "Ana da Silva",
  "jobTitle": "Montadora",
  "documentType": "CPF",
  "documentNumber": "529.982.247-25",
  "birthDate": "1990-05-20",
  "employmentType": "EMPLOYEE",
  "employmentStartDate": "2025-02-03",
  "currentlyEmployed": true,
  "gender": "FEMALE",
  "compensationAmount": "3500.00",
  "compensationFrequency": "MONTHLY",
  "ethnicity": "BROWN"
}
```

`registrationNumber` é opcional na entrada. Quando omitida, a API gera uma matrícula sequencial própria da empresa assinante no padrão `ABC-001`: as três primeiras letras da empresa e três dígitos. Exemplo para Renovar: `REN-001`, `REN-002`. Para quem não trabalha mais na empresa, envie `currentlyEmployed: false` e `employmentEndDate`.

`PUT /api/v1/company/employees/{employeeId}` atualiza o perfil completo. Use o mesmo conjunto de campos obrigatórios do cadastro; a matrícula é preservada pela API e não deve ser enviada.

`gender`: `MALE`, `FEMALE`, `NON_BINARY`, `OTHER` ou `PREFER_NOT_TO_INFORM`. `ethnicity` é opcional e autodeclarada: `WHITE`, `BLACK`, `BROWN`, `ASIAN`, `INDIGENOUS` ou `NOT_DECLARED`.

`documentType` pode ser `CPF`, `PASSPORT`, `FOREIGN_TAX_ID`, `NATIONAL_ID` ou `OTHER`. O `documentNumber` correspondente é obrigatório e retorna normalizado (somente letras e números) em `EmployeeResponse`. `pis` é opcional; informe os 11 dígitos, com ou sem pontuação. A resposta retorna somente `pisMasked`.

Remuneração é decimal canônico de duas casas e não calcula folha: `MONTHLY` (mensalista), `DAILY` (diarista) ou `HOURLY` (horista).

## Endereço

Após criar o funcionário:

`POST /{employeeId}/addresses` cria um endereço. São obrigatórios `street`, `city` e `country`; `primaryAddress` identifica o endereço principal. `GET /{employeeId}/addresses` lista os endereços.

`PUT /{employeeId}/addresses/{addressId}` substitui os dados de um endereço ativo. `DELETE /{employeeId}/addresses/{addressId}` faz exclusão lógica: retorna `204`, preserva o histórico e remove o endereço da listagem operacional.

`PUT /{employeeId}/addresses/{addressId}/primary` define o endereço principal ativo e remove essa marca dos demais endereços ativos do mesmo funcionário. Não recebe corpo e retorna o endereço marcado.

## Conta de pagamento e Pix

`POST /{employeeId}/payment-accounts` recebe conta bancária e/ou chave Pix. Para conta, informe `accountNumber`, `accountType` (`CHECKING`, `SAVINGS`, `PAYROLL`) e, quando existir, `accountCheckDigit` com um ou dois caracteres alfanuméricos. Para Pix, informe `pixKey`, `pixKeyType` (`CPF`, `EMAIL`, `PHONE`, `RANDOM`), `pixReceiverName` e `pixReceiverCity`. Nome e cidade são do titular real e confirmado da chave — nunca preencha automaticamente com dados do funcionário. Um dígito sem número de conta é inválido.

Os valores completos são cifrados no backend e não retornam nos endpoints de cadastro, listagem ou edição. `GET /{employeeId}/payment-accounts` devolve somente `accountNumberMasked` e `pixKeyMasked`. Esses endpoints apenas armazenam referência de pagamento; não efetuam Pix, transferência ou folha salarial.

`PUT /{employeeId}/payment-accounts/{accountId}` atualiza os metadados bancários e, quando enviados, substitui os números de conta ou chave Pix. Para alterar apenas banco/agência/tipo, omita os valores sigilosos: os valores já cifrados são preservados. `DELETE /{employeeId}/payment-accounts/{accountId}` faz exclusão lógica, retorna `204` e remove a referência da listagem operacional.

### Visualização para pagamento manual

O `accountCheckDigit` é opcional (até dois caracteres alfanuméricos) e é armazenado cifrado junto do número da conta. A listagem continuará retornando somente `accountNumberMasked` e `pixKeyMasked`; nenhum valor completo, inclusive o dígito, aparece por padrão.

Para quem realmente executa vale ou pagamento fora do sistema, existe a ação explícita **Ver dados para pagamento**, condicionada à permissão `EMPLOYEES.VIEW_PAYMENT_DETAILS`:

```http
POST /api/v1/company/employees/{employeeId}/payment-accounts/{accountId}/payment-details/reveal
```

Ele não recebe corpo e retorna `EmployeePaymentDetailsResponse` apenas naquela interação, com `id`, `bankName`, `bankCode`, `branch`, `accountType`, `accountNumber`, `accountCheckDigit`, `pixKeyType` e `pixKey`. Quando a referência Pix possui nome e cidade confirmados do titular, também devolve `pixCopyPaste` (BR Code estático completo, CRC válido) e `pixQrCode` (PNG em data URL). O payload não contém valor, cobrança, vencimento nem identificador financeiro: o pagador digita o valor no banco. Retorna `403` sem a permissão e `404` se funcionário ou referência não pertencem à empresa autenticada — inclusive quando a referência estiver inativa. A chamada cria auditoria com ator, funcionário, referência consultada, data/hora e origem, sem gravar os valores no evento.

Referências Pix antigas, salvas antes destes dados do titular existirem, retornam `pixCopyPaste` e `pixQrCode` como `null`. Edite a referência e envie `pixReceiverName` e `pixReceiverCity` confirmados para habilitar o QR.

No frontend, mostrar confirmação antes da chamada (“Esta visualização será registrada”), manter os dados apenas em estado de memória enquanto o modal estiver aberto, limpar ao fechar/navegar e nunca enviar esses valores para logs, analytics, telemetria, exportação ou armazenamento do navegador.

Não registre CPF, conta, Pix, remuneração ou endereço em logs, analytics, telemetria ou mensagens de erro do frontend.
