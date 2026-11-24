# Prévia XML e cobertura — definição incorporada à implementação

A definição inicial de 06/11/2026 evoluiu para as entregas F05-39/40/41. O contrato vigente, rotas efetivas, campos, permissões, avisos e limites estão em [FRONTEND-INVOICE-XML-AND-COVERAGE.md](FRONTEND-INVOICE-XML-AND-COVERAGE.md).

A decisão humana permite cobrir uma cobrança por serviços inteiros, sem fracionar o montante já cobrado de um serviço. Grupos projetados incluem todas as origens oficiais daquela empresa/contrato/cobrança/serviço; o POST manual e a aprovação revalidam o conjunto completo. Legado e recebimentos parciais permanecem preservados.

A prévia é stateless e não cria NF/anexo/imposto/conta/baixa/proposta/reserva. Retorna informações tributárias disponíveis com proveniência e revisão humana; não valida XSD/assinatura/autenticidade fiscal. Substituição é aviso, sem cancelar/substituir documento antigo. O XML privado não foi movido, copiado ao repositório ou usado como fixture. Pacote atômico formal F05-42 e publicação/migration persistente continuam fora desta entrega.

[Provas, export e pacote](F05-39-41-VALIDATION-2026-11-06.md). A instância local não foi atualizada: o build compartilhado contém V92 ainda pendente de autorização no banco persistente, separada deste recorte.
