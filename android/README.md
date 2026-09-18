# GBW Android 6.x

Aplicativo Android nativo do Guitar Backing Wizard. `linux/` preserva o baseline GBW Linux 5.23.0 e não é alterado pelo desenvolvimento Android.

## Separação

O Android usa exclusivamente Demucs `htdemucs_6s`.

Contrato:
- float32 estéreo / 44,1 kHz;
- window 343.980 frames;
- core 242.550;
- contexto 50.715 por lado;
- um chunk por vez;
- seis stems: drums, bass, other, vocals, guitar, piano.

Runtime:
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- arm64-v8a.

## Performance física

Baseline vigente: alpha9.1 / 2 threads:
- 1977 s;
- 1714 MiB PSS;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve;
- áudio aprovado.

4 threads foi rejeitado:
- 2460 s;
- 1698 MiB;
- mediana 58,8 s;
- máximo 102,8 s;
- térmico leve.

Candidato atual: `6.0.0-alpha9.3-1t`, versionCode 12:
- commit `4e237e8c1b8f9774ad101b018d744a64b9cb1940`;
- CI #86 SUCCESS;
- APK SHA-256 `dc076f4b88243f65c3ccc8a170a35d32f5160f626f6b83547ae7de7b321a42b7`.

## Pitch

Rubber Band R3 4.0.0 via NDK/JNI, com processamento offline e validação de saída.

## Background

Foreground Service no processo `:media`, estado persistido, cancelamento, cleanup e recuperação controlada.

## Homologação

Certificado estável de teste:
`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Não usar essa identidade em produção.

## CI

A Android CI valida arquitetura, Demucs/modelo, domínio, Rubber Band, unit tests, lint, build NDK, assinatura, runtime nativo, FFmpeg, Manifest, hash e artifacts.

Branch oficial: `dev/android-6.0`.
