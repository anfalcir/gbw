# GBW Android 6.x

Reimplementação Android nativa do Guitar Backing Wizard, tendo o GBW Linux 5.23 como baseline funcional.

## Estado

Versão de desenvolvimento: `6.0.0-alpha1`.

Esta árvore ainda é **pré-APK homologável**. O domínio e a infraestrutura inicial estão implementados, mas os gates de DSP/ML nativo ainda precisam ser fechados.

## Stack

- Kotlin com suporte Kotlin integrado do AGP 9.x.
- Jetpack Compose / Material 3.
- compileSdk 37 / targetSdk 36 / minSdk 28.
- Foreground Service `mediaProcessing` para tarefas longas.
- Storage Access Framework para arquivos do usuário.
- FFmpegKit mantido para inspeção/codec enquanto a camada nativa definitiva é consolidada.
- Rubber Band R3 planejado via NDK/JNI.

## Decisão Android

A separação inicial padrão é **Rápida**, mapeada para Demucs `htdemucs_6s`. Alta qualidade / BS-RoFormer permanece disponível.

## Validação de domínio sem SDK

```bash
bash scripts/validate_domain.sh
```

Resultado esperado:

```text
DOMAIN_SMOKE_OK
```

## Build

O workflow `.github/workflows/android-ci.yml` é manual-only e será o primeiro gate de compilação reproduzível enquanto o ambiente local não possui Android SDK completo.

## Roadmap

Consulte `../docs/ANDROID_MIGRATION_PLAN.md` e `../docs/CURRENT_STATE.md`.
