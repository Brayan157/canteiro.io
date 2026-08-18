# canteiro.io

## Asaas sandbox

O adapter de pagamentos fica desabilitado por padrão. Para habilitá-lo localmente, crie uma conta em
`https://sandbox.asaas.com`, gere uma chave exclusiva de sandbox e configure no `.env`:

```properties
ASAAS_ENABLED=true
ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3
ASAAS_API_KEY=$aact_hmlg_substitua_pela_chave_do_sandbox
ASAAS_WEBHOOK_TOKEN=substitua_por_um_token-seguro
ASAAS_USER_AGENT=canteiro.io/0.0.1 (Java 21; sandbox)
ASAAS_WEBHOOK_ZONE=America/Sao_Paulo
```

A chave deve permanecer somente no backend. O adapter recusa URL e chave de produção.
