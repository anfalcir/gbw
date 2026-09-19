# GBW — Estado Atual

**Data:** 2026-09-19  
**Branch:** dev/android-6.0  
**Baseline Linux:** 5.23.0 congelado / somente leitura  
**Roadmap:** docs/ANDROID_MIGRATION_PLAN.md

## Candidato atual

- versão: 6.0.0-rc3
- versionCode: 24
- commit: 25214623cb886697a0d847a00af7171147bbfa60
- Android CI #123 / run 35443092492: SUCCESS
- APK de homologação: app-debug.apk
- APK bytes: 65,894,905
- APK SHA-256: eb9fc4ed8a133a4ec04bf3522b3026726a5e5491ea7fa55b1772393714ddae63
- certificado de homologação SHA-256: 6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0
- artifact: GBW-Android-debug-123

## O que o RC3 corrige

- pesquisa online pertence ao ViewModel e não é cancelada ao trocar de tela;
- preparação de fonte online usa WorkManager persistente, sem consumir a quota `dataSync` de foreground service;
- resultado individual do YouTube indisponível/privado/bloqueado é descartado sem derrubar a pesquisa inteira;
- Apple Music/iTunes foi removido da pesquisa porque não entrega fonte utilizável para preparação;
- erros e alertas são mostrados junto à ação correspondente e também em toast temporário;
- textos do fluxo principal foram polidos para usuário final;
- detalhes de engenharia ficam concentrados em Sistema → Diagnóstico avançado;
- Logs deixou de ser página principal e passou para Sistema → Histórico de atividades;
- ao encerrar/remover o app e abri-lo novamente, nenhuma sessão de projeto é reaberta automaticamente;
- rotação, troca de tela e retorno normal do background preservam a sessão corrente.

## Gates fechados

- R1 / Alpha13: project scoping, sessão e Projetos.
- R2 / Alpha14: produto original-only e remoção física do stack de alteração de tom.
- R3 / Alpha15: Logs, Sistema e UX de release.
- R4 / Alpha16: hardening digital, stress lógico, storage preflight e contratos de lifecycle/backup.
- RC1: pipeline de produção separado e fail-closed.
- RC2: preflight também para incorporação de fonte local/preparada.
- RC3: resiliência de pesquisa/preparo online, quota Android, sessão limpa e polimento final de UX.

## Invariantes finais do produto

- Android-first.
- Demucs-only: htdemucs_6s.
- seis stems: drums, bass, other, vocals, guitar, piano.
- OpenBLAS default 1 thread; política 1/2.
- original-only: sem detecção/alteração de tom e sem runtime antigo.
- export: backing + guitar com shared gain.
- projectId UUID imutável.
- backup SAF/Google Drive Android v2.
- nova execução do app começa sem projeto aberto.
- Linux 5.23 permanece congelado e não é formato de projeto/backup do Android.

## O que falta para 100%

### R5 — produção pessoal

Decisão de produto: o APK final é para uso pessoal do proprietário, sem distribuição pública ou comercial.

Estado:
1. chave/secrets de produção foram informados pelo usuário como cadastrados no GitHub;
2. o conector não permite ler secrets, portanto a validade real só será comprovada no workflow manual de produção;
3. requisitos específicos de publicação/distribuição pública não fazem parte do critério de aceite desta release pessoal;
4. antes de qualquer distribuição futura a terceiros, a auditoria de licenças/direitos deve ser reaberta;
5. após R6, executar Android Production Release e registrar APK, fingerprint e SHA-256 de produção.

Infraestrutura:
- .github/workflows/android-release.yml
- android/scripts/prepare_production_signing.sh
- docs/ANDROID_PRODUCTION_SIGNING.md
- docs/ANDROID_THIRD_PARTY.md

### R6 — homologação física

RC3 precisa validar especialmente:
- pesquisa online continuando ao trocar de tela;
- YouTube ignorando resultado indisponível e retornando candidatos válidos;
- ausência de Apple Music na lista;
- preparação online sem erro de quota de processamento em segundo plano;
- preparação continuando ao mudar de tela;
- feedback contextual + toast;
- nova abertura do app sem projeto automaticamente aberto;
- Logs dentro de Sistema e ausência de textos de engenharia no fluxo principal.

Além disso permanecem:
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
- gerar APK final assinado com a chave de produção;
- consolidar relatório final;
- merge dev/android-6.0 → main.

## Regra

Não declarar 100% antes de R1–R6 estarem fechados. Não usar a chave pública de homologação no APK final.
