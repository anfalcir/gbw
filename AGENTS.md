# GBW — Regras para agentes de desenvolvimento

Este repositório é o monorepo oficial do Guitar Backing Wizard.

## Fonte de verdade

- Linux congelado: `linux/app/` — GBW 5.23.0.
- Plano Android: `docs/ANDROID_MIGRATION_PLAN.md`.
- Estado atual: `docs/CURRENT_STATE.md`.
- Paridade: `docs/PARITY_MATRIX.md`.
- Workflow: `docs/DEVELOPMENT_WORKFLOW.md`.

## Regras obrigatórias

1. Antes de escrever, confirmar o HEAD remoto da branch ativa.
2. Não alterar `linux/**` durante desenvolvimento Android sem decisão explícita de nova baseline.
3. Android é reimplementação nativa; não embutir Python/Termux como runtime do produto.
4. Separação Rápida / Demucs é o padrão Android até benchmark aprovado em contrário.
5. Tarefas pesadas não dependem da Activity e devem suportar background/bloqueio de tela.
6. `Pitch de Arquivo` é o primeiro fluxo DSP a fechar ponta a ponta.
7. Não esconder falhas de CI; corrigir a causa real.
8. Não versionar segredos, keystores privados, projetos/músicas do usuário, modelos grandes ou outputs de build.
9. Atualizar `docs/CURRENT_STATE.md` ao fechar qualquer gate relevante.
10. Todo commit dispara Android CI automaticamente; acompanhar o run antes de declarar um gate fechado.

## Branches

- `main`: estado consolidado/estável do monorepo.
- `android/dev`: branch recomendada para desenvolvimento Android contínuo.

Antes de continuar Android em outra sessão, usar integralmente `docs/ANDROID_HANDOFF_PROMPT.md`.
