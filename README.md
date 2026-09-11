# canteiro.io

## Acesso local de plataforma

O perfil `local` disponibiliza bootstraps idempotentes para acelerar a validação do catálogo e do onboarding. No `.env`, defina credenciais locais próprias e habilite o proprietário da plataforma:

```properties
BOOTSTRAP_PLATFORM_OWNER_ENABLED=true
BOOTSTRAP_PLATFORM_OWNER_EMAIL=owner@canteiro.local
BOOTSTRAP_PLATFORM_OWNER_PASSWORD=<senha-local-forte>
```

Ao iniciar `docker compose up --build -d api`, a API cria esse proprietário como `PLATFORM_OWNER` se o e-mail ainda não existir. O mesmo perfil garante o plano ativo `LOCAL_MVP`, com preço vigente, para que `GET /api/v1/catalog` e o onboarding público tenham ao menos uma oferta utilizável. Esses bootstraps não são carregados em produção.

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
