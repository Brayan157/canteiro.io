# Integração — privacidade operacional, primeiro recorte

Data: 22/10/2026. Backend somente; sem deploy/reinício/push ou edição no repositório frontend. Este guia descreve o código/contrato em validação, não a versão publicada em localhost:8081 nem certificação jurídica.

## Funcionários: lista mínima, perfil preservado

`GET /api/v1/company/employees` mantém tenant da sessão, `EMPLOYEES.READ` e paginação. Sua resposta passa a `PageResponse<EmployeeListItemResponse>`; a paginação não muda.

Cada item inclui somente:

- `id`, `fullName`, `preferredName`, `jobTitle`, `department`, `registrationNumber`;
- `employmentType`, `currentlyEmployed`, `active`, `userId`, `photoUrl`.

Não há `documentType`, `documentNumber`, `pisMasked`, `phone`, `birthDate`, `gender`, `ethnicity`, `compensationFrequency` ou `compensationAmount` na lista. Usar nome/matrícula na tabela e gerar tipos do OpenAPI, sem DTO manual. Foto continua sendo rota autenticada com permissão própria de leitura, nunca URL pública/storageKey.

**O salário não foi removido do sistema.** `GET /api/v1/company/employees/{employeeId}`, POST de cadastro e PUT de atualização continuam com `EmployeeResponse` completo, incluindo `compensationFrequency`/`compensationAmount` e os demais campos do perfil. O responsável consulta remuneração no detalhe. Não abrir automaticamente o detalhe de todos os itens para reconstruir tabela de dados pessoais; não converter ausência na lista em valor zero ou cadastro vazio.

O PUT continua completo: carregar o perfil atual antes de editar e preservar os campos que não foram alterados. Nunca montar o PUT somente com o item mínimo da lista nem enviar `null` para apagar campos não carregados. A futura separação de permissões/edições parciais ainda não foi implementada neste recorte.

Filtros e ordenações sensíveis foram removidos da listagem: não há `document`, `compensationFrequency`, `compensationMin`, `compensationMax`, `compensationAmount` nem `compensationFrequency` em query/ordenação. **Permissões granulares para o detalhe e edição de RH permanecem pendentes em F09-09.** Não declarar que há separação RH/operação só porque a lista foi reduzida. O estado de qualquer consulta pessoal no navegador deve ser efêmero, sem URL da aplicação, histórico, estado de rota ou storage. Sanitização de logs da API/proxy/telemetria e revisão de URLs antigas são pendências separadas de backend/infra.

## Auditoria e propostas

Novos snapshots CREATE/UPDATE do perfil deixam de copiar `gender`, `ethnicity` e `compensationFrequency`; preservam identificação operacional e indicadores como `hasDocument`, `hasPis` e `hasCompensationAmount`, sem valores completos desses dados. Vale para aplicação direta e criação aprovada, pelo mesmo gerador de snapshot. Não há mudança no DTO público da auditoria/central de aprovação.

Nenhum evento antigo foi modificado, excluído ou anonimizado. Propostas de aprovação ainda contêm os dados necessários para aplicar o cadastro; cifragem em repouso e ciclo de retenção dessas cópias estão pendentes (F09-10). A projeção pública segura da central não equivale a proteção completa da persistência.

## Anexos de NF

`POST /api/v1/company/invoices/{invoiceId}/attachments` mantém multipart (`attachmentType`, `file`), resposta `200 InvoiceAttachmentResponse`, alçada direta INVOICES.CREATE e isolamento da NF no tenant.

- Extensão, MIME e conteúdo devem corresponder; limite de 10 MiB.
- PDF deve ser estruturalmente legível, ter páginas e não ser cifrado/senha.
- XML deve ser bem formado; DOCTYPE, entidades externas e XInclude são rejeitados. Limites: profundidade 128, 100.000 elementos e 100 atributos por elemento.
- Falha retorna `400 Problem Details` com `correlationId`, sem conteúdo/erro bruto do parser e antes de storage/metadados/auditoria de sucesso. `403` sem alçada direta; `404` sem NF no tenant. O fluxo de upload não cria proposta `202`.
- Não é validação de assinatura, schema ou autorização da NF, nem scanner de malware/conteúdo ativo. Antimalware/quarentena e tratamento dos anexos históricos permanecem F09-12.

## Planejamento, sem contratos executáveis novos

SDD 5.7 e Fase 12 do backlog registram H.H., lotação por obra, transferências temporárias, vedação de sobreposição efetiva, acesso granular por obra, apontamento diário e evolução para integração de ponto/espelho/jornadas/atestados. São tarefas **pendentes**, não endpoints/DTOs disponíveis. Venda, custo trabalhista, marcação real e quantidade faturável são separados. O usuário esclareceu que “remunerado” significa DSR: pretende cobrá-lo comercialmente quando todo o período semanal trabalhado for H.H. para aquele tomador. Fórmula, semana, elegibilidade e casos especiais permanecem pendentes; não presumir 1/6, um dia ou rateio, transferir obrigação trabalhista ou contar descanso como hora trabalhada. Não criar telas/integração com DTO fictício a partir desse planejamento.

Resultado por obra permanece separado do corporativo: gastos/tributos COMPANY somente no corporativo, sem rateio silencioso. Não existe relatório oficial de margem a corrigir neste recorte nem regra fiscal universal nova.

## Pendências antes da abertura pública

Identidade do responsável, canal oficial e publicação do aviso de privacidade aguardam definição humana; não inventar empresa/contato nem presumir que somente pessoa jurídica pode ser controladora. Permanecem inventário/finalidade/base aplicável, direitos/retenção por categoria, cópias em propostas/auditoria/backups, proteção de documentos e cifragem adicional (F08-11, F09-02/03/09/10/11/12). Nenhuma eliminação automática ou alteração de retenção imutável foi autorizada.

## Validação e export

Fonte: `/v3/api-docs` da aplicação em teste; export em `postman/canteiro-io.openapi.json`. A geração e as provas deste recorte são registradas ao concluir a validação. Não implica publicação no servidor em uso, cópia automática ao outro repositório ou aplicação de migrations reais. Não há mudança de schema/banco neste recorte, portanto não foi criada migration vazia.
