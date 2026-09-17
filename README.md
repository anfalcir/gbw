# Guitar Backing Wizard (GBW)

Repositório principal do **Guitar Backing Wizard**.

Este repositório preserva duas linhas do produto em pastas separadas:

- `linux/v5.23/` — **baseline funcional congelado** do GBW Linux 5.23.0.
- `android/` — reimplementação Android nativa em desenvolvimento, linha 6.x.

## Baseline autoritativo

O baseline de referência para a migração Android é:

- **GBW Linux:** `5.23.0`
- **Arquivo:** `Guitar_Backing_Wizard_v5.23_Linux.zip`
- **SHA-256:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

A versão Linux 5.23 deve ser tratada como referência funcional, comportamental e de qualidade até que uma nova baseline seja explicitamente aprovada.

## Estrutura

```text
.
├── linux/
│   └── v5.23/               # baseline Linux completo
├── android/                 # GBW Android 6.x em desenvolvimento
├── docs/
│   ├── ANDROID_MIGRATION_PLAN.md
│   ├── CURRENT_STATE.md
│   └── PARITY_MATRIX.md
└── .github/workflows/
    └── android-ci.yml        # CI manual-only
```

## Android

A implementação Android é uma reimplementação nativa, não um empacotamento do Tkinter/Python.

Diretrizes principais:

- Kotlin + Jetpack Compose.
- Android Gradle Plugin 9.4 / API 37.
- tarefas longas independentes da Activity, com Foreground Service;
- **Separação Rápida / Demucs** como padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção;
- Pitch de Arquivo como primeiro fluxo DSP de paridade;
- projetos e backups projetados para interoperabilidade Linux ↔ Android;
- nenhuma dependência de Termux ou Python externo no produto final.

Consulte `docs/ANDROID_MIGRATION_PLAN.md` para o roadmap autoritativo.

## Política de desenvolvimento

- Mudanças Android devem preservar contratos da v5.23 ou documentar explicitamente divergências justificadas.
- Processos pesados nunca devem depender da tela permanecer aberta.
- Dependências e modelos devem ter versão/hash fixados quando aplicável.
- CI Android é **manual-only** (`workflow_dispatch`) para evitar consumo involuntário.
- Nenhum segredo, keystore privado, projeto de usuário, música ou arquivo de trabalho pessoal deve ser versionado.

## Estado atual

Consulte `docs/CURRENT_STATE.md`.
