# GBW Android 6.x

Reimplementação Android nativa do Guitar Backing Wizard, tendo **GBW Linux 5.23.0** como baseline funcional.

## Estado

Versão de desenvolvimento: `6.0.0-alpha1`.

O Android já possui dois fluxos DSP/ML centrais implementados digitalmente:

- **Pitch de Arquivo** com Rubber Band R3 via NDK/JNI;
- **Separação Rápida** com Demucs `htdemucs_6s` via C++17/NDK/JNI.

A linha continua alpha porque faltam BS-RoFormer-SW, workflow completo, projetos/backup e homologação física final em hardware Android arm64.

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
- FFmpegKit como camada de inspeção/codec/conversão.
- Rubber Band Library `4.0.0` @ `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`.
- `demucs.cpp` @ `f1206e9adeea103aef4a636b9e62297cf1f8e34e`.
- Eigen @ `dd8c71e62852b2fe429edb6682ac91fd1c578a26`.
- ABI inicial `arm64-v8a`.

## Pitch de Arquivo

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

Inclui +N/-N semitons, afinação/manual, formantes para Vocal, cancelamento, cleanup, preflight de espaço, progresso persistido, wake lock limitado e redelivery seguro.

## Separação Rápida — Demucs `htdemucs_6s`

O caminho Rápida está ligado à tela de Separação e ao Foreground Service real.

Checkpoint externo:

- arquivo: `ggml-model-htdemucs-6s-f16.bin`;
- revisão: `5f5daffffcf06ad7b27a7285da327e18ea62068a`;
- tamanho: `54,855,129` bytes;
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- fora do APK;
- download para `.part`, verificação e promoção atômica para cache privado.

Contrato de áudio:

- entrada preparada em float32 estéreo a 44,1 kHz;
- janela nativa: `343,980` frames / 7,8 s;
- core gravado: `242,550` frames / 5,5 s;
- contexto: `50,715` frames / 1,15 s por lado;
- saída: seis WAVs float32 estéreo, com sample rate/canais/frames validados.

Stems, nesta ordem:

1. `drums`
2. `bass`
3. `other`
4. `vocals`
5. `guitar`
6. `piano`

O pipeline mede tempo total e pico PSS observado, remove saídas parciais em erro/cancelamento e rejeita modelo de quatro fontes no JNI.

## Alta qualidade

**BS-RoFormer-SW** continua sendo o motor planejado para Alta qualidade e é o próximo gate principal. A UI não substitui silenciosamente essa opção por Demucs enquanto o motor não estiver implementado.

## Validações sem aparelho

```bash
cd android
bash scripts/validate_domain.sh
bash scripts/validate_rubberband_host.sh
bash scripts/validate_demucs_model.sh
```

Resultados esperados incluem:

```text
DOMAIN_SMOKE_OK
RUBBERBAND_HOST_SMOKE_OK
DEMUCS_MODEL_CHECKPOINT_OK
```

O gate Demucs baixa ou reutiliza cache do checkpoint somente após validar tamanho, SHA-256, magic `dmc6` e tensores esperados.

## Build CI

Todo commit/push dispara automaticamente **Android CI**. O workflow executa:

1. smoke/paridade de domínio;
2. golden host Rubber Band R3;
3. validação do checkpoint real `htdemucs_6s`;
4. testes unitários Android;
5. Android Lint;
6. `assembleDebug` com NDK/CMake;
7. verificação de `libgbw_rubberband.so` e `libgbw_demucs.so` no APK arm64;
8. SHA-256/metadata do APK e pins de DSP/ML;
9. publicação do APK debug e relatórios como artifacts.

## Limite da homologação digital

Ainda dependem de aparelho real:

- inferência completa Demucs em música real;
- qualidade auditiva dos seis stems e seams entre chunks;
- RAM/PSS, tempo, thermal throttling e bateria reais;
- Home/outro app/tela bloqueada por períodos longos;
- providers SAF reais;
- percepção auditiva final do Rubber Band R3.

## Desenvolvimento

- referência consolidada: `main`;
- branch oficial Android: `dev/android-6.0`;
- acompanhar CI de todo commit relevante até verde;
- `linux/` não é modificado como efeito colateral do Android.

Consulte `../docs/CURRENT_STATE.md`, `../docs/ANDROID_MIGRATION_PLAN.md`, `../docs/PARITY_MATRIX.md` e `../docs/ANDROID_HANDOFF_PROMPT.md` antes de alterar decisões estruturais.
