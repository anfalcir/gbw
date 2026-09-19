# Android Native Third-Party Components

## demucs.cpp

- commit: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- uso: Demucs `htdemucs_6s`
- licença upstream: MIT

## Eigen

- commit: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- licença conforme upstream

## OpenBLAS

- versão: 0.3.34
- commit: `e0166008be8e466242aa76b2ff75ce3f0fbf574a`
- estático dentro de `libgbw_demucs.so`
- ABI arm64-v8a
- build nativo continua compilado com capacidade máxima 4 threads
- **política do produto aceita apenas 1/2 threads**
- default homologado: **1 thread**
- 4 threads foi rejeitado fisicamente e bloqueado pelo JNI do GBW
- licença: BSD 3-Clause

## Modelo htdemucs_6s

- arquivo: `ggml-model-htdemucs-6s-f16.bin`
- revisão: `5f5daffffcf06ad7b27a7285da327e18ea62068a`
- bytes: 54,855,129
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`
- não empacotado no APK
- cache privado validado por tamanho + SHA-256
