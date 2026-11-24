# Canteiro.io — Backend

API REST para prestadoras de serviços acompanharem a execução e o financeiro de suas obras. O sistema organiza a relação entre cliente, obra, contrato e serviços, desde a medição e o aceite até o faturamento, os recebimentos e o caixa. Cada empresa administra sua própria equipe, papéis e alçadas. A interface web fica no [repositório do frontend](https://github.com/Brayan157/canteiro-front).

## Fluxos do produto

1. **Clientes, obras e contratos.** Cadastre o cliente final e suas obras. Cada obra pode ter vários contratos, cada um com serviços, valores e saldo faturável próprios. A obra consolida as informações dos contratos. [Ciclo contratual](docs/FRONTEND-CONTRACT-LIFECYCLE.md)
2. **Medições e aceite.** Lance avanços de execução e registre o aceite externo. Os itens aceitos ficam no histórico, e os próximos avanços usam novas revisões. Com vínculo de obra, contrato e destino de serviço, o incremento aceito pode compor o valor contratual conforme a alçada. [Medição contínua](docs/FRONTEND-MEASUREMENT-CONTINUOUS.md)
3. **Faturamento e entradas.** Crie cobranças a partir do saldo contratual. A cobrança aprovada gera um título a receber. Registre a nota fiscal sobre os serviços completos da cobrança e depois registre o recebimento pelos itens da NF. [Faturamento líquido](docs/FRONTEND-NET-BILLING.md) · [Cobertura da NF](docs/FRONTEND-INVOICE-XML-AND-COVERAGE.md) · [Recebimentos](docs/FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md)
4. **Despesas e caixa.** Cadastre fornecedores e categorias, lance contas a pagar e registre baixas. O caixa realizado usa as baixas e os estornos oficiais. O previsto usa os saldos em aberto por vencimento. Despesas gerais entram no caixa da empresa, enquanto o resultado de uma obra considera as despesas vinculadas a ela. [Contas a pagar](docs/FRONTEND-PAYABLES.md) · [Caixa realizado](docs/FRONTEND-REALIZED-CASH.md) · [Caixa previsto](docs/FRONTEND-CASH-DUE.md)
5. **Equipe e Diário de Obra.** Acompanhe lotações, atividades, equipe, ocorrências, clima e fotos privadas. Operações no Diário exigem permissão e concessão vigente naquela obra. Os registros operacionais do Diário são independentes de faturamento e pagamento. [Guia da API](docs/FRONTEND-API-ENDPOINTS.md)

## Executar localmente

Pré-requisitos para o ambiente completo: Git, Docker e Docker Compose. Clone o repositório e crie o `.env` a partir do [arquivo de exemplo](.env.example).

```sh
git clone https://github.com/Brayan157/canteiro.io.git
cd canteiro.io
cp .env.example .env
```

No PowerShell, a cópia é `Copy-Item .env.example .env`. Edite o `.env` antes de iniciar. Defina uma senha local para `POSTGRES_PASSWORD` e gere valores aleatórios independentes para `JWT_SECRET`, `EMPLOYEE_PAYMENT_ENCRYPTION_KEY` e `PAYABLE_PAYMENT_DATA_ENCRYPTION_KEY`. O `.env` fica fora do Git.

```sh
docker compose up --build -d
docker compose logs -f api
```

O Compose inicia PostgreSQL, Mailpit e API com o perfil `local`. Na inicialização, o Flyway aplica as migrações pendentes e o Hibernate valida o esquema. Com as portas padrão, a API fica em [localhost:8081](http://localhost:8081/v3/api-docs) e o correio local em [localhost:8025](http://localhost:8025). Para parar os serviços e preservar os volumes, use `docker compose stop`.

Para criar a primeira empresa, consulte `GET /api/v1/catalog`, selecione o plano `LOCAL_MVP` e use `POST /api/v1/onboarding/companies` com o ID do plano. O convite do proprietário chega no Mailpit. Com o frontend local na origem padrão `http://localhost:5173`, abra o link de ativação e defina a senha. O catálogo local é criado automaticamente. Se precisar também de um administrador **da plataforma**, habilite `BOOTSTRAP_PLATFORM_OWNER_ENABLED` e configure `BOOTSTRAP_PLATFORM_OWNER_EMAIL` e `BOOTSTRAP_PLATFORM_OWNER_PASSWORD` no `.env`. [Configuração por ambiente](docs/fase-0/F00-04-configuracao-por-ambiente.md) · [Ativação de conta](docs/FRONTEND-ACCOUNT-ACTIVATION.md)

### Asaas sandbox

A integração de cobrança da **assinatura do Canteiro.io** é desativada por padrão. Em uma execução local fora do Compose padrão, habilite `ASAAS_ENABLED` somente se precisar testar a integração. Use `ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3`, credenciais exclusivas de sandbox para `ASAAS_API_KEY` e `ASAAS_WEBHOOK_TOKEN`, e defina `ASAAS_USER_AGENT` e `ASAAS_WEBHOOK_ZONE` conforme o exemplo. Mantenha as credenciais no backend. [Arquivo de exemplo](.env.example)

## Autenticação e permissões

O login retorna um JWT de acesso, enviado como `Authorization: Bearer <token>` nas rotas protegidas. A renovação usa um cookie `HttpOnly`. `GET /api/v1/auth/me` mostra o contexto da sessão e as permissões efetivas. Nas rotas de empresa, o backend obtém o tenant da sessão autenticada. A administração da plataforma tem contexto e rotas próprios.

Os papéis concedem permissões por módulo e ação. Com alçada direta, a operação é aplicada e auditada. Com alçada somente para solicitar, a API retorna `202 Accepted` e cria uma solicitação. O dado oficial só muda após a aprovação de outra pessoa autorizada. Ter permissão de solicitar e aprovar no mesmo módulo concede alçada direta para a operação correspondente. [Autenticação, governança e auditoria](docs/FRONTEND-API-ENDPOINTS.md)

## OpenAPI e integrações

- [OpenAPI JSON da API em execução](http://localhost:8081/v3/api-docs).
- [Contrato OpenAPI versionado](postman/canteiro-io.openapi.json) e [coleções Postman](postman/README.md).
- [Guia de endpoints](docs/FRONTEND-API-ENDPOINTS.md) e [mapa do projeto](docs/MAPA-DO-PROJETO.md).

Ao integrar com uma instância específica, confira seu `/v3/api-docs` para identificar as rotas e os esquemas disponíveis naquele ambiente. O atalho `/swagger` redireciona para a interface do Swagger UI, cujo acesso depende da configuração de segurança da instância. Se a interface responder `401`, consulte o JSON ou importe-o no Postman.

## Stack e testes

Monólito modular em Java 21 e Spring Boot, com Spring MVC, Spring Security e Spring Data JPA. Usa PostgreSQL 17, Flyway, Gradle Wrapper, Docker Compose e OpenAPI. Os testes usam JUnit e PostgreSQL descartável via Testcontainers. O pipeline verifica os testes, o estilo com Checkstyle e a cobertura com JaCoCo.

Com JDK 21 e Docker em execução, rode o mesmo comando do CI:

```sh
./gradlew --no-daemon check jacocoTestReport
```

No Windows, use `.\gradlew.bat --no-daemon check jacocoTestReport`. [Workflow de CI](.github/workflows/ci.yml)

Para consultar as regras de domínio ou acompanhar o que está planejado, veja o [SDD](docs/SDD-MVP-Gestao-Financeira.md), os [ADRs](docs/adr/README.md) e o [backlog](docs/CODEX-IMPLEMENTATION-BACKLOG.md).
