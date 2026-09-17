# GBW Android 6.x

Reimplementação Android nativa do Guitar Backing Wizard, tendo **GBW Linux 5.23.0** como baseline funcional.

## Estado

Versão de desenvolvimento: `6.0.0-alpha1`.

A árvore Android já contém domínio portado e infraestrutura inicial, mas ainda é **pré-homologação**: os gates de DSP/ML nativo e o build completo em CI precisam ser fechados antes de tratá-la como APK funcional equivalente ao desktop.

## Stack fixada

- Kotlin `2.4.20`.
- Android Gradle Plugin `9.4.0`.
- Gradle `9.6.0`.
- JDK `17`.
- Jetpack Compose BOM `2026.08.00`.
- `compileSdk 37`, `targetSdk 36`, `minSdk 28`.
- Storage Access Framework para arquivos do usuário.
- Foreground Service `mediaProcessing` para tarefas longas.
- FFmpegKit mantido temporariamente para inspeção/codec enquanto a camada de mídia é consolidada.
- Rubber Band R3 planejado via NDK/JNI.

## Decisão Android

A separação inicial padrão é **Rápida**, mapeada para Demucs `htdemucs_6s`. A opção **Alta qualidade / BS-RoFormer** permanece disponível.

## Estrutura

```text
android/
├── app/          # aplicação Android
├── scripts/      # validações auxiliares
├── tools/        # smoke tests / utilitários de desenvolvimento
└── README.md
```

## Validação de domínio sem SDK

```bash
cd android
bash scripts/validate_domain.sh
```

Resultado esperado: `DOMAIN_SMOKE_OK`.

## Build CI

Todo commit/push dispara automaticamente **Android CI**. O workflow executa:

1. testes unitários;
2. Android Lint;
3. `assembleDebug`;
4. SHA-256 do APK;
5. publicação do APK debug e relatórios como artifacts.

O artifact debug é instalável diretamente para homologações técnicas. Release assinado definitivo terá gate próprio quando a arquitetura/DSP estiverem maduros.

Consulte `../docs/CURRENT_STATE.md` e `../docs/ANDROID_MIGRATION_PLAN.md` antes de alterar decisões estruturais.
