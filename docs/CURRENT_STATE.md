# GBW — Estado Atual

**Data:** 2026-09-17  
**Repositório:** `anfalcir/gbw`

## Linux

- Baseline congelado: **GBW Linux 5.23.0**.
- ZIP autoritativo: `Guitar_Backing_Wizard_v5.23_Linux.zip`.
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`.
- A pasta `linux/v5.23/src/` contém a fonte expandida correspondente ao baseline.
- Não alterar esse baseline durante a migração Android sem decisão explícita de revisão da baseline.

## Android

Linha de desenvolvimento: **6.0.0-alpha1**.

### Implementado no código

- Projeto Android nativo Kotlin + Jetpack Compose.
- Navegação responsiva inicial para tablet/telefone.
- Domínio portado da v5.23:
  - afinações;
  - delta global de semitons;
  - validação do workflow;
  - normalização de artista/música;
  - regras de qualidade do input;
  - regras do Pitch de Arquivo.
- Separação **Rápida / Demucs** como padrão Android.
- Alta qualidade / BS-RoFormer preservada como opção de domínio/UI.
- Pitch de Arquivo com:
  - seletor SAF;
  - inspeção WAV nativa;
  - inspeção FFmpeg para formatos não-WAV;
  - classificação Ideal / Adequado / Ressalva;
  - modo por afinação e por semitons;
  - Instrumento/Mix e Vocal;
  - seleção de formato final.
- Infraestrutura de `ForegroundService` do tipo `mediaProcessing`:
  - notificação persistente;
  - cancelamento;
  - estado persistido do job;
  - callback de timeout Android moderno.
- Testes de paridade de domínio e smoke test independente do SDK.

### Validado neste ambiente

- SHA do baseline Linux confirmado.
- Smoke test de domínio Kotlin: `DOMAIN_SMOKE_OK`.
- Estrutura de projeto e contratos de domínio auditados estaticamente.

### Ainda NÃO homologado

- Build APK completo com Android SDK/AGP.
- Rubber Band R3 via NDK/JNI.
- Render de Pitch de Arquivo ponta a ponta no Android.
- Demucs/htdemucs_6s real no Android.
- BS-RoFormer-SW real no Android.
- Workflow completo Fonte → Separação → Afinação → Exportação.
- Persistência/restore cross-platform completos.
- Homologação em dispositivo Android real.

## Próximo gate

1. Compilar `android/` em CI/manual toolchain Android.
2. Corrigir qualquer incompatibilidade de AGP/Compose/FFmpegKit.
3. Integrar Rubber Band R3 NDK/JNI.
4. Fechar Pitch de Arquivo ponta a ponta.
5. Benchmarkar Demucs 6 stems no Android real.

## Política de CI

O workflow Android deste repositório é **manual-only** (`workflow_dispatch`). Nenhum push deve consumir GitHub Actions automaticamente.
