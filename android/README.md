# GBW Android 6.x

Reimplementação Android nativa do Guitar Backing Wizard, tendo **GBW Linux 5.23.0** como baseline funcional.

## Estado

Versão de desenvolvimento: `6.0.0-alpha1`.

O primeiro checkpoint Android já possui **CI verde com APK debug gerado automaticamente**. A linha ainda é pré-homologação porque os gates de DSP/ML nativo e a paridade funcional completa com o desktop seguem em desenvolvimento.

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

1. smoke/paridade de domínio;
2. testes unitários Android;
3. Android Lint;
4. `assembleDebug`;
5. SHA-256/metadata do APK;
6. publicação do APK debug e relatórios como artifacts.

O artifact debug é instalável diretamente para homologações técnicas. Release assinado definitivo terá gate próprio quando arquitetura, DSP e ML estiverem maduros.

## Desenvolvimento

- referência consolidada: `main`;
- branch oficial Android: `dev/android-6.0`;
- após cada commit, a sessão deve acompanhar a CI autonomamente e corrigir qualquer gate vermelho antes de avançar.

Consulte `../docs/CURRENT_STATE.md`, `../docs/ANDROID_MIGRATION_PLAN.md` e `../docs/ANDROID_HANDOFF_PROMPT.md` antes de alterar decisões estruturais.
