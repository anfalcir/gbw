# Estrutura do Repositório GBW

O repositório separa a referência Linux congelada da implementação Android ativa.

```text
/
├── linux/            # identidade do baseline Linux 5.23
├── android/          # produto Android nativo
│   └── app/          # módulo Android
├── docs/             # contratos e documentação compartilhados
└── .github/workflows # automação CI
```

## Regras de fronteira

- `linux/**` identifica o baseline congelado e não é alterado como efeito colateral do Android.
- Código Android permanece em `android/**`.
- Contratos cross-platform ficam em `docs/**`.
- Artefatos de usuário, projetos, músicas, modelos grandes, caches, venvs, build outputs e keystores privados nunca entram no Git.
- Android deve portar contratos do Linux 5.23, não importar módulos Python como runtime.

## Versionamento

- Linux baseline: `5.23.0`.
- Android: `6.0.0-alphaN` → `beta` → `rc` → `6.0.0`.

## CI

- Android CI: todo push/commit em qualquer branch.
- Linux Baseline CI: somente quando a referência Linux/paridade/workflow correspondente muda.
- `workflow_dispatch`: fallback, não dependência do fluxo normal.

## Automação

`docs/CI_AUTOMATION.md` e `docs/DEVELOPMENT_WORKFLOW.md` definem o ciclo autônomo commit → CI → logs → correção → artifact APK.
