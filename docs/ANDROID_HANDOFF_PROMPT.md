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
- SHA-256 Linux: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- Linha Android: `6.0.0-alpha*`

Atue como engenheiro sênior Android/Kotlin/Compose, C++/NDK/JNI, áudio/DSP, FFmpeg, ML inference, Demucs, Foreground Services, persistência, QA, CI/CD e release engineering.

NÃO pare em análise ou planejamento. Continue executando diretamente no GitHub, com commits pequenos, rastreabilidade, testes objetivos e acompanhamento autônomo da CI. Deixe para homologação física apenas aquilo que realmente exige aparelho/percepção humana.

==================================================
0. ESTADO REAL PRIMEIRO
==================================================

ANTES DE ESCREVER:

1. confirme HEAD remoto de `main` e `dev/android-6.0`;
2. confirme que não há avanço concorrente relevante;
3. leia:
   - `README.md`
   - `AGENTS.md`
   - `linux/BASELINE.md`
   - `docs/CURRENT_STATE.md`
   - `docs/ANDROID_MIGRATION_PLAN.md`
   - `docs/PARITY_MATRIX.md`
   - `docs/DEVELOPMENT_WORKFLOW.md`
   - `docs/CI_AUTOMATION.md`
   - `docs/ANDROID_HANDOFF_PROMPT.md`
   - `android/README.md`
   - `android/app/src/main/cpp/THIRD_PARTY.md`;
4. consulte commits e Android CI recentes;
5. confirme último run verde, jobs e artifacts.

Nunca trate SHA deste handoff como autoritativo se o repositório tiver avançado.

==================================================
1. REGRAS INVIOLÁVEIS
==================================================

- GBW Linux 5.23.0 é a fonte de verdade funcional.
- Não modifique `linux/` como efeito colateral do Android.
- Android é reimplementação nativa; sem Python/Termux externo no produto final.
- Divergências Linux × Android devem ser registradas em `PARITY_MATRIX` e `CURRENT_STATE`.
- Storage Access Framework é a interface de arquivos do usuário.
- Tarefas pesadas não pertencem à Activity.
- Foreground Service `mediaProcessing` + estado persistido é o padrão para processamento longo.
- No Android existe **um único separador: Demucs `htdemucs_6s`**.
- Não usar no Android os conceitos **Rápida**, **Alta qualidade**, **Comparar** ou seleção/preferência de motor.
- BS-RoFormer/PTE/ExecuTorch/XNNPACK e toda infraestrutura exclusiva de múltiplos motores devem ser removidos no próximo APK; não manter código morto.
- O Linux 5.23 preserva suas opções históricas e não deve ser alterado por essa decisão Android.
- O modelo Demucs permanece fora do APK, com versão/revisão/hash/download/cache explícitos.

==================================================
2. TOOLCHAIN FIXADA
==================================================

- Kotlin `2.4.20`
- Compose BOM `2026.08.00`
- AGP `9.4.0`
- Gradle `9.6.0`
- JDK `17`
- compileSdk `37`
- targetSdk `36`
- minSdk `28`
- NDK `27.2.12479018`
- CMake `3.22.1`

Não altere versões por preferência.

==================================================
3. CI — AUTONOMIA OBRIGATÓRIA
==================================================

`.github/workflows/android-ci.yml` dispara automaticamente em push/PR.

Após TODO commit relevante:

commit/push
→ localizar run do SHA
→ consultar jobs
→ aguardar conclusão
→ se falhar, investigar o job/step exato
→ corrigir a causa real
→ novo commit
→ repetir até verde
→ conferir artifacts

Não use rerun cego, continue-on-error ou remoção de gates.

Gates esperados:

- domain parity smoke;
- Rubber Band R3 host golden smoke;
- checkpoint real Demucs: revisão/tamanho/SHA-256/`dmc6`/tensores;
- unit tests;
- Android Lint;
- assembleDebug NDK/CMake arm64;
- `libgbw_rubberband.so` + `libgbw_demucs.so` dentro do APK;
- metadata/SHA-256;
- APK e relatórios como artifacts.

==================================================
4. GATE CONCLUÍDO — RUBBER BAND R3 / PITCH DE ARQUIVO
==================================================

Confirme no repositório, mas o estado esperado inclui:

- Rubber Band `4.0.0` @ `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`;
- R3/Finer, ABI `arm64-v8a`;
- offline `study`/`process`;
- float32 em blocos, time ratio 1.0, +N/-N;
- formantes preservados para Vocal;
- cancelamento/cleanup;
- golden host estéreo +3/-3;
- SAF input → FFmpeg → R3 → validação → WAV32f/WAV24/FLAC24 → SAF final;
- preflight de espaço, Foreground Service, JobStore, wake lock e redelivery.

Rubber Band continua com gate de licença antes de RC/distribuição pública: GPL v2-or-later ou licença comercial apropriada.

==================================================
5. GATE CONCLUÍDO DIGITALMENTE — DEMUCS `htdemucs_6s`
==================================================

Estado esperado, sempre revalidando no repo:

Runtime:

- `demucs.cpp` @ `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen @ `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- C++17/NDK/JNI, `arm64-v8a`;
- `libgbw_demucs.so` dentro do APK;
- JNI rejeita 4-source e exige saída `[6,2,frames]` finita.

Checkpoint:

- `ggml-model-htdemucs-6s-f16.bin`;
- Hugging Face `Retrobear/demucs.cpp`;
- revisão `5f5daffffcf06ad7b27a7285da327e18ea62068a`;
- `54,855,129` bytes;
- SHA-256 `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- magic `dmc6`;
- fora do APK;
- `.part` → validação → promoção atômica para cache privado;
- CI valida o checkpoint externo real.

Áudio/chunking:

- preparação float32 estéreo 44,1 kHz;
- window 343.980 frames / 7,8 s;
- core 242.550 / 5,5 s;
- contexto 50.715 / 1,15 s por lado;
- stems fixos: drums, bass, other, vocals, guitar, piano;
- seis WAVs float32 estéreo validados por sample rate/canais/frames;
- cancelamento/cleanup;
- métricas `elapsedMillis` e pico PSS observado;
- UI de **Separação** ligada ao Foreground Service real; no estado-alvo Android não existe rótulo ou conceito de Rápida.

Ainda NÃO chame isso de homologação física. Exigem aparelho real:

- música real e avaliação auditiva dos seis stems;
- seams entre chunks;
- RAM/PSS, tempo, thermal throttling e bateria;
- Home/outro app/tela bloqueada;
- providers SAF reais.

==================================================
6. PRÓXIMO BLOCO OBRIGATÓRIO — CONSOLIDAÇÃO DEMUCS-ONLY
==================================================

Após concluir a homologação física do alpha8, o próximo APK deve simplificar estruturalmente a linha Android:

- renomear toda UX para simplesmente **Separação**;
- remover telas, cards, botões, textos e estados de Rápida / Alta qualidade / Comparar;
- remover BS-RoFormer, PTE, importação/manager, ExecuTorch/XNNPACK e PFFFT se não houver outro consumidor;
- remover actions/intents/branches do MediaProcessingService exclusivos do BS-RoFormer;
- remover tipos de job, persistência, preferências, recursos e caminhos de storage exclusivos de múltiplos motores;
- remover testes, scripts, workflows/gates, artifacts e metadata exclusivos do motor retirado;
- revisar Gradle/CMake/APK para garantir que dependências e bibliotecas nativas sem uso não permaneçam empacotadas;
- atualizar README, CURRENT_STATE, PARITY_MATRIX, THIRD_PARTY e plano de migração;
- preservar capacidade de ouvir/exportar os seis stems Demucs;
- não modificar `linux/`.

A limpeza só fecha quando uma busca global no Android/docs ativos não encontrar referências funcionais a:
`BS-RoFormer`, `Alta qualidade`, `Comparar`, `PTE`, `ExecuTorch` ou seleção de engine, exceto histórico explicitamente marcado como tal.

==================================================
7. DEPOIS DA CONSOLIDAÇÃO DEMUCS-ONLY
==================================================

- Fonte/download;
- workflow Separação integrado;
- Afinação & Pitch do workflow;
- Exportação/shared gain;
- Projetos;
- backup/restore cross-platform;
- hardening;
- auditoria final de licenças somente do que efetivamente é distribuído no Android;
- RC assinado;
- homologação física final;
- Android 6.0.0.

Siga `docs/ANDROID_MIGRATION_PLAN.md`; a decisão Demucs-only supersede qualquer trecho antigo que ainda descreva múltiplos motores.

==================================================
8. DEFINITION OF DONE
==================================================

Um bloco só fecha quando:

- código está no GitHub;
- testes objetivos possíveis foram implementados;
- CI automática está verde;
- artifact foi verificado quando aplicável;
- regressões foram avaliadas;
- CURRENT_STATE/PARITY/THIRD_PARTY foram atualizados quando pertinente;
- limitações físicas estão separadas das digitais.

Comece confirmando estado remoto real e prossiga imediatamente do próximo gate pendente.
```
