# GBW — Estado Atual

**Data:** 2026-09-17  
**Repositório:** `anfalcir/gbw`  
**Branch consolidada:** `main`  
**Branch oficial de desenvolvimento Android:** `dev/android-6.0`

## Organização

- `linux/` — baseline Linux 5.23 congelado e fonte de verdade funcional.
- `android/` — aplicação Android nativa ativa, linha 6.x.
- `docs/` — contratos, roadmap, paridade, CI e handoff.
- `.github/workflows/` — automação CI.

## Linux

- Baseline congelado: **GBW Linux 5.23.0**.
- Pacote autoritativo: `Guitar_Backing_Wizard_v5.23_Linux.zip`.
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`.
- A referência Linux permanece imutável durante a migração Android salvo decisão explícita de nova baseline.
- Contratos portáveis estão em `docs/PARITY_MATRIX.md`.

## Android

Linha atual: **6.0.0-alpha1**.

### Implementado

- projeto nativo Kotlin + Jetpack Compose;
- domínio portado da v5.23: afinações, delta global, workflow, normalização textual, regras de qualidade e Pitch de Arquivo;
- **Rápida / Demucs** como separação padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção;
- inspetor WAV nativo;
- fallback FFmpeg para formatos adicionais;
- estados Ideal / Adequado / Ressalva;
- infraestrutura `ForegroundService` `mediaProcessing`;
- persistência básica de jobs;
- cancelamento/timeout;
- UI Compose inicial e adaptativa;
- smoke test e testes de paridade de domínio;
- build Android debug real validado em CI.

### Toolchain fixado

- AGP `9.4.0`;
- Gradle `9.6.0`;
- JDK `17`;
- Kotlin `2.4.20`;
- Compose BOM `2026.08.00`;
- compileSdk `37`;
- targetSdk `36`;
- minSdk `28`.

### CI validada

O ciclo automático foi comprovado no `main`:

```text
commit/push
→ Android CI automática
→ DOMAIN_SMOKE_OK
→ Unit Tests PASS
→ Android Lint PASS
→ assembleDebug PASS
→ APK artifact publicado
```

Checkpoint funcional verde: commit `e68a41f3b626681594da99da5d9af7e7d0141d2c`, run Android CI #6. O artifact `GBW-Android-debug-6` foi publicado. O chat/agente não deve pedir ao usuário para disparar builds manualmente.

### Ainda não homologado

- Rubber Band R3 NDK/JNI;
- Pitch de Arquivo ponta a ponta com render R3 real;
- preservação/validação real de duração, canais e sample rate no render final Android;
- execução longa real em dispositivo com tela bloqueada/background/thermal;
- Demucs `htdemucs_6s` real no Android arm64;
- BS-RoFormer real no Android;
- workflow completo Fonte → Separação → Afinação → Exportação;
- persistência/restore cross-platform completos;
- release assinado e homologação física final.

## CI

A política manual-only foi revogada.

- **Todo commit/push em qualquer branch dispara Android CI automaticamente.**
- Pull Requests também executam o workflow.
- `workflow_dispatch` permanece somente como fallback.
- O agente deve consultar run, jobs, logs e artifacts autonomamente.
- Falhas devem ser corrigidas por novo commit; gates obrigatórios não podem ser silenciados.
- Um run verde publica APK debug e relatórios como artifacts.

## Próximo gate

1. integrar Rubber Band R3 via NDK/JNI;
2. fechar o Pitch de Arquivo ponta a ponta usando o mesmo contrato do Linux 5.23;
3. validar foreground/background/lock-screen de tarefas reais;
4. provar e benchmarkar Demucs `htdemucs_6s` 6 stems em Android arm64;
5. integrar BS-RoFormer;
6. avançar seriada e documentadamente pelo workflow completo e pela paridade Linux ↔ Android.

## Continuidade

O prompt oficial para outro chat está em `docs/ANDROID_HANDOFF_PROMPT.md`. A nova sessão deve confirmar o HEAD remoto, branch ativa e estado real da CI antes de qualquer escrita.
