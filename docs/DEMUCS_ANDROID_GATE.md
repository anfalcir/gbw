# Gate Técnico — Demucs `htdemucs_6s` no Android

**Data:** 2026-09-17  
**Branch:** `dev/android-6.0`  
**Baseline funcional:** GBW Linux 5.23.0

## Objetivo

Provar uma implementação Android arm64 real da Separação Rápida sem Python/Termux externo, preservando o contrato de seis stems do `htdemucs_6s`, mantendo o modelo grande fora do APK e limitando memória por unidade de inferência.

## Runtime fixado

- `demucs.cpp`: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- Eigen: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- C++17 / NDK / JNI
- ABI inicial: `arm64-v8a`
- biblioteca: `libgbw_demucs.so`

## Modelo fixado

- contrato: `htdemucs_6s`
- arquivo: `ggml-model-htdemucs-6s-f16.bin`
- provider: Hugging Face `Retrobear/demucs.cpp`
- revisão: `5f5daffffcf06ad7b27a7285da327e18ea62068a`
- tamanho: `54,855,129` bytes
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`
- serialization magic: `dmc6` / `0x646d6336`

O modelo não é embutido no APK. O runtime usa download privado com `.part`, validação e promoção atômica.

## Contrato JNI

A camada JNI:

- exige input estéreo interleaved float32;
- rejeita modelo de quatro fontes;
- chama a inferência real `demucs.cpp`;
- exige saída `[6, 2, frames]`;
- rejeita PCM não finito;
- mantém cancelamento nativo cooperativo;
- expõe identidade de runtime/pins.

## Contrato de áudio

O modelo exige 44,1 kHz; portanto esse resampling é uma exigência explícita do motor.

- entrada: float32 estéreo 44.100 Hz;
- janela: `343,980` frames = 7,8 s;
- core: `242,550` frames = 5,5 s;
- contexto: `50,715` frames = 1,15 s por lado;
- zero-padding apenas nas bordas externas;
- somente o core é persistido por passagem.

A música completa nunca é enviada como um único tensor JNI.

## Stems

Ordem fixa:

1. `drums`
2. `bass`
3. `other`
4. `vocals`
5. `guitar`
6. `piano`

Cada stem é WAV float32 estéreo 44,1 kHz e é validado para existência, sample rate, canais e número de frames idêntico ao input preparado.

## Resiliência e métricas

- Foreground Service `mediaProcessing` possui o job;
- progresso em `JobStore`;
- cancelamento UI/notificação + coroutine/native;
- temporários isolados por `jobId`;
- saída parcial apagada em falha/cancelamento;
- modelo válido permanece em cache privado;
- execução mede elapsed time e pico PSS observado.

## Evidência digital consolidada

Checkpoint de UI/pipeline:

- commit `724a3604cf42e7a610ee8c3e19076075444ca508`;
- Android CI `#39`: **SUCCESS**.

Gate de checkpoint externo:

- commit `f4089aa5c338154f07cdda15d432f4bbe30dd837`;
- Android CI run `#41` / ID `35284207021`: **SUCCESS**;
- `Demucs model checkpoint smoke`: **SUCCESS**;
- unit tests: **SUCCESS**;
- Android Lint: **SUCCESS**;
- `assembleDebug`: **SUCCESS**;
- verificação das bibliotecas nativas no APK: **SUCCESS**;
- artifact: `GBW-Android-debug-41`;
- artifact digest: `sha256:fbf8e1310e2c8c2c287abad08cb375ebed3d76c3ac714ebfaf9f577df09baaf8`.

## O que este gate NÃO prova

Somente hardware Android real pode fechar:

- qualidade auditiva dos seis stems;
- ausência perceptível de seams nas fronteiras dos cores;
- tempo de inferência de música real;
- pico PSS/RAM real em diferentes aparelhos;
- thermal throttling e bateria;
- comportamento prolongado com Home/outro app/tela bloqueada;
- providers SAF específicos.

Estado correto: **implementação digital concluída / homologação física pendente**.

## Próximo gate

Implementar **BS-RoFormer-SW / Alta qualidade** com o mesmo rigor de identidade de runtime/modelo, cache verificado, segmentação móvel, cancelamento, métricas e CI antes de ativar a opção na UI.
