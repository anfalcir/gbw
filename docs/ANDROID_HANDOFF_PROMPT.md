# Handoff — GBW Android

Repositório: `anfalcir/gbw`  
Branch: `dev/android-6.0`

## Regra crítica

`linux/` é baseline congelado. Não modificar como efeito colateral do Android.

## Checkpoint atual

- versão: **6.0.0-alpha9.1**
- versionCode: **10**
- HEAD funcional: `5d889e3ec1e8f1ffc3221dc24556ed0adac6d38d`
- Android CI: **#83**
- run ID: `35373520877`
- resultado: **SUCCESS**
- APK: `55.470.133` bytes
- SHA-256: `eb8109b4252f1321ed961860ecd6754f59f6641cd2879ab7f2b07f8e64d2ba6b`
- certificado homologação SHA-256: `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

## Assinatura

Alpha8 e o primeiro alpha9 foram produzidos com debug keys efêmeras diferentes. Android bloqueia atualização com assinatura diferente.

A partir de alpha9.1:
- build debug/homologação usa `android/app/gbw-homologation.p12`;
- a chave é pública e exclusivamente de teste;
- CI valida o fingerprint do APK com `apksigner`;
- não reutilizar essa chave em release/produção;
- detalhes em `docs/ANDROID_HOMOLOGATION_SIGNING.md`.

Para um dispositivo que ainda tem alpha8: exportar dados/stems necessários, desinstalar alpha8 e instalar alpha9.1. Essa é a migração única. Depois, builds de homologação futuros com o mesmo certificado podem atualizar por cima.

## Separação Android

Único motor: Demucs `htdemucs_6s`.

Runtime:
- demucs.cpp `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- OpenBLAS 0.3.34 `e0166008be8e466242aa76b2ff75ce3f0fbf574a`;
- ARM64;
- um chunk por vez;
- BLAS 1/2/4 threads; default 2.

Chunking preservado:
- window 343.980 frames;
- core 242.550;
- contexto 50.715 por lado.

## Baseline físico alpha8

- 2165 s;
- 2180 MiB PSS;
- 39 chunks;
- mediana 53,5 s;
- máximo 68,4 s;
- thermal leve;
- seis stems ouvidos e aprovados;
- sem cortes/clicks/seams perceptíveis.

## Próximo gate físico

Instalar alpha9.1 após a migração única de assinatura e repetir a mesma música. Registrar tempo, PSS, chunks, mediana, máximo, thermal e qualidade dos seis stems. Conferir também exportação, Home/tela bloqueada e o novo ícone de notificação.

Não declarar ganho de performance antes dessa comparação.
