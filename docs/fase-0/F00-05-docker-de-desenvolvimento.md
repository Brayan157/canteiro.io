# F00-05 — Docker de desenvolvimento

- Estado: concluído
- Data: 2026-08-10

## Componentes

- `Dockerfile`: build multiestágio com Java 21 e execução como usuário sem
  privilégios.
- `docker-compose.yml`: PostgreSQL 17 com volume persistente, health check e
  API Spring conectada pelo nome interno `postgres`.
- `.dockerignore`: reduz o contexto e impede o envio de `.env` ao build.

## Como iniciar

```powershell
Copy-Item .env.example .env
docker compose up --build
```

A API fica disponível em `http://localhost:8080` por padrão e o PostgreSQL em
`localhost:5432`. Ajuste as portas no `.env` quando necessário.

O build da imagem gera o `bootJar` sem executar a suíte de testes: testes de
integração usam Testcontainers e devem ser executados no pipeline/ambiente de
desenvolvimento, não durante a construção da imagem de runtime.

## Storage privado local (15/10/2026)

O serviço api monta o volume `canteiroio-api-private-storage` em
`/app/var/storage`, com LOCAL_STORAGE_BASE_PATH apontando para esse diretório.
A imagem prepara o alvo com dono canteiro e modo 0700, mantendo a aplicação
como usuário não root. O volume conserva anexos entre recriações da API; não
deve ser removido em um restart comum. PostgreSQL mantém seu volume próprio.
Inventário, preservação e prova de persistência com artefato técnico isolado:
[F08-23](../F08-23-LOCAL-PRIVATE-STORAGE.md). Não substitui backup externo ou
object storage de produção.
