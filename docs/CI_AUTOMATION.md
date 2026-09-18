# CI Automatizada do GBW

## Android CI

Workflow: `.github/workflows/android-ci.yml`

Gates:
1. arquitetura Demucs-only;
2. small icon;
3. domínio;
4. Rubber Band host golden;
5. checkpoint Demucs;
6. unit tests;
7. lint;
8. assembleDebug NDK/CMake;
9. certificado de homologação;
10. conteúdo nativo do APK;
11. contrato Demucs-only no APK;
12. Demucs/OpenBLAS;
13. FFmpeg Java runtime;
14. Manifest `:media`;
15. metadata + SHA-256;
16. artifacts.

Regra: não usar rerun cego. Inspecionar a etapa que falhou.

## Política Demucs atual

- OpenBLAS 0.3.34;
- default: 1 thread;
- política interna aceita: 1/2;
- 4 threads foi rejeitado fisicamente e também removido do gate JNI;
- um chunk por vez.

## Assinatura

Homologação:
`6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

Produção deverá usar identidade privada separada.

## Checkpoint

- versão: `6.0.0-alpha10`;
- commit: `d93d45c11dae72065ba450b83927d5d8bc39ed26`;
- CI #92 / run `35389966284`: SUCCESS;
- APK SHA-256: `1e6a692c3eb1b73501219809d371acb3747451dd6bb1a40a53e784eba1574ea4`.
