# GBW — Estado Atual

**Data:** 2026-09-18  
**Repositório:** `anfalcir/gbw`  
**Branch Android:** `dev/android-6.0`

## Regras de preservação

- `linux/` é o baseline congelado **GBW Linux 5.23.0**.
- O desenvolvimento Android não pode modificar `linux/` como efeito colateral.
- A separação no Android usa **exclusivamente Demucs `htdemucs_6s`**.

## Baseline físico homologado — alpha9.1

Versão: `6.0.0-alpha9.1`  
versionCode: `10`  
Commit funcional: `5d889e3ec1e8f1ffc3221dc24556ed0adac6d38d`  
CI: #83 / run `35373520877` — **SUCCESS**

APK:
- bytes: `55,470,133`;
- SHA-256: `eb8109b4252f1321ed961860ecd6754f59f6641cd2879ab7f2b07f8e64d2ba6b`;
- certificado de homologação SHA-256: `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`.

Homologação física no Samsung Galaxy Tab A11+:
- resultado: **SUCCESS 100%**;
- 6 stems;
- 44,1 kHz;
- duração da fonte: 211,9 s;
- tempo total: **1977 s**;
- PSS observado: **1714 MiB**;
- chunks: **39**;
- mediana por chunk: **45,9 s**;
- máximo por chunk: **87,3 s**;
- térmico: **leve**;
- áudio dos seis stems: **aprovado**, sem cortes, falhas, clicks ou seams percebidos.

Comparação contra alpha8:
- tempo: 2165 → 1977 s (**-188 s / -8,7%**);
- PSS: 2180 → 1714 MiB (**-466 MiB / -21,4%**);
- mediana: 53,5 → 45,9 s (**-14,2%**);
- máximo: 68,4 → 87,3 s; acompanhar outliers;
- térmico: leve → leve;
- qualidade: aprovada em ambos.

O alpha9.1 com **BLAS 2 threads** é o baseline físico vigente.

## Candidato A/B — alpha9.2-4t

Versão: `6.0.0-alpha9.2-4t`  
versionCode: `11`  
Commit funcional: `75adc0c223291fdb40a91d23cc40aa1134cbe60f`  
CI: #85 / run `35378498398` — **SUCCESS**

APK:
- bytes: `55,470,133`;
- SHA-256: `79e1f740b02787f27bbf284fdae4dcbe3e738a211d6997facb7ba18fb628a012`;
- mesmo certificado estável de homologação.

Variável experimental:
- alpha9.1: BLAS default = 2 threads;
- alpha9.2-4t: BLAS default = 4 threads.

Todo o restante foi preservado:
- um chunk por vez;
- mesma janela/core/contexto;
- mesmo modelo e pesos;
- mesmo `demucs.cpp`;
- mesmo Eigen;
- mesmo OpenBLAS;
- mesmo pipeline de WAV;
- mesma UI;
- mesma instrumentação.

## Runtime Demucs Android

- modelo: `htdemucs_6s`;
- checkpoint: `ggml-model-htdemucs-6s-f16.bin`;
- bytes: `54,855,129`;
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- source revision: `5f5daffffcf06ad7b27a7285da327e18ea62068a`;
- demucs.cpp: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34: `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- ABI: `arm64-v8a`;
- chunk parallelism: 1;
- threads BLAS suportadas internamente: 1, 2, 4;
- janela: 343.980 frames;
- core: 242.550 frames;
- contexto: 50.715 frames por lado;
- PSS periódico: 4 s, preservando amostras antes/depois da inferência nativa.

Stems fixos:
1. drums
2. bass
3. other
4. vocals
5. guitar
6. piano

## Assinatura de homologação

A partir de alpha9.1, builds de homologação usam certificado estável de teste:

`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

A CI valida o fingerprint do APK final. Essa chave é exclusivamente de homologação e **não deve ser usada em produção**.

## Próximo gate físico

Instalar alpha9.2-4t **por cima do alpha9.1**, sem limpar dados, e repetir exatamente a mesma música.

Registrar:
- sucesso/falha;
- tempo total;
- PSS observado;
- 39 chunks esperados;
- mediana;
- máximo;
- estado térmico;
- qualidade auditiva dos seis stems.

Decisão:
- manter 4 threads somente se houver ganho útil sem regressão de estabilidade, memória, térmico ou áudio;
- caso contrário, restaurar 2 threads como default.

Não alterar chunking/contexto antes de concluir esse A/B.
