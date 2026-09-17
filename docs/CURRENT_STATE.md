# GBW — Estado Atual

**Data:** 2026-09-17  
**Repositório:** `anfalcir/gbw`  
**Branch principal:** `main`

## Organização

- `linux/` — identidade congelada do baseline Linux 5.23.
- `android/` — aplicação Android nativa ativa.
- `docs/` — contratos, roadmap, paridade e handoff.
- `.github/workflows/` — automação CI.

## Linux

- Baseline congelado: **GBW Linux 5.23.0**.
- Pacote autoritativo: `Guitar_Backing_Wizard_v5.23_Linux.zip`.
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`.
- A referência Linux é imutável durante a migração Android salvo decisão explícita de nova baseline.
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
- UI Compose inicial;
- smoke test e testes de paridade de domínio.

### Toolchain fixado

- AGP `9.4.0`;
- Gradle `9.6.0`;
- JDK `17`;
- Kotlin `2.4.20`;
- Compose BOM `2026.08.00`;
- compileSdk `36`;
- targetSdk `36`;
- minSdk `28`.

### Ainda não homologado

- primeiro Android CI verde após a consolidação;
- Rubber Band R3 NDK/JNI;
- Pitch de Arquivo ponta a ponta com render R3;
- Demucs `htdemucs_6s` real no Android;
- BS-RoFormer real no Android;
- workflow completo Fonte → Separação → Afinação → Exportação;
- persistência/restore cross-platform completos;
- homologação física arm64/background/thermal.

## CI

A política manual-only foi revogada.

- **Todo commit/push em qualquer branch dispara Android CI automaticamente.**
- O agente deve consultar o run, jobs e logs sem pedir ação manual ao usuário.
- Falhas devem ser corrigidas por novo commit; gates obrigatórios não podem ser silenciados.
- Um run verde publica APK debug, SHA-256 e relatórios como artifacts.
- `workflow_dispatch` permanece somente como fallback.

## Próximo gate

1. obter o primeiro Android CI verde no repositório consolidado;
2. corrigir incompatibilidades reais de toolchain/dependências;
3. integrar Rubber Band R3 via NDK/JNI;
4. fechar Pitch de Arquivo ponta a ponta;
5. provar e benchmarkar Demucs 6 stems em Android arm64 real.

## Continuidade

O prompt oficial para outro chat está em `docs/ANDROID_HANDOFF_PROMPT.md`. A nova sessão deve confirmar o HEAD e o estado real da CI antes de escrever.
