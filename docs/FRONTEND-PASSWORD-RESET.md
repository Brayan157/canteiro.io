# FRONT — Redefinição de senha por link de e-mail

## Alteração de experiência

A recuperação de senha segue o mesmo padrão da ativação de conta. Após informar o e-mail, a pessoa recebe um e-mail com o botão **Redefinir minha senha**; ela não deve copiar, visualizar ou digitar um token.

`POST /api/v1/auth/password-reset/request` continua respondendo `204 No Content` para um JSON válido, mesmo quando o e-mail não pertence a uma conta ativa. A interface deve sempre informar: “Se houver uma conta ativa para este e-mail, enviamos um link para redefinir a senha.”

## Link recebido

O backend monta o link a partir de `FRONTEND_PASSWORD_RESET_URL`. Em desenvolvimento, o padrão é:

```text
http://localhost:5173/redefinir-senha#passwordResetToken=<token-de-uso-unico>
```

O token está no fragmento (`#`), não na query string. Portanto ele não é enviado ao servidor HTTP, nem aparece no cabeçalho `Referer` do carregamento da página.

## Implementação necessária no frontend

Na rota pública `/redefinir-senha`:

1. Leia `passwordResetToken` de `window.location.hash` com `URLSearchParams`.
2. Remova o fragmento imediatamente com `history.replaceState(null, '', window.location.pathname)`.
3. Se o token não estiver presente, apresente uma mensagem genérica de link inválido, expirado ou já utilizado, com ação para solicitar outro e-mail.
4. Exiba somente os campos de nova senha e confirmação. Não exiba, não copie e não mantenha um campo de token no formulário.
5. Ao confirmar, envie o token apenas no corpo da requisição abaixo, usando HTTPS.

```http
POST /api/v1/auth/password-reset/confirm
Content-Type: application/json

{
  "passwordResetToken": "<token-extraido-do-fragmento>",
  "newPassword": "senha-definida-pelo-usuario"
}
```

Após `204 No Content`, redirecione para o login e informe que as sessões anteriores foram encerradas. Para falhas, não revele se o token pertenceu a uma conta: mostre apenas uma mensagem genérica e a ação para solicitar novo link.

## Ambientes

| Ambiente | `FRONTEND_PASSWORD_RESET_URL` | Destino do e-mail |
| --- | --- | --- |
| Local | `http://localhost:5173/redefinir-senha` | Mailpit |
| Homologação | URL HTTPS do frontend de homologação | SMTP configurado |
| Produção | URL HTTPS pública do frontend | SMTP configurado |

Em produção, `FRONTEND_PASSWORD_RESET_URL` é obrigatório. O backend persiste apenas o hash do token e o e-mail não apresenta o token isoladamente.
