# Handoff — GBW Android 6.0

Repo: `anfalcir/gbw`  
Branch ativa: `dev/android-6.0`  
Baseline Linux: `linux/` = GBW Linux 5.23.0, somente leitura.

## Leia primeiro

1. `docs/ANDROID_MIGRATION_PLAN.md` — source of truth;
2. `docs/CURRENT_STATE.md`;
3. `docs/PARITY_MATRIX.md`;
4. `docs/DEVELOPMENT_WORKFLOW.md`;
5. confirmar HEAD remoto e CI recentes.

## Escopo final consolidado

Fluxo Android:

`Fonte → Separação Demucs → Export original backing+guitar → Projeto/Backup Android`

### NÃO implementar/reintroduzir

- Afinação & Pitch;
- detecção de afinação;
- Pitch de Arquivo;
- Rubber Band;
- export pitched;
- BS-RoFormer;
- Alta Qualidade/Comparar;
- interoperabilidade de projeto/backup Linux↔Android.

Esses itens são decisões de produto, não pendências.

## Backup

Sistema oficial:
- Android SAF;
- Google Drive quando escolhido pelo usuário;
- backup automático/manual;
- UUID interno;
- pastas humanas `Artista - Música`;
- restore Android;
- dedupe/hash;
- conflitos explícitos.

A homologação alpha12 confirmou funcionamento real do backup automático e da organização no Drive.

## Checkpoint

- `6.0.0-alpha12`;
- versionCode 17;
- commit `9631fd165c457adeb103912633e2cc59d3adb32e`;
- CI #108 SUCCESS;
- APK SHA-256 `cded2ef6bb8ea02c329434c5d58550c9c4cfd9b3b18c8b42cb4bf5f1dff76ae7`.

## Próximo trabalho

R1 / alpha13:
- corrigir vazamento de stems ao fechar projeto;
- eliminar mensagem stale de fechamento;
- remover duplicação do nome do projeto;
- state scoping por projectId;
- melhorar Projetos;
- estados vazios/CTAs;
- workflow coerente entre sessões.

R2:
- remover integralmente pitch/tuning/Rubber Band e export ajustado.

Depois seguir R3–R7 do roadmap até 6.0.0.

## Regra de execução

Antes de escrever:
- confirmar HEAD;
- não tocar `linux/`;
- preservar Demucs-only;
- acompanhar Android CI após commits;
- nunca enfraquecer gates para obter verde.
