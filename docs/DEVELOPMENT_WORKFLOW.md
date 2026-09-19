# Workflow de Desenvolvimento e CI

## Regra principal

Fluxo Android:

consultar HEAD remoto
→ ler source of truth
→ editar
→ commit/push
→ Android CI automática
→ consultar run/jobs/logs
→ corrigir causa real
→ repetir até verde
→ registrar SHA/artefato

Não é necessário workflow_dispatch para o fluxo normal.

## Antes de escrever

1. confirmar HEAD remoto de dev/android-6.0;
2. ler docs/ANDROID_MIGRATION_PLAN.md;
3. ler docs/CURRENT_STATE.md;
4. ler docs/PARITY_MATRIX.md;
5. conferir runs recentes;
6. nunca alterar linux/app como efeito colateral do Android.

## Política de commits

- commits semanticamente fechados;
- mensagens claras;
- não criar commit vazio para forçar CI;
- não enfraquecer gates;
- toda falha exige leitura do log;
- preservar decisões Demucs-only, original-only e Android-first.

## Android CI obrigatória

- domínio;
- project scoping;
- original-only;
- release UX;
- hardening;
- release-pipeline contract;
- testes unitários;
- lint;
- assemble debug;
- arquitetura nativa;
- modelo Demucs;
- FFmpeg runtime;
- assinatura de homologação;
- metadata/SHA;
- artifacts.

## Release de produção

.github/workflows/android-release.yml é manual por design.

Nunca:
- versionar private key;
- reutilizar a chave pública de homologação;
- contornar falta de LICENSE/compliance;
- publicar APK sem verificar fingerprint e SHA-256.

A release só pode avançar quando os blockers em docs/ANDROID_THIRD_PARTY.md estiverem fechados.

## Linux baseline

Linux 5.23 continua congelado. Linux CI valida a baseline sem alterar linux/app.

## Segurança

Nunca versionar:
- keystore privado;
- senhas/tokens;
- músicas/projetos de usuário;
- outputs;
- modelos grandes;
- caches/venvs/build outputs.
