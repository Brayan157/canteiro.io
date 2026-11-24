# Mapa do backend Canteiro.io

Localize o domínio abaixo. Leia os trechos aplicáveis do [SDD](SDD-MVP-Gestao-Financeira.md) e do [AGENTS.md](../AGENTS.md), que definem as regras e invariantes. Use o [backlog](CODEX-IMPLEMENTATION-BACKLOG.md) para acompanhar entregas e pendências.

Para integrar, consulte o [guia de endpoints](FRONTEND-API-ENDPOINTS.md) e o [OpenAPI exportado](../postman/canteiro-io.openapi.json). Confirme a disponibilidade na instância que o frontend usa em `GET /v3/api-docs`. O [`OpenApiContractIntegrationTest`](../src/test/java/com/renovar/canteiro/io/OpenApiContractIntegrationTest.java) valida o contrato executável e pode exportá-lo via `OPENAPI_EXPORT_PATH`.

## Fase 6 do backend — fornecedores, contas a pagar e caixa

Regras no [SDD §5.5](SDD-MVP-Gestao-Financeira.md#55-contas-a-pagar-custos-despesas-e-lucro) e no AGENTS.md §10. Use a [Fase 6 do backlog](CODEX-IMPLEMENTATION-BACKLOG.md#fase-6--gastos-contas-a-pagar-impostos-e-resultado-por-obra) para ver a ordem acordada com a Fase 9 do frontend.

| Domínio | Entrada HTTP | Aplicação | Guia e termos de busca |
|---|---|---|---|
| Fornecedores e categorias | [`PayableCatalogController`](../src/main/java/com/renovar/canteiro/io/payables/api/PayableCatalogController.java) | [`PayableCatalogService`](../src/main/java/com/renovar/canteiro/io/payables/application/PayableCatalogService.java) | [Cadastros](FRONTEND-PAYABLE-CATALOGS.md) · `suppliers`, `financial-categories`, `expectedVersion` |
| Títulos a pagar | [`PayableController`](../src/main/java/com/renovar/canteiro/io/payables/api/PayableController.java) | [`PayableService`](../src/main/java/com/renovar/canteiro/io/payables/application/PayableService.java) | [Títulos e grupos](FRONTEND-PAYABLES.md) · `financialScope`, `financialGroupId`, `operationId` |
| Saldo, baixas e estornos | [`PayableSettlementController`](../src/main/java/com/renovar/canteiro/io/payables/api/PayableSettlementController.java) | [`PayableSettlementService`](../src/main/java/com/renovar/canteiro/io/payables/application/PayableSettlementService.java) | [Baixas e saldo](FRONTEND-PAYABLE-SETTLEMENTS.md) · `balance`, `settlements`, `reversals` |
| Contato do fornecedor e instruções de pagamento | `PayableCatalogController`, [`PayablePaymentInstructionController`](../src/main/java/com/renovar/canteiro/io/payables/api/PayablePaymentInstructionController.java) | `PayableCatalogService`, [`PayablePaymentInstructionService`](../src/main/java/com/renovar/canteiro/io/payables/application/PayablePaymentInstructionService.java) | [Contatos, Pix, boleto e transferência](FRONTEND-SUPPLIER-CONTACTS-AND-PAYMENT-INSTRUCTIONS.md) · `contactName`, `payment-instructions`, `VIEW_PAYMENT_DETAILS` |
| Caixa realizado, empresa e obra | [`RealizedCashController`](../src/main/java/com/renovar/canteiro/io/reporting/api/RealizedCashController.java) | [`ReadRealizedCashUseCase`](../src/main/java/com/renovar/canteiro/io/reporting/application/ReadRealizedCashUseCase.java) | [Realizado](FRONTEND-REALIZED-CASH.md) · `cash-flow`, `realized` |
| Caixa previsto por vencimento | [`CashDueController`](../src/main/java/com/renovar/canteiro/io/reporting/api/CashDueController.java) | [`ReadCashDueUseCase`](../src/main/java/com/renovar/canteiro/io/reporting/application/ReadCashDueUseCase.java) | [Previsto](FRONTEND-CASH-DUE.md) · `cash-flow`, `due` |
| Comparação de pagamentos | [`CashPaymentsComparisonController`](../src/main/java/com/renovar/canteiro/io/reporting/api/CashPaymentsComparisonController.java) | [`CompareCashPaymentsUseCase`](../src/main/java/com/renovar/canteiro/io/reporting/application/CompareCashPaymentsUseCase.java) | [Comparação entre períodos](FRONTEND-CASH-PAYMENTS-COMPARISON.md) · `payments/comparison`, `coverage` |

## Demais domínios

Os nomes abaixo são termos de busca no [código Java](../src/main/java/com/renovar/canteiro/io).

| Domínio | Classes e termos de busca | Onde ler |
|---|---|---|
| Identidade, onboarding e acesso | `AuthenticationController`, `CompanyOnboardingController`, `CompanyAccessController`, `tenancy` | SDD §§4, 6.3–6.4 · [Ativação](FRONTEND-ACCOUNT-ACTIVATION.md), [recuperação de senha](FRONTEND-PASSWORD-RESET.md) |
| Aprovação e auditoria | `ChangeRequestController`, `ChangeRequestDecisionService`, `AuditEventController`, `AuditPresentationService` | SDD §§4.3–4.4 · [Guia de endpoints](FRONTEND-API-ENDPOINTS.md) §§2.9–2.9.1 |
| Plataforma, catálogo e assinaturas | `PlatformCatalogController`, `PublicCatalogController`, `PlatformSubscriptionController`, `PlatformCompanyController` | SDD §5.6 · [Guia de endpoints](FRONTEND-API-ENDPOINTS.md) §§2.16–2.19 |
| Funcionários e privacidade | `EmployeeController`, `payment-accounts`, `payment-details/reveal`, `photo` | AGENTS.md §6.1 · [Perfil](FRONTEND-EMPLOYEE-PROFILE.md), [privacidade](FRONTEND-PRIVACY-REMEDIATION.md) |
| Clientes finais, obras e contratos | `FinalCustomerController`, `WorkController`, `ContractController`, `ServiceTemplateController` | SDD §5.1 · [Guia de endpoints](FRONTEND-API-ENDPOINTS.md) §§2.4–2.6, [ciclo contratual](FRONTEND-CONTRACT-LIFECYCLE.md) |
| Medições e documentos | `MeasurementController`, `MeasurementAccumulatedController`, `GetAccumulatedMeasurementUseCase`, `MeasurementContractCompositionController`, `MeasurementDocumentController`, `MeasurementAccumulatedDocumentController` | SDD §§5.3, 9 · [Medição contínua](FRONTEND-MEASUREMENT-CONTINUOUS.md), [moeda e relatórios](FRONTEND-MEASUREMENT-CURRENCY-REPORTS.md) |
| Faturamento, NF e tributos | `BillingControlController`, `NetBillingController`, `InvoiceController`, `InvoiceXmlPreviewController`, `TaxObligationController` | SDD §§5.4–5.4.1 · [Faturamento líquido](FRONTEND-NET-BILLING.md), [XML e cobertura](FRONTEND-INVOICE-XML-AND-COVERAGE.md) |
| Contas a receber e recebimentos | `ReceivableController`, `InvoiceSettlementController`, `InvoiceSettlementService` | SDD §5.4 · [Contas a receber](FRONTEND-RECEIVABLE-READ.md), [recebimento por NF](FRONTEND-MANUAL-INVOICE-SETTLEMENTS.md) |
| Base técnica e operação | `application.properties`, `db/migration`, `OpenApiContractIntegrationTest` | SDD §10 · [ADRs](adr/README.md), [configuração por ambiente](fase-0/F00-04-configuracao-por-ambiente.md), [segregação de segredos](fase-9/F09-01-segregacao-de-segredos.md) |
