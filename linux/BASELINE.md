# GBW Linux 5.23 — Baseline Congelado

**Versão:** `5.23.0`  
**Pacote autoritativo:** `Guitar_Backing_Wizard_v5.23_Linux.zip`  
**SHA-256:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

Este diretório é a **âncora imutável de identidade** do baseline Linux usado como referência funcional, comportamental e de qualidade para o desenvolvimento Android 6.x.

O pacote autoritativo não é presumido como armazenado dentro do Git. Sempre que for necessário inspecionar ou comparar o código Linux, deve-se usar uma cópia de `Guitar_Backing_Wizard_v5.23_Linux.zip` e validar que o SHA-256 seja exatamente o registrado acima antes de tratá-la como fonte de verdade.

## Regra de congelamento

Durante a migração Android, não modificar contratos do baseline Linux sem decisão explícita. Se uma nova baseline for aprovada:

1. registrar nova versão e SHA-256;
2. registrar a identidade do novo pacote em `linux/BASELINE.md`;
3. atualizar `docs/PARITY_MATRIX.md`;
4. atualizar `docs/ANDROID_MIGRATION_PLAN.md` quando necessário;
5. registrar a mudança em `docs/CURRENT_STATE.md`;
6. atualizar o prompt de handoff se o baseline funcional mudar.

## Regra para agentes

- Não inventar nem reconstruir comportamento Linux apenas a partir da memória.
- Não assumir que existe `linux/app/` se essa pasta não estiver realmente presente no HEAD consultado.
- Para dúvidas de paridade, priorizar os contratos versionados em `docs/PARITY_MATRIX.md` e, quando necessário, conferir o pacote 5.23 cuja hash corresponda à identidade acima.
