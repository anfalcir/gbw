# GBW Android 6.0

Aplicativo Android nativo do Guitar Backing Wizard.

Produto: **Android-first, Demucs-only e original-only**.

## Workflow

1. Fonte
2. Separação
3. Exportação

Gerenciamento: Projetos e Logs.  
Aplicativo: Configurações e Sistema.

## Separação

- Demucs htdemucs_6s;
- seis stems: drums, bass, other, vocals, guitar, piano;
- float32 estéreo / 44,1 kHz no runtime;
- arm64-v8a;
- OpenBLAS default 1 thread; política 1/2;
- processamento pesado no processo :media;
- cancelamento/cleanup e preflight de storage.

## Export

- backing = drums + bass + other + vocals + piano;
- guitar separada;
- shared gain comum;
- FLAC 24, WAV 24 e WAV float32;
- sempre no tom original da fonte.

Não existem no Android 6.0: detecção/alteração de tom, ferramenta de alteração de arquivo, Rubber Band, BS-RoFormer ou export ajustado.

## Projetos e backup

- UUID imutável;
- sessão isolada por projectId;
- SAF / Google Drive;
- backup automático/manual;
- restore Android;
- dedupe/hash e conflito explícito.

Não existe requisito de projeto/backup cross-platform com Linux.

## Candidato atual

6.0.0-rc2 / versionCode 23

- commit d49a84ad00eaa21328a2742ae0be5caca815d3bd;
- Android CI #119: SUCCESS;
- APK SHA-256 eb1cfd6b3316a53a8399e69e3df4a141229adeaacd3366bd4f78fac7812cf21c;
- assinatura: homologation-public-test-key.

Produção usa workflow separado e exige private key + compliance de distribuição.

Source of truth: docs/ANDROID_MIGRATION_PLAN.md  
Estado: docs/CURRENT_STATE.md  
Compliance: docs/ANDROID_THIRD_PARTY.md  
Homologação física: docs/ANDROID_FINAL_VALIDATION.md
