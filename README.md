# Guitar Backing Wizard (GBW)

Repositório oficial do **Guitar Backing Wizard**, organizado por plataforma para preservar o baseline Linux e desenvolver a linha Android nativa com rastreabilidade.

## Estrutura

```text
gbw/
├── linux/                  # referência congelada do GBW Linux 5.23
│   ├── BASELINE.md         # identidade, versão e SHA-256 autoritativos
│   └── README.md
├── android/                # aplicação Android nativa — linha 6.x
│   ├── app/                # módulo Android
│   ├── scripts/
│   ├── tools/
│   └── README.md
├── docs/                   # documentação compartilhada e migração
│   ├── ANDROID_MIGRATION_PLAN.md
│   ├── CURRENT_STATE.md
│   ├── PARITY_MATRIX.md
│   ├── DEVELOPMENT_WORKFLOW.md
│   ├── REPOSITORY_STRUCTURE.md
│   └── ANDROID_HANDOFF_PROMPT.md
└── .github/workflows/
    ├── android-ci.yml
    └── linux-ci.yml
```

## Linux — baseline congelado

O comportamento de referência é **GBW Linux 5.23.0**.

- Pacote autoritativo: `Guitar_Backing_Wizard_v5.23_Linux.zip`
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- `linux/BASELINE.md` registra formalmente essa identidade.
- O desenvolvimento Android não altera o baseline Linux como efeito colateral.

A árvore Linux não é a linha ativa de desenvolvimento deste repositório durante a migração; os contratos necessários estão documentados na matriz de paridade e no plano Android.

## Android

A pasta [`android/`](android/) contém a reimplementação nativa, atualmente na linha **6.0.0-alpha**.

Princípios consolidados:

- Kotlin + Jetpack Compose / Material 3;
- AGP 9.4.0, Gradle 9.6, JDK 17, compileSdk 37;
- tarefas longas independentes da Activity;
- `ForegroundService` para processamento prolongado;
- **Separação Rápida / Demucs** como padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção;
- Pitch de Arquivo como primeiro fluxo DSP de paridade;
- projetos/backups preparados para interoperabilidade Linux ↔ Android;
- produto final sem Termux ou Python externo.

## CI

A CI Android é **100% automática por commit/push em qualquer branch**:

- cada push dispara `.github/workflows/android-ci.yml`;
- a sessão de desenvolvimento deve consultar autonomamente run, jobs e logs;
- quando verde, o workflow publica APK debug + SHA-256 como artifact;
- `workflow_dispatch` é apenas contingência, não o fluxo normal.

A CI Linux apenas protege a identidade documental do baseline congelado quando `linux/**`/paridade mudam.

## Documentação autoritativa

- [`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md) — estado técnico atual.
- [`docs/ANDROID_MIGRATION_PLAN.md`](docs/ANDROID_MIGRATION_PLAN.md) — roadmap mestre Android.
- [`docs/PARITY_MATRIX.md`](docs/PARITY_MATRIX.md) — contratos Linux 5.23 ↔ Android 6.x.
- [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md) — regras de desenvolvimento e CI.
- [`docs/CI_AUTOMATION.md`](docs/CI_AUTOMATION.md) — ciclo autônomo commit → CI → logs → APK.
- [`docs/ANDROID_HANDOFF_PROMPT.md`](docs/ANDROID_HANDOFF_PROMPT.md) — prompt oficial de continuidade entre chats.
