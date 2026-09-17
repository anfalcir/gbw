# GBW Linux — referência congelada

O produto Linux usado como referência funcional da migração Android é o **GBW 5.23.0**.

## Identidade autoritativa

- Pacote: `Guitar_Backing_Wizard_v5.23_Linux.zip`
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- Contrato formal: `BASELINE.md`

Durante a migração Android, esta pasta funciona como **âncora imutável de baseline**, não como linha ativa de desenvolvimento. Os comportamentos que precisam ser reproduzidos no Android são registrados em `docs/PARITY_MATRIX.md` e `docs/ANDROID_MIGRATION_PLAN.md`.

Uma nova baseline Linux só pode substituir a 5.23 mediante decisão explícita, nova versão, novo SHA-256 e atualização coordenada da documentação de paridade.
