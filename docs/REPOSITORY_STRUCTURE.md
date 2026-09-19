# Estrutura do Repositório GBW

O repositório separa a distribuição Linux congelada da implementação Android ativa.

/
├── linux/
│   ├── app/              # distribuição Linux 5.23 congelada
│   ├── ci/               # harness/validadores fora da baseline
│   ├── BASELINE.md
│   ├── MANIFEST.sha256
│   └── README.md
├── android/
│   └── app/              # produto Android
├── docs/
└── .github/workflows/
    ├── android-ci.yml
    ├── android-release.yml
    └── linux-ci.yml

## Fronteiras

- linux/app/** é somente leitura para o trabalho Android;
- linux/ci/** pode conter harness de validação sem alterar a baseline;
- Android vive em android/**;
- contratos ficam em docs/**;
- dados de usuário, modelos grandes, caches, build outputs e private keys não entram no Git.

## Versionamento

- Linux: 5.23.0 congelado;
- Android: 6.0.0-alphaN → 6.0.0-rcN → 6.0.0.

## CI

- Android CI: push/PR;
- Android Production Release: manual, com assinatura privada e gates de compliance;
- Linux Baseline CI: alterações relevantes à baseline/harness.

## Automação

docs/CI_AUTOMATION.md e docs/DEVELOPMENT_WORKFLOW.md definem o ciclo commit → CI → logs → correção → artifact.
