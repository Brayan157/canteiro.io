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

As respostas de criação guardam IDs necessários em variáveis da collection. As requisições de desativação ficam em subpastas separadas e devem ser usadas somente nos registros `DEMO` criados pela própria collection.

O módulo de acesso da Company exige permissões já atribuídas. O onboarding atual cria o proprietário pendente, mas a atribuição inicial de papel não faz parte da F02-04.
