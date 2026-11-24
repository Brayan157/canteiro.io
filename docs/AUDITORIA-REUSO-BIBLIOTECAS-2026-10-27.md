# Auditoria de reutilização de bibliotecas — backend Canteiro.io

Data: 27/10/2026

Estado: auditoria estática concluída; recomendações não implementadas.

Branch observada: `fase-5-faturamento`. HEAD: `fb0dc4ece6b7a41598feb6c8ff0cc997337ec52a`.

## 1. Conclusão executiva

O backend não está reinventando Spring, criptografia, Excel, PDF, QR Code ou persistência. Essas funções já são delegadas a bibliotecas estabelecidas. Há, porém, código mecânico que pode ser simplificado e pontos onde podemos aproveitar melhor recursos já disponíveis.

Minha recomendação é:

1. Priorizar tratamento dos erros nativos do Spring e validação/decodificação limitada de imagens.
2. Trocar as quatro conversões manuais de hash para hexadecimal por `HexFormat`, sem adicionar dependência.
3. Fazer uma prova pequena de MapStruct e da validação condicional de CPF antes de adotar em vários módulos.
4. Evoluir a leitura XLSX com a API de eventos do próprio POI se o volume justificar.
5. Manter explícitas as regras financeiras, tenant, aprovação e histórico. Bibliotecas podem executar mecanismos; não podem escolher essas políticas pelo produto.

Não recomendo uma reescrita nem instalar todas as bibliotecas citadas. Uma função curta, correta e bem testada pode ser preferível a uma dependência adicional.

## 2. Escopo, método e limitações

Inventário do estado de trabalho, incluindo alterações anteriores ainda não commitadas:

| Item | Quantidade |
| --- | ---: |
| Arquivos Java em produção | 1.068 |
| Linhas Java de produção | 48.595 |
| Arquivos Java de teste | 172 |
| Linhas Java de teste | 21.448 |
| Migrations SQL Flyway | 89 |
| Arquivos terminados em Mapper.java | 37 |
| Arquivos com createNativeQuery | 14 |

Contagens de arquivos/linhas são inventário textual, não classificação por AST nem medição de cobertura.

Método: inventário de todos os módulos, dependências declaradas, buscas transversais por implementações técnicas, inspeção dirigida dos candidatos e dos pontos de domínio relacionados, leitura de testes representativos e consulta a documentação primária. AGENTS e SDD continuam sendo as referências de produto.

Esta revisão é abrangente por módulos e categorias, mas não significa leitura manual linha a linha de todos os 1.068 arquivos. Não é pentest, parecer jurídico, certificação criptográfica, medição de desempenho ou varredura completa de CVEs/licenças. Não foi resolvida novamente toda a árvore transitiva do Gradle.

Não foram executados build, testes, deploy, chamadas financeiras autenticadas ou migrations nesta auditoria. Os testes indicados adiante são critérios para futuras alterações, não resultados obtidos agora. Não foram lidos valores de `.env` ou registros reais de pessoas/finanças.

A única entrega nova deste trabalho é este relatório. Não altera código, dependências, SDD, backlog, OpenAPI, frontend, banco ou instância local.

## 3. O que já usa biblioteca

Evidência principal: [build.gradle.kts:40](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/build.gradle.kts:40).

| Responsabilidade | Reutilização existente | Decisão |
| --- | --- | --- |
| HTTP, injeção e transações | Spring MVC, Spring Boot, Spring Transactions | Manter |
| Persistência e consultas usuais | Spring Data JPA/Hibernate, PostgreSQL | Manter |
| Evolução de schema | Flyway | Manter; nunca editar migration aplicada |
| JSON e contratos decimais | Jackson com extensões pequenas próprias | Manter contrato explícito |
| Validação básica | Jakarta Bean Validation, Hibernate Validator via starter | Aproveitar mais onde aplicável |
| Senhas | Spring Security BCrypt, custo configurado 12 | Não substituir por hash próprio |
| JWT | Spring Security OAuth2 JOSE, Nimbus | Não adicionar outro motor JWT |
| Tokens aleatórios e digest | JDK SecureRandom, Base64 e MessageDigest | Manter primitivas |
| Cifra bancária de funcionário | JCA/JCE AES-GCM | Não é algoritmo AES caseiro; gestão de chaves merece evolução separada |
| E-mails | JavaMailSender e MimeMessageHelper | Não é cliente SMTP próprio |
| XLSX | Apache POI, incluindo SXSSF | Manter |
| PDF | Apache PDFBox, com cache temporário no gerador compartilhado | Manter |
| QR Code | ZXing | Manter; BR Code e CRC são composição própria |
| Imagens | JDK ImageIO | Reforçar limites e avaliar codec WEBP |
| Storage local | JDK NIO Files/Path | Manter adapter; SDK oficial quando houver object storage |
| Paginação e ordenação | Page, PageRequest e Sort do Spring Data | Manter whitelist própria |
| Logs/correlação | SLF4J e MDC | Já existem; não precisam ser “reinventados” |
| Testes | JUnit/Spring Test/Testcontainers PostgreSQL | Manter |
| Qualidade | Checkstyle e JaCoCo | Ampliar proteção, não substituir sem necessidade |

Versões explicitamente declaradas observadas: Spring Boot 4.1.0, Springdoc 3.1.0, POI 5.5.1, PDFBox 3.0.8, ZXing 3.5.3, Checkstyle 10.26.1 e JaCoCo 0.8.13. São versões do arquivo local, não afirmação de que são as mais recentes ou livres de vulnerabilidades.

## 4. Cobertura por módulo

“Coberto” aqui significa incluído no inventário/buscas e na classificação; profundidade maior nos achados com evidência individual.

| Grupo | Java | Avaliação de reutilização e fronteira |
| --- | ---: | --- |
| raiz da aplicação | 1 | Bootstrap Spring; sem oportunidade justificada de trocar framework |
| access | 50 | Spring Security apoia autenticação; alçada efetiva e permissões controladas permanecem próprias |
| bootstrap | 5 | Configuração/seed com Spring; não substituir governança de seed por dados automáticos indiscriminados |
| config | 1 | Springdoc/OpenAPI existente; geração contratual permanece fonte para frontend |
| contracts | 64 | JPA/BigDecimal; desconto, capacidade e composição são regras próprias |
| customers | 42 | Spring Data/Specifications; mapper mecânico pode ser candidato |
| employees | 45 | CPF, imagens e cifra concentram oportunidades; projeções privadas permanecem explícitas |
| governance | 47 | JPA/Jackson; aprovação, snapshots seguros e auditoria imutável não são simples CRUD |
| identity | 91 | BCrypt/Nimbus/JavaMail/JCA já usados; hex e templates são candidatos |
| measurements | 230 | POI/PDFBox e lotes existentes; importação, layouts e continuidade exigem políticas próprias |
| notifications | 7 | JavaMail/Spring Scheduled; regras persistidas de entrega não se confundem com retry genérico |
| payables | 35 | JPA/Bean Validation; escopo COMPANY/WORK e efeitos de pagamento permanecem próprios |
| platform/catalog | 64 | JPA/Bean Validation; vigência/ausência de sobreposição permanecem transacionais |
| platform/company | 30 | Mapper e imagens candidatos; identidade/estrutura da empresa protegidas |
| platform/subscription | 96 | RestClient/JPA/Scheduled; resiliência opcional sem eliminar idempotência |
| platform/support | 22 | Contexto e autorização próprios sobre Spring; não introduzir bypass global |
| receivables | 192 | SQL/JPA/BigDecimal; projeções podem melhorar; ledger, NF, saldo e estorno não terceirizados |
| shared | 22 | JDK/Spring já atendem muita coisa; erros e hex têm melhoria possível |
| tenancy | 6 | Filtros Spring e contexto confiável; isolamento exige consulta/constraint/testes |
| works | 18 | JPA/Specifications; hierarquia de obra e cliente é domínio |

Relatórios/documentos foram examinados onde estão implementados, especialmente dentro de measurements e receivables; não foi presumida a existência de módulos separados ainda não criados.

## 5. Achados priorizados

Prioridades indicam ordem sugerida de trabalho, não classificação CVSS:
P1 = robustez/correção a tratar primeiro; P2 = simplificação ou evolução relevante; P3 = opcional/condicionada a necessidade.

### A01 — P1 — Aproveitar o tratamento nativo de erros do Spring

Evidência: [GlobalExceptionHandler.java:118](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/api/error/GlobalExceptionHandler.java:118).

O handler trata algumas exceções MVC individualmente e termina com captura genérica de Exception que retorna 500. Não há tratamento explícito de todas as exceções nativas, como método HTTP não suportado e mídia não suportada. Pelo código, elas podem cair no fallback 500; cada caso deve ser confirmado por teste.

Recomendação: avaliar `ResponseEntityExceptionHandler`/`ErrorResponse` do próprio Spring, preservando code, reason, violations e correlationId. Não instalar um starter externo de erros só para isso. O Spring oferece esse ponto de extensão para as exceções MVC ([documentação](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)).

Não basta ligar uma propriedade e assumir compatibilidade: o advice atual tem precedência alta, e erros nos filtros de autenticação/tenant são outro caminho.

Critérios: 400, 401, 403, 404, 405, 409 e 415 corretos; preservar Allow quando aplicável, application/problem+json, correlação e ausência de detalhes internos. Erro inesperado continua 500. Sem mudança silenciosa do contrato do frontend.

### A02 — P1 — Imagens: reutilizar decodificação segura e limitada, não apenas assinatura

Evidências: [CompanyLogoController.java:35](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/platform/company/api/CompanyLogoController.java:35), [EmployeeController.java:168](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/employees/api/EmployeeController.java:168), [EmployeeController.java:333](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/employees/api/EmployeeController.java:333) e [MeasurementCompanyBrandingImage.java:30](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/infrastructure/branding/MeasurementCompanyBrandingImage.java:30).

Há três estratégias diferentes: logo confere assinatura; foto usa ImageIO.read e só depois verifica dimensões; branding usa ImageReader para consultar dimensões antes de ler pixels. Nos dois uploads, getBytes ocorre antes da checagem local de tamanho.

Recomendação: reutilizar o padrão ImageReader com limite antes da decodificação, validar tamanho antes da cópia e manter limites multipart. Centralizar somente a mecânica comum, preservando tipos e limites específicos de cada finalidade. Isso pode ser feito inicialmente com o JDK já existente.

WEBP: o build não declara codec dedicado; branding possui fallback explícito quando não há reader. Se a exigência passar a ser sempre renderizar WEBP, avaliar um plugin ImageIO, como TwelveMonkeys, com prova de compatibilidade e arquivos reais de teste. O projeto oferece extensões de ImageIO e suporte de leitura WEBP ([fonte](https://github.com/haraldk/TwelveMonkeys)).

Não foi demonstrada exploração nesta auditoria. Assinatura/MIME não equivalem a imagem completamente válida nem a antimalware. Testar imagem truncada, conteúdo falso, dimensões enormes, limites de upload, PNG/JPEG/WEBP, fallback, privacidade e ausência de URL pública.

### A03 — P2 — Hexadecimal manual: substituir pelo Java 21

Evidências:

- [Sha256RefreshTokenHasher.java:24](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/identity/infrastructure/security/Sha256RefreshTokenHasher.java:24)
- [Sha256PasswordResetTokenHasher.java:24](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/identity/infrastructure/security/Sha256PasswordResetTokenHasher.java:24)
- [Sha256ActivationTokenHasher.java:24](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/identity/infrastructure/security/Sha256ActivationTokenHasher.java:24)
- [MeasurementSpreadsheetImportService.java:398](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/application/MeasurementSpreadsheetImportService.java:398)

Os quatro trechos montam hexadecimal de SHA-256 em loop com String.format. Outros pontos do projeto já usam HexFormat.

Recomendação clara: `HexFormat.of().formatHex(bytes)`, que já existe no JDK, com saída minúscula padrão ([Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html)). Não adicionar Commons Codec apenas para essa conversão.

Risco baixo, mas preservar exatamente SHA-256, codificação UTF-8, 64 caracteres e zeros iniciais. Testar igualdade byte a byte da representação antiga/nova e compatibilidade de tokens/hash de importação existentes. Não trocar hash de senha por SHA-256: são usos diferentes.

### A04 — P2 — CPF: biblioteca possível, validação condicional continua necessária

Evidência: [Employee.java:171](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/employees/domain/Employee.java:171) e teste [src/test/java/com/renovar/canteiro/io/employees/domain/EmployeeTest.java:15](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/test/java/com/renovar/canteiro/io/employees/domain/EmployeeTest.java:15).

O algoritmo dos dígitos verificadores foi escrito no domínio. Hibernate Validator oferece a anotação pública `@CPF` ([documentação](https://docs.hibernate.org/validator/9.1/api/org/hibernate/validator/constraints/br/CPF.html)); o starter de validação já está no build.

A recomendação anterior de usar @CPF é viável, mas não é uma substituição direta: o documento pode ser estrangeiro e a regra é condicional ao tipo CPF. Aprovações e chamadas internas também precisam continuar protegidas; só anotar o controller não basta.

Decisão: realizar comparação de comportamento e escolher conscientemente entre:
- manter a pequena função pura de domínio, com testes mais completos; ou
- delegar o algoritmo à API pública de validação em um desenho que também cubra domínio/aplicação.

Não usar classes internas do Hibernate Validator no domínio. Não aplicar CPF a todo documento de cliente/fornecedor. CPF matematicamente válido não comprova titularidade, existência ou regularidade cadastral.

Testar máscara/normalização, dígitos errados, sequências repetidas, zeros à esquerda, tamanhos inválidos, ausência de documento, documentos estrangeiros, atualização e aprovação. PIS hoje possui validação de formato de 11 dígitos em normalizePis; esta auditoria não o considera verificado por checksum nem autoriza nova regra.

### A05 — P2 — Mapeamento mecânico: MapStruct pode reduzir manutenção

Evidência representativa: [CompanyApiMapper.java:14](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/platform/company/api/CompanyApiMapper.java:14). Existem 37 arquivos com sufixo Mapper.

MapStruct gera mapeadores em compilação, sem depender de reflexão para cada conversão ([guia](https://mapstruct.org/documentation/stable/reference/html/)). Pode ajudar no transporte repetitivo de campos entre DTO/command/projeção.

Decisão: prova em um mapper simples antes de adoção ampla. Validar Java 21, Spring Boot 4, records, Lombok e annotation processors da versão escolhida. Usar listas explícitas de campos e falhar compilação quando um destino relevante ficar sem mapeamento.

Não aprovar automaticamente os 37 mappers. Não gerar merges indiscriminados de entidade JPA, não sobrescrever tenant, IDs, versões, origem, permissões, salário ou campos omitidos. Projeção segura de auditoria não deve virar cópia automática de payload.

Critérios: JSON/OpenAPI sem diferença, campos sensíveis ausentes, null/update preservados, histórico e campos imutáveis intactos. Não recomendo adicionar um mapper por reflexão para substituir toda a camada de uma vez.

### A06 — P2 — Importação XLSX: há API de eventos no próprio POI

Evidências: [MeasurementSpreadsheetImportParser.java:33](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/application/MeasurementSpreadsheetImportParser.java:33) e [MeasurementSpreadsheetImportParser.java:52](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/application/MeasurementSpreadsheetImportParser.java:52).

A importação usa byte[] e WorkbookFactory; o limite de 10.000 linhas é verificado depois de abrir a planilha. Portanto, esse parser não tem a mesma estratégia de memória da exportação SXSSF.

Se houver pressão de memória/volumes maiores, avaliar XSSFReader/event model do POI, sem trocar de biblioteca. A documentação distingue o modelo completo da leitura por eventos e da escrita SXSSF ([POI](https://poi.apache.org/components/spreadsheet/)).

Não afirmar que limite de linhas elimina custo de descompressão de XLSX. Definir também limites de tamanho comprimido/descomprimido, estrutura e quantidade de células. Importação transacional ainda pode precisar guardar um plano limitado de diferenças: ler por SAX não torna todo o fluxo de memória constante automaticamente.

Preservar IDs estáveis, moeda, metadados, linhas agrupadoras, preview/confirm/hash, ordem, inativação lógica, 202 sem efeito oficial e recálculo no servidor. Manter leitura decimal exata; não converter preço via double.

### A07 — P2 — Cifra de dados bancários: primitiva já pronta; gestão é a oportunidade

Evidência: [EmployeePaymentDataCipher.java:39](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/employees/infrastructure/security/EmployeePaymentDataCipher.java:39).

AES-GCM é executado por Cipher/JCA, com IV aleatório de 12 bytes e tag de 128 bits. O código próprio monta o envelope IV+ciphertext e deriva a chave do segredo. Não há updateAAD nem identificador de versão/chave no envelope observado.

Decisão: manter a implementação atual até desenho específico de gestão/rotação. Avaliar Tink AEAD e/ou SDK do KMS escolhido quando houver requisitos definidos. Tink fornece primitivas AEAD e associação autenticada de contexto ([documentação](https://developers.google.com/tink/aead)); não resolve sozinho provisionamento, backup, permissão ou rotação operacional.

Não é troca cosmética nem declaração de vulnerabilidade comprovada. Exige inventário de campos cifrados, modelo de ameaça, segredo de alta entropia, formato versionado, compatibilidade com legado, recuperação, rotação e testes de adulteração. Trocar o formato sem migração pode tornar dados existentes ilegíveis. Nenhuma migration ou recifragem autorizada por este relatório.

### A08 — P3 — E-mails HTML: template quando o custo de manutenção justificar

Evidências: [SmtpAccountActivationEmailSender.java:30](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/identity/infrastructure/email/SmtpAccountActivationEmailSender.java:30) e [SmtpPasswordResetEmailSender.java:31](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/identity/infrastructure/email/SmtpPasswordResetEmailSender.java:31).

SMTP/MIME já são de biblioteca. O trabalho manual é o HTML concatenado de dois e-mails.

Thymeleaf pode separar layout/texto e tornar evolução mais simples ([documentação](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html)). Não é obrigatório adicionar motor de template só para dois conteúdos curtos. Antes da escolha, confirmar integração compatível com o Spring atual; não copiar configuração de versão antiga.

Preservar alternativa em texto, botão, escape de valores, URL de frontend configurada e token no fragmento. Não colocar token em logs, query de analytics, auditoria ou resposta pública. Montagem de URL pode aproveitar URI builders existentes; não foi demonstrada falha de escape apenas pela concatenação observada.

### A09 — P2 — Projeções SQL: reduzir casts frágeis, não trocar ORM

Evidências: [JpaReceivableRepository.java:49](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/receivables/infrastructure/persistence/JpaReceivableRepository.java:49) e [JpaReceivableRepository.java:124](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/receivables/infrastructure/persistence/JpaReceivableRepository.java:124). Há 14 arquivos com createNativeQuery.

Parte das consultas faz projeção com Object[] e índices/casts manuais. É candidato a result mapping tipado do JPA ou projeções Spring Data quando a consulta permitir. O projeto já usa JpaSpecificationExecutor em consultas convencionais, por exemplo [FinalCustomerJpaRepository.java:11](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/customers/infrastructure/persistence/FinalCustomerJpaRepository.java:11).

Não recomendo adicionar QueryDSL/jOOQ ou substituir Hibernate só porque existe SQL nativo. CTEs financeiras e queries de reconhecimento possuem justificativa própria.

A consulta representativa usa parâmetros e whitelist de sort; SQL em texto não é, por si só, evidência de SQL injection. Qualquer migração deve preservar count, paginação estável, moeda, UTC, tenant e ausência de duplicidade financeira. Comparar resultados com PostgreSQL/Testcontainers e plano de execução, não apenas compilar.

### A10 — P3 — Storage: SDK oficial no adapter futuro, não um cliente S3 próprio

Evidências: [StorageProvider.java:3](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/infrastructure/storage/StorageProvider.java:3) e [LocalStorageProvider.java:19](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/infrastructure/storage/LocalStorageProvider.java:19).

O adapter local já usa NIO; não há necessidade de biblioteca para executar Files.write/readAllBytes. A porta atual recebe/devolve byte[]: para arquivos maiores, pode evoluir para streams/Resource, com tamanho e fechamento explícitos.

Quando object storage for autorizado, usar SDK oficial do provedor atrás da porta. Não implementar assinatura HTTP, multipart ou criptografia de transporte do provedor manualmente.

Arquivos pequenos limitados e logos não são o mesmo caso de um relatório volumoso. Não trocar todo byte[] sem medir necessidade. Preservar caminhos privados, hash, autorização no download e comportamento de falha. Consistência entre transação do banco e storage não é garantida só por instalar um SDK.

### A11 — P3 — Resiliência externa: biblioteca opcional, idempotência permanece domínio

Evidência: [AsaasSandboxConfiguration.java:17](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/platform/subscription/infrastructure/asaas/AsaasSandboxConfiguration.java:17).

A integração já usa RestClient. Esta configuração não declara timeouts explicitamente; os limites efetivos de runtime não foram medidos e podem depender de customização/defaults.

Primeiro: definir timeouts e classificação de falhas com o cliente existente. Depois, se necessário, Resilience4j pode fornecer retry/backoff/circuit breaker ([retry](https://resilience4j.readme.io/docs/retry)).

Nunca aplicar retry genérico a criação de cobrança, baixa, NF ou aprovação. Preservar chaves de idempotência, payload conflitante, eventos ordenados, reconciliação e estado persistido de entrega. Não transformar rejeição de negócio em falha transitória. Testar timeout depois do envio, duplicidade e resposta tardia sem duplicar dinheiro.

### A12 — P3 — Jobs: Spring já agenda; lock distribuído depende da topologia

Evidências: [SubscriptionDunningScheduler.java:16](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/platform/subscription/infrastructure/SubscriptionDunningScheduler.java:16) e [JpaPlatformChargeNoticeRepository.java:49](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/platform/subscription/infrastructure/persistence/JpaPlatformChargeNoticeRepository.java:49).

Não há motivo para trocar @Scheduled por um scheduler desenvolvido do zero. Há claim/estado persistido de entregas no repositório.

Se houver várias réplicas, avaliar se os locks/claims existentes cobrem todas as rotinas. ShedLock é uma opção de exclusão de execução entre nós ([projeto](https://github.com/lukas-krecan/ShedLock)); não é uma garantia universal de “exactly once” e não substitui idempotência/transação por cobrança. Não adicioná-lo sem verificar a necessidade real.

### A13 — P2 — Ferramentas de arquitetura e dependências agregam mais que várias trocas

Evidências: [build.gradle.kts:121](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/build.gradle.kts:121), [config/checkstyle/checkstyle.xml:9](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/config/checkstyle/checkstyle.xml:9) e [.github/workflows/ci.yml:1](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/.github/workflows/ci.yml:1).

O CI executa check e JaCoCo; o piso configurado é 15% de instruções, não a cobertura efetivamente medida. Checkstyle tem poucas regras. Não há job explícito de SBOM/scan de dependências no workflow examinado.

Avaliar:
- ArchUnit para dependências entre módulos/camadas e ciclos ([guia](https://www.archunit.org/userguide/html/000_Index.html)).
- CycloneDX para SBOM das dependências resolvidas ([plugin Gradle](https://github.com/CycloneDX/cyclonedx-gradle-plugin)).
- Dependency-Check ou scanner equivalente para CVEs, com triagem e atualização controlada ([plugin](https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/)).

Nenhum scanner foi executado aqui e nenhuma versão foi declarada vulnerável sem evidência. SBOM não é parecer de licença nem comprovação de segurança.

Observação de operação, não de biblioteca: pushes disparam CI apenas para main e feature/** no workflow atual. A branch fase-5-faturamento não corresponde a esses padrões; PR para main está coberto. Ajustar só mediante tarefa própria, sem afirmar que todo push atual já recebe os gates.

### A14 — P3 — Segurança futura: aproveitar mecanismos maduros, sem confundir com implementação atual

Evidência: dependências e buscas no código de produção; não foram encontrados componentes dedicados de rate limiting/MFA pelos padrões pesquisados.

Na implementação das tarefas de segurança existentes, considerar biblioteca como Bucket4j para limites de requisições ([projeto](https://github.com/bucket4j/bucket4j)) e mecanismo maduro para MFA. Não escrever protocolo TOTP/criptografia do zero.

Isso não prova ausência de proteção em proxy/infra, que não foi auditada em execução. Também não significa que basta instalar uma biblioteca: definir limites por rota/conta/IP confiável, comportamento distribuído, prevenção de lockout abusivo, recuperação e testes.

RLS é política PostgreSQL, não uma dependência Java que instala isolamento automaticamente. MFA, RLS, proteção de documentos e gestão de chaves continuam tarefas próprias da fase de segurança. Não foram implementadas por esta auditoria.

### A15 — Manter — PDF/XLSX já usam motores adequados

Evidências: [ApachePoiMeasurementSpreadsheetGenerator.java:44](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/infrastructure/spreadsheet/ApachePoiMeasurementSpreadsheetGenerator.java:44), [StreamingMeasurementAccumulatedReportGenerator.java:58](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/infrastructure/reporting/StreamingMeasurementAccumulatedReportGenerator.java:58) e [GenerateMeasurementAccumulatedReportService.java:146](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/application/GenerateMeasurementAccumulatedReportService.java:146).

Há SXSSFWorkbook com janela de 100 linhas, cache PDFBox em arquivo temporário e consultas por lotes de 250 nos caminhos inspecionados. O gerador PDF por versão delega ao gerador compartilhado.

Desenhar cabeçalho, logo privada, quebra de texto, grupos, subtotais e origem da linha é código de apresentação sobre a biblioteca; não é escrever um motor PDF/XLSX próprio.

Não recomendo JasperReports/EasyExcel apenas para eliminar esse layout manual. A adoção exigiria prova de streaming, memória, precisão, licenciamento, importabilidade, fidelidade de prévia e preservação histórica. Estas evidências não constituem benchmark nem prova de memória constante de todo o pipeline.

### A16 — Manter — QR usa ZXing; BR Code/CRC merece testes de conformidade

Evidência: [PixStaticQrCodeGenerator.java:32](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/employees/application/PixStaticQrCodeGenerator.java:32).

ZXing já transforma payload em imagem ([projeto](https://github.com/zxing/zxing)). O código próprio monta campos BR Code e CRC16.

Não recomendo adicionar biblioteca Pix sem avaliar procedência, especificação suportada e comportamento estático sem valor. Um pequeno CRC padronizado com fixtures independentes pode ser mais simples. CRC32 do Java não substitui CRC16.

Manter dados confirmados do titular, ausência de valor/vencimento/identificador financeiro, geração efêmera e auditoria sem chave/payload/imagem. Fixtures devem validar payload completo e CRC por implementação independente; esta auditoria não certificou interoperabilidade bancária.

## 6. O que deve continuar explícito no nosso código

“Não substituir” significa não delegar a decisão de negócio a um padrão genérico; bibliotecas continuam oferecendo operações básicas.

| Regra própria | Por que uma biblioteca genérica não a define |
| --- | --- |
| Tenant derivado da sessão e cadeia Company → cliente → obra → contrato | Precisa validar cada consulta, operação, exportação e referência |
| Alçada direta = direta OU solicitação + aprovação | Semântica específica; nem Spring Security nem um role genérico escolhem a política |
| 202 sem alterar/reservar dado oficial | Precisa ser garantido em cada caso de uso e aprovação |
| Proibição de autoaprovação e limites de suporte | Workflow genérico não implica essas restrições |
| Descontos, capacidades e resíduos determinísticos | Exigem regras financeiras, versão e testes de invariantes |
| Composição incremental aceita, reconhecimento por raiz e reversões | Não é simples soma de versões |
| Faturamento canônico por BillingAllocation | Impede dupla contagem entre parcela/NF/recebível |
| Origem em composição, NF/item e baixa parcial | Identidade financeira não pode ser inferida por nome ou biblioteca |
| Soft delete e auditoria imutável | ORM/Envers não substituem evento de negócio seguro e aprovado |
| Dados seguros por permissão e reveal explícito | Mapper automático/serializer não decide o que pode vazar |
| Fórmulas UNIT/m²/metro/kg e unitQuantity informativa | Biblioteca matemática executa operações, não escolhe a fórmula |
| Nomes seguros de arquivos e filtros permitidos | São políticas do contrato e das plataformas atendidas |

Evidências representativas:
- [AccessAuthorizationService.java:1](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/access/application/AccessAuthorizationService.java:1)
- [ProportionalMoneyAllocation.java:14](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/domain/ProportionalMoneyAllocation.java:14)
- [CanonicalMoneySerializer.java:13](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/api/json/CanonicalMoneySerializer.java:13)
- [StrictDecimalStringDeserializer.java:11](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/api/json/StrictDecimalStringDeserializer.java:11)
- [PageQuery.java:19](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/api/pagination/PageQuery.java:19)
- [FilterQueryParser.java:14](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/api/filter/FilterQueryParser.java:14)
- [MeasurementExportFilename.java:9](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/measurements/application/MeasurementExportFilename.java:9)
- [TenantContextAuthenticationFilter.java:29](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/tenancy/infrastructure/TenantContextAuthenticationFilter.java:29)

Jackson já é utilizado: os serializers pequenos adaptam a regra “dinheiro em string canônica, sem arredondamento implícito”. Não os remover em troca de serialização numérica padrão. BigDecimal atende às operações atuais; uma biblioteca monetária não resolve origem, desconto, moeda imutável ou ledger.

PageQuery já constrói PageRequest/Sort. A whitelist de campos e limite de página são necessários: não trocar por Pageable irrestrito e perder proteção/contrato.

A correlação já usa MDC e limpeza no finally em [CorrelationIdFilter.java:34](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/shared/infrastructure/web/CorrelationIdFilter.java:34). Não há justificativa para criar outra infraestrutura de logs apenas para repetir isso.

## 7. Validação de anexos: reuso correto não elimina guardrails

Evidência: [InvoiceAttachmentContentValidator.java:17](C:/Users/oqdel/OneDrive/Desktop/canteiro.io/src/main/java/com/renovar/canteiro/io/receivables/application/InvoiceAttachmentContentValidator.java:17).

PDF é lido por PDFBox; XML usa SAX/JDK com bloqueio de DTD/entidades externas/XInclude e limites estruturais. Logo, não são parsers PDF/XML caseiros.

Uma futura biblioteca de detecção de conteúdo pode complementar o reconhecimento, mas não substituir:
- validação de estrutura/conteúdo e limites;
- parser XML endurecido;
- autorização/tenant e storage privado;
- antimalware quando implementado;
- regras fiscais/assinaturas que não pertencem a esse validador.

Não relaxar as configurações de segurança em nome de reduzir linhas. Detectar MIME não significa validar uma NF fiscalmente.

## 8. Plano de adoção sugerido — não executado

| Etapa | Proposta | Dependência nova | Gate antes de concluir |
| --- | --- | --- | --- |
| 1 | Erros MVC e imagens limitadas (A01/A02) | Não, inicialmente | Testes negativos e contrato sem vazamento |
| 2 | HexFormat (A03) | Não | Igualdade dos hashes legados |
| 3 | CPF condicional e um mapper piloto (A04/A05) | CPF via stack atual; MapStruct só se escolhido | Todos os caminhos, isolamento e paridade de JSON |
| 4 | Gates de arquitetura/SBOM/CVE (A13) | Apenas ferramentas selecionadas | CI reproduzível; triagem, sem exceções amplas |
| 5 | Importação por eventos/projeções tipadas (A06/A09) | Não, em princípio | Benchmark limitado + regressões PostgreSQL/XLSX |
| 6 | Chaves/storage/resiliência/jobs (A07/A10/A11/A12) | Condicional | Requisitos e autorização operacional/migração |
| 7 | Templates/codec/MFA/rate limit conforme fase (A02/A08/A14) | Condicional | Prova de compatibilidade e política definida |

Cada troca deve ser pequena, comparada ao comportamento anterior e testada antes da próxima. Não agrupar todas em um PR gigante.

Antes de qualquer dependência: confirmar manutenção, licença da versão e transitivas, compatibilidade com Java/Spring/Jackson atuais, escopo mínimo do artefato, CVEs aplicáveis, custo de operação e saída/rollback. Documentação consultada não substitui essa validação no build real.

Antes de qualquer alteração financeira: preservar a contenção vigente de recebimentos e as decisões pendentes de vínculo manual NF/item. Biblioteca de dinheiro, mapper ou retry não autoriza remover bloqueio nem escolher automaticamente distribuição. Esse é um trabalho de domínio separado.

## 9. Registro de entrega

- Relatório produzido a partir do estado de trabalho desta data.
- Recomendações têm evidências e critérios de verificação; nenhuma foi marcada como implementada.
- Sem mudanças de contrato/API: não é necessário regenerar OpenAPI ou pedir alteração ao frontend para esta entrega.
- Sem atualização de status de implementação no backlog/SDD.
- Sem testes de aplicação executados, pois não houve alteração de comportamento.
- Fontes externas usadas são documentação dos próprios projetos, vinculadas nas seções correspondentes.
