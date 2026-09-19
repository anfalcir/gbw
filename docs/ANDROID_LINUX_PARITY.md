# GBW Android — Uso do Linux 5.23 como Referência

O Linux 5.23 permanece congelado em `linux/`.

A partir da revisão de escopo de 2026-09-18, **não existe meta de paridade total**.

## Linux continua sendo referência para

- normalização de artista/música;
- pesquisa/ranking de fontes;
- conceitos de estado inicial/projeto aberto;
- fechar projeto sem excluir;
- organização/listagem de projetos;
- estados vazios;
- logs;
- princípios de export backing+guitar;
- UX e mensagens quando úteis.

## Divergências intencionais do Android

- separação: somente Demucs `htdemucs_6s`;
- sem BS-RoFormer;
- sem detecção de afinação;
- sem pitch;
- sem Pitch de Arquivo;
- sem Rubber Band no produto final;
- sem export pitched;
- projeto/backup usa schema Android próprio;
- backup oficial é SAF/Google Drive;
- não existe round-trip de projeto/backup Linux↔Android.

Essas divergências não são gaps.

Para contratos detalhados, use `docs/PARITY_MATRIX.md`.  
Para o caminho até 100%, use `docs/ANDROID_MIGRATION_PLAN.md`.
