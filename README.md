# Guitar Backing Wizard (GBW)

Repositório oficial do **Guitar Backing Wizard**, organizado por plataforma para preservar o baseline Linux e desenvolver a linha Android nativa com rastreabilidade.

## Estrutura

```text
gbw/
├── linux/                  # âncora imutável do GBW Linux 5.23
│   ├── BASELINE.md         # identidade, versão e SHA-256 autoritativos
│   └── README.md           # política do baseline Linux
├── android/                # aplicação Android nativa — linha 6.x
│   ├── app/                # módulo Android
│   ├── scripts/
│   ├── tools/
│   └── README.md
├── docs/                   # contratos, roadmap, paridade e handoff
└── .github/workflows/      # CI automática
```

## Linux — baseline congelado

O comportamento de referência é **GBW Linux 5.23.0**.

- Pacote autoritativo: `Guitar_Backing_Wizard_v5.23_Linux.zip`
- SHA-256: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- `linux/BASELINE.md` registra formalmente essa identidade.
- O pacote binário/fonte empacotado **não é presumido como presente no Git**; quando for necessário inspecioná-lo, deve-se usar uma cópia que bata exatamente com o SHA-256 acima.
- O desenvolvimento Android não altera o baseline Linux como efeito colateral.

## Android

A pasta [`android/`](android/) contém a reimplementação nativa, atualmente na linha **6.0.0-alpha**.

Stack e decisões consolidadas:

- Kotlin + Jetpack Compose / Material 3;
- AGP `9.4.0`, Gradle `9.6.0`, JDK `17`, Kotlin `2.4.20`;
- Compose BOM `2026.08.00`;
- `compileSdk 37`, `targetSdk 36`, `minSdk 28`;
- tarefas longas independentes da Activity;
- `ForegroundService` para processamento prolongado;
- **Separação Rápida / Demucs** como padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção;
- Rubber Band R3 via NDK/JNI já integrado no checkpoint atual;
- Pitch de Arquivo como primeiro fluxo DSP ponta a ponta;
- projetos/backups preparados para interoperabilidade Linux ↔ Android;
- produto final sem Termux ou Python externo.

## Branches

- `main` — estado consolidado e referência para gates aprovados.
- `dev/android-6.0` — branch oficial de desenvolvimento Android; deve permanecer baseada no `main` consolidado e ser usada para evolução seriada.

Branches temporárias antigas não são referência de desenvolvimento e devem ser ignoradas.

## CI

A CI Android é **100% automática por commit/push em qualquer branch**:

- cada push dispara `.github/workflows/android-ci.yml`;
- o agente deve consultar autonomamente run, jobs, logs e artifacts;
- gates atuais: smoke de domínio, golden Rubber Band R3, unit tests, Android Lint, `assembleDebug`, verificação nativa do APK, metadata/SHA-256 e artifacts;
- `workflow_dispatch` é apenas contingência, não o fluxo normal.

Novos chats devem sempre confirmar o HEAD e o run mais recente antes de escrever.

## Documentação autoritativa

- [`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md) — estado técnico atual.
- [`docs/ANDROID_MIGRATION_PLAN.md`](docs/ANDROID_MIGRATION_PLAN.md) — roadmap mestre Android.
- [`docs/PARITY_MATRIX.md`](docs/PARITY_MATRIX.md) — contratos Linux 5.23 ↔ Android 6.x.
- [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md) — regras de desenvolvimento.
- [`docs/CI_AUTOMATION.md`](docs/CI_AUTOMATION.md) — ciclo autônomo commit → CI → logs → APK.
- [`docs/ANDROID_HANDOFF_PROMPT.md`](docs/ANDROID_HANDOFF_PROMPT.md) — prompt oficial para continuidade entre chats.
