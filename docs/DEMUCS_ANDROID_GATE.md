# Demucs Android Gate

## Contrato

Único pipeline Android: Demucs htdemucs_6s.

Runtime:
- demucs.cpp f1206e9adeea103aef4a636b9e62297cf1f8e34e;
- Eigen dd8c71e62852b2fe429edb6682ac91fd1c578a26;
- OpenBLAS 0.3.34 / e0166008be8e466242aa76b2ff75ce3f0fbf574a;
- arm64-v8a;
- default 1 thread;
- política 1/2;
- um chunk por vez.

Modelo:
- ggml-model-htdemucs-6s-f16.bin;
- 54,855,129 bytes;
- SHA-256 09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856;
- baixado em runtime e verificado.

Áudio:
- float32 estéreo;
- 44,1 kHz;
- window 343.980;
- core 242.550;
- contexto 50.715 por lado;
- seis stems.

## Threads — evidência física

| Threads | Tempo | PSS | Mediana | Máximo | Térmico | Decisão |
|---:|---:|---:|---:|---:|---|---|
| 1 | 1895 s | 1712 MiB | 48,2 s | 51,0 s | normal | promovido |
| 2 | 1977 s | 1714 MiB | 45,9 s | 87,3 s | leve | fallback interno |
| 4 | 2460 s | 1698 MiB | 58,8 s | 102,8 s | leve | rejeitado |

4 threads é bloqueado pela política de produto/JNI.

## Gate digital

CI valida:
- source/APK Demucs-only;
- commits nativos pinados;
- modelo por tamanho + SHA;
- OpenBLAS estático;
- 1/2 threads;
- exatamente seis stems;
- SR/canais/frames;
- cleanup/cancelamento;
- preflight de espaço.

## Gate físico restante

Na campanha R6:
- spot-check auditivo dos seis stems do RC;
- estabilidade em background/lock screen;
- temperatura/RAM/bateria;
- cancelamento real.

Licenciamento do checkpoint é tratado separadamente em docs/ANDROID_THIRD_PARTY.md e faz parte do gate de produção.
