# F00-04 — Configuração por ambiente

- Estado: concluído
- Data: 2026-08-10

## Perfis

| Perfil | Uso | Banco |
| --- | --- | --- |
| `local` | execução manual e Docker Compose de desenvolvimento | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |
| `test` | testes com Testcontainers | conexão fornecida pelo container de teste |
| `prod` | implantação | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |

As configurações comuns não contêm credenciais. O perfil padrão é `local`; em
produção, defina explicitamente `SPRING_PROFILES_ACTIVE=prod`.

O arquivo `.env` é ignorado pelo Git. Copie `.env.example` para `.env` e
substitua a senha de desenvolvimento antes de iniciar os containers.
