# Demucs Android Gate

## Contrato

O GBW Android possui um único pipeline de separação: Demucs `htdemucs_6s`.

Modelo:
- `ggml-model-htdemucs-6s-f16.bin`;
- bytes `54,855,129`;
- SHA-256 `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- revisão `5f5daffffcf06ad7b27a7285da327e18ea62068a`.

Runtime:
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- arm64-v8a;
- um chunk por vez.

Áudio:
- float32 estéreo;
- 44,1 kHz;
- window 343.980 frames;
- core 242.550;
- contexto 50.715 por lado;
- seis stems: drums, bass, other, vocals, guitar, piano.

## Gates digitais

A CI exige:
- checkpoint válido;
- runtime nativo presente;
- OpenBLAS estaticamente ligado ao runtime Demucs;
- ausência de runtimes de separação não utilizados;
- identidade nativa e janela esperadas;
- unit tests de contrato/chunking;
- APK arm64 válido;
- assinatura de homologação esperada.

## Gate físico atual

Baseline alpha9.1/2t:
- 1977 s;
- 1714 MiB PSS;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve;
- seis stems auditivamente aprovados.

Candidato alpha9.2-4t:
- CI #85 verde;
- mesma arquitetura e chunking;
- única variável funcional: threads BLAS default 2 → 4.

A decisão 2t vs 4t depende do A/B físico com a mesma música.
