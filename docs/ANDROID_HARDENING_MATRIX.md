# GBW Android 6.0 — Matriz de Hardening

Este documento separa o que é validável digitalmente do que exige homologação física. O gate
autoritativo de roadmap continua em `docs/ANDROID_MIGRATION_PLAN.md`.

## Cobertura digital

| Área | Evidência automatizada / contrato | Estado |
|---|---|---|
| Isolamento entre projetos | `verify_project_scoped_workflow.py` + testes de identidade/pesquisa | coberto |
| Produto original-only | `verify_original_only.py` + migração schema v1→v2 | coberto |
| Demucs-only | gates de source/APK + checkpoint SHA-256 do modelo | coberto |
| Integridade de stems | validação de 6 WAVs, SR/canais/frames + testes | coberto |
| Export backing+guitar | shared gain + stress numérico + validação de frames/formato | coberto |
| Storage baixo | preflight de Fonte, Demucs e Export + aritmética saturada | coberto |
| Áudio truncado/malformado | testes de WAV inválido/truncado + falha fechada | coberto |
| Cancelamento/cleanup | coroutine cancellation + limpeza de staging/output parcial | coberto |
| Backup corrompido/incompleto | commit/manifest/hash por arquivo + staging transacional | coberto |
| Conflitos de backup | `BackupReconcilerTest` e resolução explícita | coberto |
| Coalescência de backup | quiet window/max latency extraídos e testados | coberto |
| Offline→online | WorkManager exige `NetworkType.CONNECTED` + retry/backoff | coberto por contrato |
| Histórico/observabilidade | log persistente de jobs, erro, projeto e timestamp | coberto |
| Stress lógico | 1000 identidades de projeto, 100 cópias e shared-gain sweep | coberto |
| Layout responsivo | rail/tablet ≥840dp e drawer móvel + gate estático | coberto por contrato |
| Font scale | tipografia Material sem tamanhos fixos no shell auditado | coberto por contrato |
| Touch/accessibilidade básica | controles Material textuais; sem clickables crus no shell auditado | coberto por contrato |
| Recriação/process death | projeto/job/backup são persistidos; UI recompõe da fonte durável | coberto por arquitetura |

## Homologação física final obrigatória

A automação não substitui os seguintes itens:

- instalação limpa em tablet físico;
- upgrade sobre build anterior;
- Google Drive/provider real, inclusive offline→online;
- lock screen/background prolongado;
- comportamento térmico e bateria durante Demucs;
- reprodução auditiva de stems e export;
- ergonomia, orientação e font scale extremos;
- TalkBack em uso real;
- cancelamento em tarefas longas no hardware;
- campanha final Fonte → Separação → Export → Backup → Restore.

Esses itens devem ser concentrados na campanha R6 para evitar repetição de testes físicos.
