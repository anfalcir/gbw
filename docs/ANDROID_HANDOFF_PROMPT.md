# Handoff — GBW Android

Repositório: `anfalcir/gbw`  
Branch: `dev/android-6.0`

## Regras

- confirmar HEAD antes de escrever;
- não modificar `linux/`;
- Separação Android = Demucs `htdemucs_6s`;
- uma variável de performance por experimento;
- CI verde não substitui benchmark físico.

## Baseline físico

`6.0.0-alpha9.1`, versionCode 10, BLAS 2 threads:
- commit `5d889e3ec1e8f1ffc3221dc24556ed0adac6d38d`;
- CI #83 SUCCESS;
- 1977 s;
- 1714 MiB;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve;
- seis stems auditivamente aprovados.

## 4 threads — rejeitado

`6.0.0-alpha9.2-4t`, versionCode 11:
- CI #85 SUCCESS;
- 2460 s;
- 1698 MiB;
- 39 chunks;
- mediana 58,8 s;
- máximo 102,8 s;
- térmico leve.

Foi +24,4% mais lento que 2 threads por ganho de memória irrelevante (~16 MiB). Não promover.

## Candidato em teste — 1 thread

`6.0.0-alpha9.3-1t`, versionCode 12:
- commit `4e237e8c1b8f9774ad101b018d744a64b9cb1940`;
- CI #86 / run `35384394744`: SUCCESS;
- APK 55,470,133 bytes;
- SHA-256 `dc076f4b88243f65c3ccc8a170a35d32f5160f626f6b83547ae7de7b321a42b7`;
- BLAS default 1;
- chunk concurrency 1;
- mesma assinatura de homologação.

Instalar por cima do alpha9.2 sem desinstalar.

## Assinatura

SHA-256:
`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

## Próxima ação

Repetir a mesma música no alpha9.3-1t e comparar contra o baseline 2t:
- 1977 s;
- 1714 MiB;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve.

Se 1t não trouxer ganho útil, fixar 2t como default definitivo e avançar.
