# GBW — Regras para agentes de desenvolvimento

Este repositório é a fonte operacional do desenvolvimento Android do Guitar Backing Wizard e também preserva integralmente o baseline Linux congelado.

## Fonte de verdade

- Baseline Linux congelado: `linux/BASELINE.md` — GBW 5.23.0 + SHA-256 autoritativo.
- Distribuição Linux preservada: `linux/app/`.
- Integridade da distribuição Linux: `linux/MANIFEST.sha256`.
- Plano Android: `docs/ANDROID_MIGRATION_PLAN.md`.
- Estado atual: `docs/CURRENT_STATE.md`.
- Paridade: `docs/PARITY_MATRIX.md`.
- Workflow: `docs/DEVELOPMENT_WORKFLOW.md`.

## Regras obrigatórias

1. Antes de escrever, confirmar o HEAD remoto da branch ativa.
2. Não alterar a identidade nem o conteúdo do baseline Linux sem decisão explícita de nova baseline.
3. Qualquer alteração deliberada em `linux/app/` exige nova identidade de baseline ou restauração byte-a-byte conforme `linux/MANIFEST.sha256`.
4. Android é reimplementação nativa; não embutir Python/Termux como runtime do produto.
5. Separação Rápida / Demucs é o padrão Android até benchmark aprovado em contrário.
6. Tarefas pesadas não dependem da Activity e devem suportar background/bloqueio de tela.
7. `Pitch de Arquivo` é o primeiro fluxo DSP ponta a ponta e deve permanecer como gate de regressão.
8. Não esconder falhas de CI; corrigir a causa real.
9. Não versionar segredos, keystores privados, projetos/músicas do usuário, modelos grandes ou outputs de build.
10. Atualizar `docs/CURRENT_STATE.md` ao fechar qualquer gate relevante.
11. Todo commit/push dispara Android CI automaticamente; acompanhar o run antes de declarar um gate fechado.
12. Alterações no baseline Linux devem passar pela Linux Baseline CI antes de serem consideradas válidas.

## Branches

- `main`: estado consolidado/estável.
- `dev/android-6.0`: branch oficial de desenvolvimento Android contínuo.

Antes de continuar Android em outra sessão, usar integralmente `docs/ANDROID_HANDOFF_PROMPT.md`.
