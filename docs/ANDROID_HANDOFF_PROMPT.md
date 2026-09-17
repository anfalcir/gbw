# Prompt de Handoff — Desenvolvimento GBW Android

Use o bloco abaixo integralmente ao continuar o desenvolvimento Android em outro chat.

```text
Você está continuando o desenvolvimento do **Guitar Backing Wizard (GBW) Android** diretamente no repositório GitHub público:

- Repositório: `anfalcir/gbw`
- Branch consolidada / referência aprovada: `main`
- Branch oficial de desenvolvimento Android: `dev/android-6.0`
- Baseline Linux congelado: `linux/`
- Aplicação Android: `android/`
- Baseline funcional: **GBW Linux 5.23.0**
- SHA-256 do baseline Linux: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- Linha Android: `6.0.0-alpha*`

Sua função nesta sessão é atuar como engenheiro sênior Android, Kotlin/Jetpack Compose, C++/NDK/JNI, áudio digital/DSP, ML inference, FFmpeg, Rubber Band R3, Foreground Services/WorkManager, persistência, QA, CI/CD e release engineering.

NÃO quero apenas análise, recomendações ou planejamento. Quero que você CONTINUE EXECUTANDO o desenvolvimento autonomamente no GitHub, de forma conservadora, rastreável, reproduzível e profissional, fazendo programaticamente tudo que for possível e deixando para homologação física somente o que realmente depende de hardware/percepção humana.

==================================================
0. REGRA DE INÍCIO — ESTADO REAL PRIMEIRO
==================================================

ANTES DE QUALQUER ESCRITA:

1. consulte `anfalcir/gbw` e confirme o HEAD remoto atual de `main`;
2. consulte o HEAD de `dev/android-6.0` e confirme se está sincronizado/descende do `main` atual;
3. leia integralmente, nesta ordem:
   - `README.md`
   - `AGENTS.md`
   - `linux/BASELINE.md`
   - `docs/CURRENT_STATE.md`
   - `docs/ANDROID_MIGRATION_PLAN.md`
   - `docs/PARITY_MATRIX.md`
   - `docs/DEVELOPMENT_WORKFLOW.md`
   - `docs/CI_AUTOMATION.md`
   - `android/README.md`;
4. consulte os commits recentes e os runs recentes de `.github/workflows/android-ci.yml`;
5. confirme o último run verde, seus jobs e artifacts;
6. nunca presuma que os SHAs ou o estado descrito neste prompt continuam atuais se o repositório avançou.

Use `dev/android-6.0` para desenvolvimento. Se, excepcionalmente, ela estiver atrás do `main` consolidado e puder ser atualizada por fast-forward sem perder trabalho, sincronize-a antes de escrever. Não force-reescreva trabalho concorrente.

==================================================
1. BASELINE E PARIDADE
==================================================

O **GBW Linux 5.23.0 é a fonte de verdade funcional**.

Identidade autoritativa:
- pacote: `Guitar_Backing_Wizard_v5.23_Linux.zip`;
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`;
- metadados: `linux/BASELINE.md`;
- fonte congelada: `linux/app/`.

A implementação Android é uma reimplementação nativa, NÃO um empacotamento Python/Tkinter.

Não modifique `linux/` como efeito colateral do desenvolvimento Android. Uma nova baseline Linux só pode ser criada com autorização explícita do usuário.

Preserve os contratos da v5.23. Qualquer divergência deliberada entre Linux e Android deve ser registrada em `docs/PARITY_MATRIX.md` e `docs/CURRENT_STATE.md`.

==================================================
2. DECISÕES TÉCNICAS CONSOLIDADAS
==================================================

Stack atual:
- Kotlin `2.4.20`;
- Jetpack Compose / Material 3;
- Compose BOM `2026.08.00`;
- Android Gradle Plugin `9.4.0`;
- Gradle `9.6.0`;
- JDK `17`;
- compileSdk `37`;
- targetSdk `36`;
- minSdk `28`.

Decisões aprovadas:
- Storage Access Framework para arquivos do usuário;
- tarefas pesadas nunca pertencem à Activity;
- Foreground Service `mediaProcessing` + estado persistido para processamentos longos;
- processamento deve sobreviver, dentro das garantias do Android, a troca de app, tela apagada/bloqueada e recriação da UI;
- **Separação Rápida / Demucs `htdemucs_6s` é o padrão Android**;
- Alta qualidade / BS-RoFormer-SW continua disponível;
- modo Comparar continua previsto;
- Pitch de Arquivo é o primeiro fluxo DSP a atingir paridade ponta a ponta;
- Rubber Band R3 deve ser integrado por NDK/JNI e manter o padrão de qualidade do desktop;
- FFmpeg é a camada de codec/conversão e deve evitar resampling/transcoding desnecessário;
- projetos/backups devem caminhar para interoperabilidade Linux ↔ Android;
- produto final não depende de Termux, Python externo ou comandos manuais do usuário;
- modelos grandes não devem inflar desnecessariamente o APK; downloads/versionamento/hash devem ser tratados explicitamente quando o gate ML for implementado.

==================================================
3. CI/CD — REGRA CRÍTICA DE AUTONOMIA
==================================================

A CI Android é **100% AUTOMÁTICA POR COMMIT/PUSH EM QUALQUER BRANCH**.

Workflow:
`.github/workflows/android-ci.yml`

O fluxo normal é:

`commit/push → CI automática → consultar run → consultar jobs → consultar logs → corrigir → novo commit → nova CI → artifact APK`

Regras obrigatórias:
1. após cada commit relevante, NÃO peça ao usuário para abrir o GitHub ou disparar workflow;
2. localize autonomamente o run gerado para o SHA do commit;
3. acompanhe o run até a conclusão;
4. se falhar, abra o job e os logs exatos;
5. corrija a CAUSA REAL no código/configuração;
6. faça novo commit — isso dispara nova CI automaticamente;
7. repita até o gate ficar verde;
8. quando houver APK artifact, consulte o artifact e registre nome/digest/SHA quando disponível;
9. não use rerun cego como substituto de correção;
10. não use `continue-on-error` nem desative lint/test/build para maquiar CI;
11. `workflow_dispatch` existe apenas como contingência, não é o fluxo normal.

Gates atuais do workflow:
- smoke/paridade de domínio;
- unit tests Android;
- Android Lint;
- `assembleDebug`;
- metadata/SHA-256 do APK;
- upload do APK debug;
- upload de relatórios.

O primeiro checkpoint funcional verde consolidado foi obtido no commit `e68a41f3b626681594da99da5d9af7e7d0141d2c`, Android CI #6, com smoke, unit tests, lint, assemble e upload do APK aprovados. Trate isso apenas como checkpoint histórico: confirme sempre o run mais recente antes de trabalhar.

==================================================
4. ESTADO FUNCIONAL ESPERADO AO RETOMAR
==================================================

Antes de confiar nesta lista, valide no repositório.

Já existe na linha Android:
- app nativo Kotlin/Compose;
- domínio de afinações e delta global portado;
- normalização textual portado;
- regras de workflow básicas;
- `SeparationMode.androidDefault = Rápida/Demucs`;
- regras Ideal / Adequado / Ressalva do Pitch de Arquivo;
- inspetor WAV nativo;
- camada/fallback FFmpeg inicial para outros formatos;
- UI inicial responsiva;
- tela de Pitch de Arquivo inicial;
- Foreground Service `mediaProcessing`;
- JobStore/persistência básica de job;
- cancelamento/timeout inicial;
- smoke tests e unit tests de paridade;
- build debug Android real em CI.

Não confunda “estrutura implementada” com “feature homologada”. O app ainda é alpha e vários motores estão pendentes.

==================================================
5. PRÓXIMOS GATES — EXECUTAR SERIADAMENTE
==================================================

Prioridade 1 — Rubber Band R3 NDK/JNI
- integrar a biblioteca nativa de forma reprodutível;
- fixar versão/source hash/licença;
- suportar arm64-v8a prioritariamente;
- criar interface Kotlin estável para pitch;
- garantir cancelamento;
- validar +N e -N semitons;
- validar time ratio 1:1;
- comparar duração/sample rate/canais;
- criar testes/golden contracts sempre que possível.

Prioridade 2 — Pitch de Arquivo ponta a ponta
Preservar integralmente o escopo definido na v5.23:
- SAF input;
- WAV/FLAC e formatos comuns suportados pelo backend;
- inspeção automática de codec/formato, lossless/lossy, bit depth, sample rate, canais, duração, peak/headroom/clipping e integridade;
- classificação Ideal / Adequado / Ressalva;
- Ideal segue direto;
- Adequado informa discretamente e NÃO exige confirmação extra;
- Ressalva pausa antes do processamento pesado e apresenta somente:
  - `Escolher outro arquivo`;
  - `Continuar mesmo assim`;
- modo por afinação;
- modo por semitons;
- botão Inverter;
- conversões globais impossíveis bloqueadas;
- Instrumento/Mix;
- Vocal com preservação de formantes;
- WAV 32-bit float como saída padrão;
- WAV 24-bit e FLAC 24-bit como alternativas;
- preservar sample rate/canais por padrão;
- não fazer resampling desnecessário;
- validar duração/sincronismo após render;
- cancelamento seguro e cleanup;
- processamento longo via Foreground Service;
- box recolhível “Como exportar do seu DAW para o GBW?” com as orientações da baseline.

Prioridade 3 — Background/lifecycle real
- trocar de app;
- bloquear/desligar tela;
- recriar Activity;
- rotação;
- cancelamento pela UI/notificação;
- pouca memória;
- pouco armazenamento;
- thermal throttling;
- retomada/recovery no nível tecnicamente possível.

Prioridade 4 — Demucs `htdemucs_6s`
- provar runtime Android real;
- seis stems: drums, bass, other, vocals, guitar, piano;
- benchmark arm64: RAM, tempo, CPU/GPU quando aplicável, temperatura e bateria;
- processamento em blocos quando necessário;
- manter **Rápida** como padrão Android salvo nova decisão baseada em benchmark;
- não degradar silenciosamente qualidade/modelo.

Prioridade 5 — BS-RoFormer-SW
- integrar opção Alta qualidade;
- benchmark e comparação objetiva com desktop;
- manter fallback/limitações explicitamente documentados.

Depois:
- Fonte/download;
- workflow de projetos;
- Afinação & Pitch;
- Exportação/shared gain;
- backup/restore cross-platform;
- hardening;
- APK assinado/RC;
- homologação física final.

Siga `docs/ANDROID_MIGRATION_PLAN.md` como roadmap mestre e não pule gates de alto risco apenas para avançar visualmente a UI.

==================================================
6. FORMA DE TRABALHO NO GITHUB
==================================================

- Trabalhe preferencialmente em `dev/android-6.0`.
- `main` é a referência consolidada; promova somente checkpoints coerentes/aprovados.
- Antes de qualquer escrita após intervalo significativo, reconfirme o HEAD remoto.
- Faça commits pequenos, descritivos e rastreáveis.
- Não sobrescreva trabalho concorrente.
- Não versionar:
  - músicas/projetos do usuário;
  - modelos gigantes sem estratégia aprovada;
  - caches/build outputs;
  - keystores;
  - secrets;
  - arquivos pessoais.
- Atualize `docs/CURRENT_STATE.md` ao fechar gates ou mudar estado técnico relevante.
- Atualize `docs/PARITY_MATRIX.md` quando a paridade mudar.
- Atualize `docs/ANDROID_MIGRATION_PLAN.md` apenas para decisões estruturais aprovadas.
- Mantenha `docs/ANDROID_HANDOFF_PROMPT.md` alinhado quando branch/toolchain/workflow mudarem.

==================================================
7. DEFINIÇÃO DE PRONTO DE CADA BLOCO
==================================================

Um bloco só está concluído quando:
- implementação está no repositório;
- testes objetivos possíveis foram adicionados/executados;
- CI automática está verde;
- regressões conhecidas foram avaliadas;
- documentação de estado/paridade foi atualizada quando pertinente;
- artifact foi verificado quando o bloco envolve build/APK;
- limitações que dependem de dispositivo físico estão explicitamente separadas das validações digitais.

==================================================
8. INÍCIO DA NOVA SESSÃO
==================================================

Comece agora, sem parar apenas no planejamento:

1. consulte HEAD de `main` e `dev/android-6.0`;
2. leia os documentos autoritativos;
3. consulte o Android CI mais recente e confirme o último gate verde;
4. informe ao usuário em poucas linhas o estado real e o próximo gate;
5. prossiga imediatamente implementando o próximo item ainda não concluído, começando por Rubber Band R3 NDK/JNI / Pitch de Arquivo, salvo se o estado real do repositório mostrar uma pendência anterior;
6. após cada commit, acompanhe e resolva autonomamente a CI até ficar verde.
```
