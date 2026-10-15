# Inventário de segredos por ambiente

Este inventário registra somente nomes, finalidade e operação. Nunca registre
valores de segredo, tokens, senhas ou chaves neste arquivo.

| Variável | Finalidade | Local | Teste | Produção | Rotação |
|---|---|---|---|---|---|
| `JWT_SECRET` | Assinatura de tokens de acesso | `.env` ignorado | valor exclusivo de teste | gerenciador de segredos | invalidar/rotacionar tokens conforme política de sessão |
| `EMPLOYEE_PAYMENT_ENCRYPTION_KEY` | Cifrar conta, dígito e Pix de funcionário | `.env` ignorado e diferente da JWT | valor exclusivo de teste | gerenciador de segredos | recifrar registros antes de remover chave anterior |
| `POSTGRES_PASSWORD` | Acesso ao PostgreSQL | `.env` ignorado | Testcontainers | gerenciador de segredos | trocar credencial e atualizar a conexão controladamente |
| `MAIL_PASSWORD` | Autenticação SMTP | `.env` ignorado quando aplicável | não usado | gerenciador de segredos | trocar no provedor e atualizar a configuração |
| `ASAAS_API_KEY` | Integração com gateway de cobrança | `.env` ignorado quando habilitado | não usado | gerenciador de segredos | trocar pelo fluxo do provedor e validar webhook/reconciliação |
| `ASAAS_WEBHOOK_TOKEN` | Validação do webhook do gateway | `.env` ignorado quando habilitado | não usado | gerenciador de segredos | coordenar troca com o provedor antes de revogar o anterior |

## Regras operacionais

- Valores locais ficam somente em `.env`, que é ignorado pelo Git; o exemplo
  versionado contém apenas placeholders.
- Produção não lê arquivo `.env`: injeta valores por serviço de segredos ou
  variáveis protegidas do ambiente de execução.
- Segredos de finalidades distintas não podem ser reutilizados.
- Uma rotação deve ser registrada operacionalmente, validada em homologação e
  aplicada sem expor valores em logs, tickets, auditoria ou commits.
