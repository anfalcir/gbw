# Android Native Third-Party Components

Somente componentes atualmente usados pelo GBW Android são documentados aqui.

## Rubber Band Library

- versão: 4.0.0
- commit: `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`
- uso: Pitch de Arquivo via NDK/JNI
- gate de distribuição: revisar GPL v2-or-later ou licença comercial antes do RC público

## demucs.cpp

- commit: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- uso: inferência Demucs `htdemucs_6s`
- licença upstream: MIT

## Eigen

- commit: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- uso: álgebra/tensores do runtime Demucs
- licença: família MPL-2.0 conforme upstream

## OpenBLAS

- versão: 0.3.34
- commit: `e0166008be8e466242aa76b2ff75ce3f0fbf574a`
- uso: BLAS interno do runtime Demucs
- build Android: estático dentro de `libgbw_demucs.so`
- ABI: arm64-v8a
- máximo compilado: 4 threads
- licença: BSD 3-Clause

## Modelo htdemucs_6s

- arquivo: `ggml-model-htdemucs-6s-f16.bin`
- revisão: `5f5daffffcf06ad7b27a7285da327e18ea62068a`
- bytes: `54,855,129`
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`
- o modelo não é empacotado no APK
- download/cache privado exige validação de tamanho e SHA-256 antes de uso

Nenhum outro runtime de separação faz parte do produto Android.
