# Android Native Third-Party Components

## demucs.cpp

- commit: f1206e9adeea103aef4a636b9e62297cf1f8e34e
- uso: inferência Demucs htdemucs_6s
- licença upstream do código: MIT

## Eigen

- commit: dd8c71e62852b2fe429edb6682ac91fd1c578a26
- uso: álgebra linear dentro do runtime nativo
- licença upstream: primariamente MPL-2.0, com arquivos de terceiros sob licenças compatíveis

## OpenBLAS

- versão: 0.3.34
- commit: e0166008be8e466242aa76b2ff75ce3f0fbf574a
- linkado estaticamente em libgbw_demucs.so
- ABI: arm64-v8a
- capacidade nativa de build: até 4 threads
- política GBW: somente 1/2 threads
- default homologado: 1 thread
- licença: BSD-3-Clause

## Modelo htdemucs_6s

- arquivo: ggml-model-htdemucs-6s-f16.bin
- revisão do mirror: 5f5daffffcf06ad7b27a7285da327e18ea62068a
- bytes: 54,855,129
- SHA-256: 09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856
- não empacotado no APK
- baixado em runtime e validado por tamanho + SHA-256
- origem do mirror: pesos oficiais do projeto Demucs

O licenciamento dos pesos pré-treinados é tratado como blocker separado de release.
Não inferir que a licença MIT do código demucs.cpp/Demucs automaticamente cobre o checkpoint.
Ver docs/ANDROID_THIRD_PARTY.md.

## Política

Nenhum componente nativo novo entra em release sem:
- revisão/version pin;
- licença identificada;
- gate de arquitetura correspondente;
- atualização desta auditoria.
