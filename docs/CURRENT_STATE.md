# GBW — Estado Atual

**Data:** 2026-09-18  
**Repositório:** `anfalcir/gbw`  
**Branch Android:** `dev/android-6.0`

## Regras de preservação

- `linux/` é o baseline congelado GBW Linux 5.23.0.
- O desenvolvimento Android não modifica `linux/` como efeito colateral.
- Separação Android usa exclusivamente Demucs `htdemucs_6s`.
- Otimizações de performance são testadas com uma variável por candidato.

## Baseline físico vigente — alpha9.1 / BLAS 2 threads

Versão: `6.0.0-alpha9.1`  
versionCode: `10`  
Commit funcional: `5d889e3ec1e8f1ffc3221dc24556ed0adac6d38d`  
CI #83 / run `35373520877`: **SUCCESS**

APK:
- 55,470,133 bytes;
- SHA-256 `eb8109b4252f1321ed961860ecd6754f59f6641cd2879ab7f2b07f8e64d2ba6b`;
- certificado de homologação `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`.

Homologação física no Samsung Galaxy Tab A11+:
- SUCCESS 100%;
- 6 stems / 44,1 kHz / fonte 211,9 s;
- tempo: **1977 s**;
- PSS observado: **1714 MiB**;
- 39 chunks;
- mediana: **45,9 s**;
- máximo: **87,3 s**;
- térmico: **leve**;
- seis stems auditivamente aprovados.

## Histórico controlado de threads

### Alpha8 — referência pré-OpenBLAS
- 2165 s;
- 2180 MiB;
- 39 chunks;
- mediana 53,5 s;
- máximo 68,4 s;
- térmico leve;
- áudio aprovado.

### Alpha9.1 — 2 threads — BASELINE
- 1977 s;
- 1714 MiB;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve;
- áudio aprovado.

Ganho vs alpha8:
- tempo: -188 s / **-8,7%**;
- PSS: -466 MiB / **-21,4%**;
- mediana: **-14,2%**.

### Alpha9.2-4t — 4 threads — REJEITADO
CI #85 / run `35378498398`: SUCCESS digital.

Resultado físico:
- SUCCESS 100%;
- tempo: **2460 s**;
- PSS: **1698 MiB**;
- 39 chunks;
- mediana: **58,8 s**;
- máximo: **102,8 s**;
- térmico: **leve**.

Comparação com 2 threads:
- +483 s / **+24,4% mais lento**;
- PSS apenas 16 MiB menor / ~0,9%;
- mediana **+28,1%**;
- máximo **+17,8%**;
- térmico igual.

Conclusão: 4 threads não compensa no hardware-alvo e está rejeitado como default.

## Candidato atual — alpha9.3-1t

Versão: `6.0.0-alpha9.3-1t`  
versionCode: `12`  
Commit funcional: `4e237e8c1b8f9774ad101b018d744a64b9cb1940`  
CI #86 / run `35384394744`: **SUCCESS**

APK:
- 55,470,133 bytes;
- SHA-256 `dc076f4b88243f65c3ccc8a170a35d32f5160f626f6b83547ae7de7b321a42b7`;
- mesmo certificado estável de homologação.

Única variável funcional frente ao baseline:
- BLAS default: 2 → 1 thread.

Preservados:
- chunk concurrency = 1;
- mesmo modelo/checkpoint;
- mesmo demucs.cpp/Eigen/OpenBLAS;
- mesma janela/core/contexto;
- mesmo pipeline WAV;
- mesma UI/instrumentação.

## Runtime Demucs

- `htdemucs_6s`;
- modelo: `ggml-model-htdemucs-6s-f16.bin`;
- modelo SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- arm64-v8a;
- window 343.980 frames;
- core 242.550;
- contexto 50.715 por lado;
- seis stems: drums, bass, other, vocals, guitar, piano.

## Próximo gate físico

Instalar alpha9.3-1t por cima do alpha9.2-4t, sem limpar dados, e executar exatamente a mesma música.

Capturar:
- sucesso/falha;
- tempo;
- PSS;
- chunks;
- mediana;
- máximo;
- térmico;
- qualidade auditiva se o candidato tiver performance competitiva.

Critério:
- se 1 thread não superar de forma útil o baseline de 1977 s sem regressões, restaurar/promover 2 threads como configuração definitiva;
- se 1 thread superar 2 threads, confirmar estabilidade/áudio e então promovê-lo;
- só depois encerrar o gate de threads e avançar para a próxima classe de otimização.
