# GBW Android 6.0

Aplicativo Android nativo do Guitar Backing Wizard.

Produto: **Android-first, Demucs-only e original-only**.

## Workflow

1. Fonte
2. Separação
3. Exportação

Biblioteca: Projetos.  
Aplicativo: Configurações e Sistema.  
Histórico de atividades/logs fica dentro de Sistema.

## Separação

- Demucs htdemucs_6s;
- seis stems: drums, bass, other, vocals, guitar, piano;
- float32 estéreo / 44,1 kHz no runtime;
- arm64-v8a;
- OpenBLAS default 1 thread; política 1/2;
- processamento pesado no processo :media;
- cancelamento/cleanup e preflight de storage.

## Fonte online

- busca automática por fontes utilizáveis;
- Apple Music/iTunes não participa da lista porque não fornece mídia adquirível pelo GBW;
- resultados YouTube indisponíveis são descartados individualmente sem abortar candidatos saudáveis;
- pesquisa sobrevive à navegação entre telas;
- preparação online usa WorkManager persistente e não depende da quota foreground `dataSync`.

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
- uma nova execução do aplicativo começa sem projeto automaticamente aberto;
- SAF / Google Drive;
- backup automático/manual;
- restore Android;
- dedupe/hash e conflito explícito.

Não existe requisito de projeto/backup cross-platform com Linux.

## Candidato atual

6.0.0-rc3 / versionCode 24

- commit 25214623cb886697a0d847a00af7171147bbfa60;
- Android CI #123 / run 35443092492: SUCCESS;
- APK bytes 65,894,905;
- APK SHA-256 eb9fc4ed8a133a4ec04bf3522b3026726a5e5491ea7fa55b1772393714ddae63;
- assinatura: homologation-public-test-key.

A release final é destinada a uso pessoal e deve usar a chave privada de produção.

Source of truth: docs/ANDROID_MIGRATION_PLAN.md  
Estado: docs/CURRENT_STATE.md  
Terceiros/compliance: docs/ANDROID_THIRD_PARTY.md  
Homologação física: docs/ANDROID_FINAL_VALIDATION.md
