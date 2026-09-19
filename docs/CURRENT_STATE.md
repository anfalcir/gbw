# GBW — Estado Atual

**Data:** 2026-09-19  
**Branch:** dev/android-6.0  
**Baseline Linux:** 5.23.0 congelado / somente leitura  
**Roadmap:** docs/ANDROID_MIGRATION_PLAN.md

## Candidato atual

- versão: 6.0.0-rc2
- versionCode: 23
- commit: d49a84ad00eaa21328a2742ae0be5caca815d3bd
- Android CI #119 / run 35439632899: SUCCESS
- APK de homologação: app-debug.apk
- APK bytes: 65,872,133
- APK SHA-256: eb1cfd6b3316a53a8399e69e3df4a141229adeaacd3366bd4f78fac7812cf21c
- certificado de homologação SHA-256: 6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0
- artifact: GBW-Android-debug-119

## Gates fechados

- R1 / Alpha13: project scoping, sessão e Projetos.
- R2 / Alpha14: produto original-only e remoção física do stack de alteração de tom.
- R3 / Alpha15: Logs, Sistema e UX de release.
- R4 / Alpha16: hardening digital, stress lógico, storage preflight e contratos de lifecycle/backup.
- RC1: pipeline de produção separado e fail-closed.
- RC2: preflight também para incorporação de fonte local/preparada.

## Invariantes finais do produto

- Android-first.
- Demucs-only: htdemucs_6s.
- seis stems: drums, bass, other, vocals, guitar, piano.
- OpenBLAS default 1 thread; política 1/2.
- original-only: sem detecção/alteração de tom e sem runtime antigo.
- export: backing + guitar com shared gain.
- projectId UUID imutável.
- backup SAF/Google Drive Android v2.
- Linux 5.23 permanece congelado e não é formato de projeto/backup do Android.

## O que falta para 100%

### R5 — produção

Ainda aberto:
1. provisionar chave privada de produção;
2. cadastrar os cinco GitHub Actions secrets;
3. escolher/registrar LICENSE do projeto compatível com a distribuição;
4. resolver explicitamente o licenciamento do checkpoint htdemucs_6s;
5. executar Android Production Release e registrar APK/fingerprint/SHA de produção.

Infraestrutura pronta:
- .github/workflows/android-release.yml
- android/scripts/prepare_production_signing.sh
- docs/ANDROID_PRODUCTION_SIGNING.md
- docs/ANDROID_THIRD_PARTY.md

### R6 — homologação física

Ainda exige hardware/percepção humana:
- instalação/upgrade;
- background/lock screen;
- temperatura/RAM/bateria no Demucs;
- escuta dos seis stems e export;
- Google Drive real, offline/online, conflito e restore;
- ergonomia, orientação, font scale e TalkBack;
- cancelamento prolongado.

Checklist: docs/ANDROID_FINAL_VALIDATION.md

### R7

Somente depois de R5 + R6:
- elevar para 6.0.0;
- gerar APK final de produção;
- consolidar relatório final;
- merge dev/android-6.0 → main.

## Regra

Não declarar 100% antes de R1–R6 estarem fechados. Não usar a chave pública de homologação em produção.
