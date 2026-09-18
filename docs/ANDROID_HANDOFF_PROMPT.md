# Handoff — GBW Android

Repositório: `anfalcir/gbw`  
Branch: `dev/android-6.0`

## Regras

- confirmar HEAD remoto antes de qualquer escrita;
- `linux/` é baseline congelado e não deve ser alterado por trabalho Android;
- Separação Android usa exclusivamente Demucs `htdemucs_6s`;
- não alterar mais de uma variável de performance por experimento;
- não declarar ganho sem benchmark físico comparável.

## Baseline físico atual

`6.0.0-alpha9.1`, versionCode 10, BLAS 2 threads.

- commit funcional: `5d889e3ec1e8f1ffc3221dc24556ed0adac6d38d`;
- CI #83: SUCCESS;
- APK SHA-256: `eb8109b4252f1321ed961860ecd6754f59f6641cd2879ab7f2b07f8e64d2ba6b`;
- 1977 s;
- 1714 MiB PSS;
- 39 chunks;
- mediana 45,9 s;
- máximo 87,3 s;
- térmico leve;
- seis stems auditivamente aprovados.

Alpha8 para referência:
- 2165 s;
- 2180 MiB;
- 39 chunks;
- mediana 53,5 s;
- máximo 68,4 s;
- térmico leve;
- áudio aprovado.

## Candidato em teste

`6.0.0-alpha9.2-4t`, versionCode 11.

- commit funcional: `75adc0c223291fdb40a91d23cc40aa1134cbe60f`;
- CI #85 / run `35378498398`: SUCCESS;
- APK: 55,470,133 bytes;
- SHA-256: `79e1f740b02787f27bbf284fdae4dcbe3e738a211d6997facb7ba18fb628a012`;
- mesmo certificado de homologação;
- BLAS default = 4 threads;
- chunk concurrency = 1.

Única variável funcional em relação ao alpha9.1: 2 → 4 threads BLAS.

## Assinatura de homologação

Fingerprint:
`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Alpha9.2 deve instalar por cima do alpha9.1 sem desinstalar/limpar dados.

## Próxima ação

No Samsung Galaxy Tab A11+, instalar alpha9.2-4t por cima do alpha9.1 e repetir a mesma música.

Capturar:
- SUCCESS/falha;
- tempo;
- PSS;
- chunks;
- mediana;
- máximo;
- térmico;
- qualidade dos seis stems.

Se 4 threads trouxer ganho útil sem regressões, promover. Caso contrário, voltar default para 2 threads.

Depois da decisão de threads, continuar o roadmap em `docs/ANDROID_MIGRATION_PLAN.md`.
