# FRONT — Ativação de conta por link de e-mail

## O que mudou

Após o cadastro público de uma empresa, a API continua criando o proprietário inicial como
`PENDING_ACTIVATION` e enviando um convite. O e-mail agora contém o botão **Ativar minha conta**.

O frontend não deve pedir, exibir, salvar ou tentar gerar um token manualmente.

## Link recebido pelo cliente

O backend monta o link a partir de `FRONTEND_ACTIVATION_URL`. Em ambiente local, o padrão é:

```text
http://localhost:5173/ativar-conta#activationToken=<token-de-uso-unico>
```

O token está no fragmento (`#`), não na query string. O navegador não envia esse fragmento ao
servidor HTTP, reduzindo exposição em logs e no cabeçalho `Referer`.

## Implementação necessária no frontend

Criar a rota pública `/ativar-conta` com uma tela para criação de senha.

Ao abrir a página:

1. Ler `activationToken` de `window.location.hash` com `URLSearchParams`.
2. Remover o fragmento imediatamente com `history.replaceState(null, '', window.location.pathname)`.
3. Se o token não existir, mostrar uma mensagem genérica de link inválido ou expirado.
4. Solicitar senha e confirmação de senha; nunca renderizar o token.
5. Enviar o token apenas no corpo da requisição abaixo, usando HTTPS.

```http
POST /api/v1/auth/activate
Content-Type: application/json

{
  "activationToken": "<token-extraido-do-fragmento>",
  "password": "senha-definida-pelo-usuario"
}
```

Resposta de sucesso: `204 No Content`.

Após sucesso, redirecionar para o login e informar que a conta foi ativada. Para qualquer erro,
mostrar uma mensagem genérica de convite inválido, expirado ou já utilizado; não revelar detalhes
sobre a conta.

## Reenviar ativação

Na tela de login ou ativação, disponibilizar o link **Reenviar ativação**. Ele solicita apenas o
e-mail e chama:

```http
POST /api/v1/auth/activation/resend
Content-Type: application/json

{ "email": "owner@example.com" }
```

Sempre que o JSON for válido, a resposta é `204 No Content`, inclusive para e-mail inexistente ou
conta já ativada. Mostrar sempre: “Se houver uma conta pendente para este e-mail, enviamos um novo
link de ativação.” O frontend não deve inferir a existência da conta pela resposta.

## Ambientes

| Ambiente | `FRONTEND_ACTIVATION_URL` | Destino do e-mail |
| --- | --- | --- |
| Local | `http://localhost:5173/ativar-conta` | Mailpit |
| Homologação | URL HTTPS do frontend de homologação | SMTP configurado |
| Produção | URL HTTPS pública do frontend | SMTP configurado |

Em produção, `FRONTEND_ACTIVATION_URL` é obrigatório. O backend não devolve o token em respostas
de onboarding e persiste apenas o hash dele.
