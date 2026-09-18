# GBW Android 6.x

Aplicativo Android nativo do Guitar Backing Wizard. O **GBW Linux 5.23.0** permanece congelado em `linux/` como baseline funcional.

## Estado atual

Baseline físico homologado:
- `6.0.0-alpha9.1`, versionCode 10;
- BLAS 2 threads;
- CI #83: SUCCESS;
- 1977 s / 1714 MiB PSS / 39 chunks;
- mediana 45,9 s / máximo 87,3 s / térmico leve;
- seis stems auditivamente aprovados.

Candidato de benchmark:
- `6.0.0-alpha9.2-4t`, versionCode 11;
- commit `75adc0c223291fdb40a91d23cc40aa1134cbe60f`;
- CI #85 / run `35378498398`: **SUCCESS**;
- SHA-256 `79e1f740b02787f27bbf284fdae4dcbe3e738a211d6997facb7ba18fb628a012`;
- única variável funcional: BLAS default 2 → 4 threads.

## Separação

No Android, **Separação = Demucs `htdemucs_6s`**. Não existe seleção de motor.

Fluxo:

```text
SAF
→ cópia privada/decodificação controlada
→ WAV float32 estéreo 44,1 kHz
→ Demucs htdemucs_6s
→ 39 chunks para a música de referência
→ seis WAVs float32 estéreo
→ validação estrutural
→ preview
→ exportação via SAF
```

Stems:
- drums
- bass
- other
- vocals
- guitar
- piano

Contrato de chunking:
- window: 343.980 frames;
- core: 242.550;
- contexto: 50.715 por lado;
- um chunk por vez.

Runtime:
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- ARM64.

## Pitch de Arquivo

Rubber Band R3 4.0.0 via NDK/JNI, com processamento offline em duas passagens, preservação de canais/sample rate conforme contrato, cancelamento e validação antes da promoção da saída.

## Background

Tarefas longas rodam em Foreground Service no processo `:media`, com estado persistido, wake lock limitado, cancelamento, cleanup e reconciliação de término anormal.

## Assinatura de homologação

APKs de homologação usam certificado estável de teste:

`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Isso permite atualização por cima entre candidatos. A chave de homologação **não é chave de produção**.

## CI

`.github/workflows/android-ci.yml` valida:
- arquitetura Demucs-only;
- ícone de notificação;
- domínio;
- Rubber Band host golden;
- checkpoint Demucs;
- unit tests;
- lint;
- build NDK/Android;
- assinatura;
- conteúdo nativo do APK;
- runtime OpenBLAS/Demucs;
- classes FFmpeg;
- manifest do worker;
- metadata/hash;
- artifacts.

## Desenvolvimento

- branch: `dev/android-6.0`;
- confirmar HEAD remoto antes de escrever;
- não modificar `linux/` durante trabalho Android;
- não declarar ganho de performance sem benchmark físico comparável.

Veja `../docs/CURRENT_STATE.md` e `../docs/ANDROID_HANDOFF_PROMPT.md`.
