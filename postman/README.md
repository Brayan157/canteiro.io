# Postman — Fase 2

Importe os dois arquivos no Postman:

- `canteiro-phase-2.postman_collection.json`;
- `canteiro-local.postman_environment.json`.

Selecione o ambiente local e preencha `platformOwnerEmail` e `password` com um usuário já ativado que tenha o papel global `PLATFORM_OWNER`.

Fluxo seguro recomendado:

1. Faça login como proprietário da plataforma.
2. Em **Catálogo da plataforma**, execute `Criar plano de demonstração` e `Adicionar preço vigente ao plano`.
3. Em **Onboarding de Company**, execute `Criar Company com plano obrigatório`.
4. Ative a conta pelo token recebido no e-mail e faça login como proprietário da empresa.

5. Em **Acesso da Company**, execute os testes de permissões, papéis e colaboradores.

## F02-10 — webhook de pagamento

Para habilitar as duas chamadas de webhook, configure no `.env` uma credencial real de sandbox e reinicie a API:

```properties
ASAAS_ENABLED=true
ASAAS_API_KEY=$aact_hmlg_...
ASAAS_WEBHOOK_TOKEN=um-token-seguro
```

Copie esse mesmo token para a variável `asaasWebhookToken` do ambiente Postman. O teste autenticado retorna `200`; o teste com token inválido retorna `401`. Para confirmar o efeito financeiro do webhook é necessário informar um `externalChargeId` que já esteja em `platform_charge`, pois ainda não existe endpoint administrativo para gerar cobranças manualmente.

As respostas de criação guardam IDs necessários em variáveis da collection. As requisições de desativação ficam em subpastas separadas e devem ser usadas somente nos registros `DEMO` criados pela própria collection.

O onboarding cria o proprietário pendente e atribui a ele o papel inicial `Company Administrator`, com todas as permissões ativas do catálogo. Após ativar a conta, esse proprietário pode administrar usuários, papéis e alçadas da própria Company.
