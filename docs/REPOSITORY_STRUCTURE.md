# Estrutura do Repositório GBW

O repositório separa a distribuição Linux congelada da implementação Android ativa.

```text
/
├── linux/
│   ├── app/              # distribuição operacional completa do Linux 5.23
│   ├── ci/               # validadores de CI fora da baseline congelada
│   ├── BASELINE.md       # identidade/contrato da baseline
│   ├── MANIFEST.sha256   # integridade byte-a-byte da árvore expandida
│   └── README.md         # uso e preservação
├── android/              # produto Android nativo
│   └── app/              # módulo Android
├── docs/                 # contratos e documentação compartilhados
└── .github/workflows/    # automação CI
```

## Regras de fronteira

- `linux/app/**` preserva o baseline congelado completo e não é alterado como efeito colateral do Android.
- `linux/ci/**` contém apenas validadores de repositório/CI e fica deliberadamente fora do manifesto byte-a-byte da aplicação congelada.
- Código Android permanece em `android/**`.
- Contratos cross-platform ficam em `docs/**`.
- Artefatos de usuário, projetos, músicas, modelos grandes, caches, venvs, build outputs e keystores privados nunca entram no Git.
- A exclusão de venv/cache/build não reduz a preservação do produto: `linux/app/install.sh` e `requirements.txt` recriam o ambiente; a distribuição de aplicação em si está integralmente versionada.
- Android deve portar contratos do Linux 5.23, não importar módulos Python como runtime.

## Versionamento

- Linux baseline: `5.23.0`.
- Android: `6.0.0-alphaN` → `beta` → `rc` → `6.0.0`.

## CI

- Android CI: todo push/commit em qualquer branch.
- Linux Baseline CI: quando `linux/**`, `docs/PARITY_MATRIX.md` ou o workflow correspondente muda.
- Linux CI valida manifesto, permissões, sintaxe, compilação Python, testes e self-test da distribuição congelada.
- `workflow_dispatch`: fallback, não dependência do fluxo normal.

## Automação

`docs/CI_AUTOMATION.md` e `docs/DEVELOPMENT_WORKFLOW.md` definem o ciclo autônomo commit → CI → logs → correção → artifact APK.
