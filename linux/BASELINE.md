# GBW Linux 5.23 — Baseline Congelado

**Versão:** `5.23.0`  
**Pacote autoritativo:** `Guitar_Backing_Wizard_v5.23_Linux.zip`  
**SHA-256:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

A pasta `linux/app/` corresponde ao pacote autoritativo acima e representa a referência funcional, comportamental e de qualidade para o desenvolvimento Android 6.x.

## Regra de congelamento

Durante a migração Android, não modificar contratos do baseline Linux sem decisão explícita. Se uma nova baseline for aprovada:

1. registrar nova versão e SHA-256;
2. atualizar `linux/app/`;
3. atualizar `docs/PARITY_MATRIX.md`;
4. atualizar `docs/ANDROID_MIGRATION_PLAN.md` quando necessário;
5. registrar a mudança em `docs/CURRENT_STATE.md`.
