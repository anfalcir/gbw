# Guitar Backing Wizard (GBW)

Repositório oficial do Guitar Backing Wizard.

## Plataformas

- `linux/` — GBW Linux 5.23.0 congelado e preservado como baseline histórico/funcional.
- `android/` — produto Android 6.x em desenvolvimento ativo.

O desenvolvimento Android não altera o baseline Linux.

## Produto Android 6.0

Escopo final:

`Fonte → Demucs htdemucs_6s → seis stems → backing+guitar original → Projeto/Backup Android`

Decisões consolidadas:
- Android nativo Kotlin/Compose;
- tarefas pesadas fora da Activity;
- Demucs-only;
- sem BS-RoFormer;
- sem pitch/afinação no produto final;
- sem Pitch de Arquivo;
- sem Rubber Band no produto final;
- sem export pitched;
- projeto UUID interno;
- apresentação `Artista - Música`;
- backup Android SAF/Google Drive;
- sem requisito de interoperabilidade de projeto/backup com Linux.

## Branches

- `main` — estado consolidado;
- `dev/android-6.0` — desenvolvimento contínuo Android.

## CI

Todo push dispara Android CI automaticamente. Gates não devem ser enfraquecidos; falhas devem ser
corrigidas pela causa real.

## Documentação autoritativa

1. `docs/ANDROID_MIGRATION_PLAN.md` — **source of truth do caminho até 100%**;
2. `docs/CURRENT_STATE.md` — checkpoint atual;
3. `docs/PARITY_MATRIX.md` — contratos mantidos e divergências intencionais;
4. `docs/DEVELOPMENT_WORKFLOW.md`;
5. `docs/CI_AUTOMATION.md`;
6. `docs/ANDROID_HANDOFF_PROMPT.md`.

Roadmaps históricos não prevalecem sobre `ANDROID_MIGRATION_PLAN.md`.
