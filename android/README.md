# GBW Android 6.x

Aplicativo Android nativo do Guitar Backing Wizard.

O produto final 6.0 é **Android-first** e **original-only**.

## Workflow alvo

1. Fonte
2. Separação Demucs
3. Exportação original

Gerenciamento:
- Projetos
- Logs

Backup:
- SAF / Google Drive;
- automático e manual;
- restore Android;
- organização por `Artista - Música`.

## Separação

Somente Demucs `htdemucs_6s`.

Contrato:
- seis stems: drums, bass, other, vocals, guitar, piano;
- float32 estéreo / 44,1 kHz no runtime do modelo;
- um chunk por vez;
- OpenBLAS default 1 thread;
- política interna 1/2;
- arm64-v8a.

Benchmark físico já confirmou 1 thread como configuração preferida no Samsung Galaxy Tab A11+.

## Export

Produto final:
- backing = drums + bass + other + vocals + piano;
- guitar separada;
- shared gain comum;
- tom original da fonte.

Não haverá export pitched no produto final.

## Decisões removidas do escopo

O código alpha12 ainda contém partes históricas que serão removidas no gate R2:
- Afinação & Pitch;
- Pitch de Arquivo;
- Rubber Band R3;
- export ajustado/pitched.

Não existe requisito de projeto/backup cross-platform com Linux.

## Checkpoint

`6.0.0-alpha12` / versionCode 17

- commit: `9631fd165c457adeb103912633e2cc59d3adb32e`;
- CI #108: SUCCESS;
- APK SHA-256: `cded2ef6bb8ea02c329434c5d58550c9c4cfd9b3b18c8b42cb4bf5f1dff76ae7`.

## Roadmap

A fonte de verdade é:
`docs/ANDROID_MIGRATION_PLAN.md`.
