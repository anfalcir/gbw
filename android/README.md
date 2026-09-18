# GBW Android 6.x

Aplicativo Android nativo do Guitar Backing Wizard. O baseline GBW Linux 5.23.0 permanece congelado em `linux/`.

## Checkpoint

`6.0.0-alpha10` / versionCode 13

- commit: `d93d45c11dae72065ba450b83927d5d8bc39ed26`
- CI #92: SUCCESS
- APK SHA-256: `1e6a692c3eb1b73501219809d371acb3747451dd6bb1a40a53e784eba1574ea4`

## Separação

Android usa exclusivamente Demucs `htdemucs_6s`.

Contrato:
- float32 estéreo / 44,1 kHz;
- window 343.980;
- core 242.550;
- contexto 50.715 por lado;
- um chunk por vez;
- seis stems.

Runtime:
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- BLAS default 1 thread;
- política interna 1/2;
- arm64-v8a.

## Benchmark físico

1 thread venceu no Samsung Galaxy Tab A11+:
- 1895 s;
- 1712 MiB PSS;
- 39 chunks;
- mediana 48,2 s;
- máximo 51,0 s;
- térmico normal.

2 threads: 1977 s / 1714 MiB.  
4 threads: 2460 s / 1698 MiB — rejeitado.

## Fonte

- arquivo local via SAF;
- inspeção/qualidade;
- Pesquisa Online;
- Bandcamp discovery;
- ranking portado do Linux 5.23;
- profundidade Robusta/Máxima;
- URL manual;
- providers isolados de falha.

A aquisição automática de mídia não está acoplada à descoberta.

## Pitch

Rubber Band R3 4.0.0 via NDK/JNI.

## Background

Foreground Service `:media`, persistência, cancelamento, cleanup e recuperação controlada.

## Assinatura de homologação

SHA-256:
`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Não usar esta chave em produção.
