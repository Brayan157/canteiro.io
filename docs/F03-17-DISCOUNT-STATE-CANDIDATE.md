# F03-17 / FRONT-F06-14 — Candidato do indicador de desconto ativo

29/10/2026. **Testado, conferido pelo frontend e publicado somente em8081 em29/10/2026 às18:44:52SãoPaulo.** [Ativação e evidências](F03-17-DISCOUNT-STATE-LOCAL-ACTIVATION-2026-10-29.md). Plano anterior preservado abaixo.

[Contrato de integração](FRONTEND-CONTRACT-DISCOUNT-STATE.md): hasActiveContractDiscount boolean|null emContractResponse. GETdetalhe devolvebooleanoficial; outrasrespostasnull/nãoavaliado. ProteçãoCONTRACTS.READ/tenant preservada, sem valores financeiros novos ou permissão de reversão implícita. Consulta emtransação read-only REPEATABLE_READ, sem tocar no estado oficial/aprovação/auditoria.

Candidato `tmp/contract-discount-state-candidate/canteiro.io-contract-discount-state.jar`,SHA256AF20D2D23FE3DE2C32B4D96FEC7E4BC6CC0F38C4D4AEFE3A8019D5BA5787747C. Comparado aoativoCF376B05:2classesexistentesalteradas(ContractController/ContractResponse),2classesadicionadas(GetContractDetailsUseCase/Details),zero removidas/inesperadas. Bibliotecas/configuração/migrationsV1–V91 idênticos,sem mudança no relatório/PDFouledger.

Testes focados: contrato semdesconto, proposta202semativo, percentualativo combasezero, reversãopendente202mantendoativo, baseadoemvalorpositivo, reversãodireta deixandofalse, listagemnull; ajustemediçãopositivonoagregado comindicadorfalse;403semCONTRACTS.READ e404outrotenant. Regressãodeobras/contratos econtratoexecutávelOpenAPI. Primeirarodada encontrou fixturesincorretas:solicitaçãodedescontousavaREQUEST_CREATE masoperaçãoexistenteéUPDATE; cenáriodeoutrotenant usavaatorpertencenteàprópriaentidadeemvezdemanagerdaempresaoposta. Corrigidosapenasfixtures;nenhumrelaxamento deautorização/negócio.

Ativação local prevista somente após testes e conferência, semnova migration/seeds/operaçãofinanceirareal: verificarbaselineCF376B05/schema91checksum-1539369050,hashdocandidato,ambiente/volume/rede/porta/restart;preservarCF376B05compatívelpara retorno. Criar imagemcopiandosomenteJARvalidado,atualizarsomenteAPI8081;confirmarstartup/OpenAPI200 e401anônimo. Não criar/reverterdesconto real comoteste. Evidênciasemtmp/contract-discount-state-candidate.

Fechamento:4testes/2suítes aprovados em1m15s na rodadafinal,Checkstyle main/test ebootJar aprovados. Exportexecutável3.1.0/148paths/257schemas SHA25624BE6633792CEA5DBC5DCAA4322255111F20327965843B58212C56A2499D0625;sóContractResponse recebeu propriedadeerequired correspondentes,paths/HTTPdemais schemaspreservados.
