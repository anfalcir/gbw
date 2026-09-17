# Guitar Backing Wizard (GBW)

Repositório oficial do **Guitar Backing Wizard**, organizado por plataforma para preservar integralmente o baseline Linux e desenvolver a linha Android nativa com rastreabilidade.

## Estrutura

```text
gbw/
├── linux/                  # distribuição operacional congelada do GBW Linux 5.23
│   ├── app/                # sistema Linux completo, expandido e pronto para instalar/usar
│   ├── ci/                 # validadores de CI; não altera a baseline congelada
│   ├── BASELINE.md         # identidade, versão e SHA-256 autoritativos
│   ├── MANIFEST.sha256     # integridade byte-a-byte da árvore expandida
│   └── README.md           # uso e política de preservação
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

- Pacote de origem validado: `Guitar_Backing_Wizard_v5.23_Linux.zip`
- SHA-256 do pacote de origem: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- `linux/app/` contém a distribuição completa expandida diretamente desse pacote autoritativo.
- `linux/MANIFEST.sha256` fixa a integridade de cada arquivo preservado.
- `linux/BASELINE.md` registra formalmente a identidade e a regra de congelamento.
- Baixar `linux/` é suficiente para preservar, instalar, validar e executar a versão Linux 5.23; o ZIP original não é necessário para uso.
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
- arquitetura de projetos/backups preparada para interoperabilidade Linux ↔ Android, com implementação completa ainda sujeita aos gates do roadmap;
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

A Linux Baseline CI roda quando `linux/**`, `docs/PARITY_MATRIX.md` ou o próprio workflow Linux mudam e valida identidade, manifesto, permissões, sintaxe, compilação Python, testes e self-test da distribuição congelada.

Novos chats devem sempre confirmar o HEAD e o run mais recente antes de escrever.

## Documentação autoritativa

- [`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md) — estado técnico atual.
- [`docs/ANDROID_MIGRATION_PLAN.md`](docs/ANDROID_MIGRATION_PLAN.md) — roadmap mestre Android.
- [`docs/PARITY_MATRIX.md`](docs/PARITY_MATRIX.md) — contratos Linux 5.23 ↔ Android 6.x.
- [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md) — regras de desenvolvimento.
- [`docs/CI_AUTOMATION.md`](docs/CI_AUTOMATION.md) — ciclo autônomo commit → CI → logs → APK.
- [`docs/ANDROID_HANDOFF_PROMPT.md`](docs/ANDROID_HANDOFF_PROMPT.md) — prompt oficial para continuidade entre chats.
