# F09-01 — Segregação de segredos por finalidade

- Estado: concluído
- Data: 2026-10-10

## Decisão aplicada

`JWT_SECRET` assina tokens e não pode criptografar dados de pagamento de
funcionários. A chave de conta, dígito e Pix é fornecida exclusivamente pela
variável `EMPLOYEE_PAYMENT_ENCRYPTION_KEY`.

## Operação por ambiente

1. Gere uma chave aleatória de ao menos 32 caracteres para cada ambiente.
2. Configure `EMPLOYEE_PAYMENT_ENCRYPTION_KEY` no gerenciador de segredos do
   ambiente publicado; nunca a versione em Git.
3. Mantenha-a diferente de `JWT_SECRET`.
4. A API não inicia sem essa variável, e o adaptador também rejeita chave vazia,
   menor que 32 caracteres ou igual à JWT.

## Migração do legado

Registros antigos de conta/Pix foram cifrados com a JWT antes desta separação.
A migration V74 os identifica como `LEGACY_JWT`. Na primeira inicialização com a
nova chave, o backend lê exclusivamente essas linhas com a JWT e as recifra com
`EMPLOYEE_PAYMENT_ENCRYPTION_KEY`, marcando-as `PAYMENT_DATA_KEY`. A JWT não é
usada para leituras operacionais após a recifragem. Se uma linha não puder ser
decifrada, a transação falha e a aplicação não deve ser publicada até correção.

## Rotação

A chave em uso cifra e decifra os registros existentes. Por isso, rotação exige
uma migração operacional controlada: manter a chave anterior disponível apenas
durante a recifragem, recifrar os dados em lotes auditáveis, validar leitura e
remover a chave anterior do gerenciador de segredos. Não altere a variável em
produção sem esse procedimento.

## Verificação

O Compose exige a variável antes de subir o contêiner. O teste unitário do
cifrador foi adicionado para cobrir chave válida e rejeição de chave ausente,
curta ou igual à JWT; sua execução fica pendente enquanto o Gradle local não
consegue produzir resultado de teste neste ambiente.
