# CI Automatizada do GBW

## Android CI

Workflow: `.github/workflows/android-ci.yml`

Fluxo:

```text
confirmar HEAD
→ alterar
→ commit/push
→ CI automática
→ inspecionar job/log exato
→ corrigir somente a causa
→ CI verde
→ baixar artifact
→ validar hash/metadados
```

Gates atuais:
1. arquitetura de separação Demucs-only;
2. máscara do small icon da notificação;
3. smoke de domínio;
4. Rubber Band R3 host golden;
5. checkpoint Demucs fixado por bytes/SHA-256/estrutura;
6. unit tests;
7. Android Lint;
8. assembleDebug NDK/CMake;
9. certificado estável de homologação;
10. bibliotecas nativas esperadas e ausência de runtime de separação não utilizado;
11. contrato Demucs no APK;
12. runtime Demucs/OpenBLAS;
13. classes Java críticas do FFmpeg;
14. Manifest do worker `:media` + `dataSync`;
15. build metadata + SHA-256;
16. artifacts APK/relatórios.

Não usar rerun cego. Abrir o log da etapa que falhou e corrigir a causa.

## Assinatura

CI de homologação exige certificado SHA-256:

`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Essa chave é somente para testes. Release público deve usar identidade privada separada.

## Linux

O baseline Linux possui validação própria. Trabalho Android não deve alterar `linux/`. Quando houver escrita no Android, auditar o diff e confirmar ausência de arquivos `linux/`.

## Checkpoint

Candidato Android atual:
- `6.0.0-alpha9.2-4t`;
- commit `75adc0c223291fdb40a91d23cc40aa1134cbe60f`;
- CI #85 / run `35378498398`: SUCCESS;
- APK SHA-256 `79e1f740b02787f27bbf284fdae4dcbe3e738a211d6997facb7ba18fb628a012`.
