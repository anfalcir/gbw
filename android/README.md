# GBW Android 6.x

Reimplementação Android nativa do Guitar Backing Wizard, tendo **GBW Linux 5.23.0** como baseline funcional.

## Estado

Versão de desenvolvimento: `6.0.0-alpha1`.

O Android já possui build debug real em CI e o primeiro fluxo DSP completo, **Pitch de Arquivo**, está implementado digitalmente com Rubber Band R3 via NDK/JNI. A linha continua alpha porque a execução no hardware Android alvo, os motores de separação e a paridade funcional completa ainda possuem gates pendentes.

## Stack fixada

- Kotlin `2.4.20`.
- Android Gradle Plugin `9.4.0`.
- Gradle `9.6.0`.
- JDK `17`.
- Jetpack Compose BOM `2026.08.00`.
- `compileSdk 37`, `targetSdk 36`, `minSdk 28`.
- NDK `27.2.12479018`.
- CMake `3.22.1`.
- Storage Access Framework para arquivos do usuário.
- Foreground Service `mediaProcessing` para tarefas longas.
- FFmpegKit mantido como camada de inspeção/codec/conversão.
- Rubber Band Library `4.0.0` fixada no commit `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`, engine R3/Finer, inicialmente `arm64-v8a`.

## Pitch de Arquivo

Fluxo atual:

```text
SAF input
→ inspeção Ideal/Adequado/Ressalva
→ FFmpeg prepara WAV float32 mantendo sample rate/canais
→ Rubber Band R3 offline em duas passagens
→ valida duração/sample rate/canais
→ WAV 32f, WAV24 ou FLAC24
→ FFprobe final
→ gravação no URI de destino somente após validação
```

Inclui:

- +N e -N semitons;
- modo por afinação ou valor manual;
- formantes preservados para Vocal;
- cancelamento e cleanup;
- temporários internos antes do SAF final;
- preflight de espaço temporário;
- execução em Foreground Service com progresso persistido;
- cancelamento pela UI/notificação;
- wake lock limitado e redelivery do Intent após reinício do processo quando suportado pelo Android.

A CI também compila o Rubber Band upstream fixado no host e executa um golden sintético estéreo para **+3/-3 semitons**, frequência e duração.

## Separação

A separação inicial padrão continua **Rápida**, mapeada para Demucs `htdemucs_6s`. A opção **Alta qualidade / BS-RoFormer-SW** permanece prevista.

O próximo gate é provar `htdemucs_6s` real no Android arm64 com os seis stems:

- drums
- bass
- other
- vocals
- guitar
- piano

## Estrutura

```text
android/
├── app/          # aplicação Android
├── scripts/      # validações auxiliares
├── tools/        # smoke/golden tests e utilitários
└── README.md
```

## Validações sem aparelho

```bash
cd android
bash scripts/validate_domain.sh
bash scripts/validate_rubberband_host.sh
```

Resultados esperados:

```text
DOMAIN_SMOKE_OK
RUBBERBAND_HOST_SMOKE_OK
```

## Build CI

Todo commit/push dispara automaticamente **Android CI**. O workflow executa:

1. smoke/paridade de domínio;
2. golden host Rubber Band R3;
3. testes unitários Android;
4. Android Lint;
5. `assembleDebug` com NDK/CMake;
6. verificação da biblioteca `libgbw_rubberband.so` dentro do APK arm64;
7. SHA-256/metadata do APK;
8. publicação do APK debug e relatórios como artifacts.

O artifact debug é instalável para homologações técnicas. Release assinado definitivo terá gate próprio quando arquitetura, DSP, ML e licenças estiverem maduros.

## Desenvolvimento

- referência consolidada: `main`;
- branch oficial Android: `dev/android-6.0`;
- após cada commit, a sessão acompanha a CI autonomamente e corrige qualquer gate vermelho antes de avançar;
- o baseline `linux/` não é modificado como efeito colateral do Android.

Consulte `../docs/CURRENT_STATE.md`, `../docs/ANDROID_MIGRATION_PLAN.md`, `../docs/PARITY_MATRIX.md` e `../docs/ANDROID_HANDOFF_PROMPT.md` antes de alterar decisões estruturais.
