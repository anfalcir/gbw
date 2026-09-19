# CI Automatizada do GBW

## Android CI

Workflow: .github/workflows/android-ci.yml

Trigger:
- push;
- pull_request;
- workflow_dispatch como contingência.

Gates atuais:
1. arquitetura Demucs-only em source;
2. máscara do ícone de notificação;
3. smoke/paridade de domínio;
4. workflow estritamente project-scoped;
5. arquitetura original-only;
6. UX de release / ausência de placeholders;
7. hardening final;
8. contrato do pipeline de produção;
9. checkpoint Demucs;
10. testes unitários;
11. Android Lint;
12. assembleDebug NDK/CMake;
13. certificado estável de homologação;
14. conteúdo nativo do APK;
15. Demucs-only no APK;
16. Demucs/OpenBLAS;
17. FFmpegKit runtime;
18. manifest do processo :media;
19. metadata + SHA-256;
20. artifacts APK e relatórios.

Regra: em falha, ler o job/log antes de alterar código. Não usar continue-on-error em gate obrigatório.

## Production Release

Workflow: .github/workflows/android-release.yml

Trigger: somente workflow_dispatch.

O job de produção exige:
- LICENSE na raiz;
- docs/ANDROID_THIRD_PARTY.md;
- keystore privado materializado apenas no runner;
- secrets GBW_RELEASE_KEYSTORE_B64, GBW_RELEASE_STORE_PASSWORD, GBW_RELEASE_KEY_ALIAS, GBW_RELEASE_KEY_PASSWORD e GBW_RELEASE_CERT_SHA256;
- certificado de produção diferente do certificado público de homologação;
- unit/lint/gates completos;
- assembleRelease;
- verificação de assinatura, arquitetura e runtime;
- SHA-256/build-info do APK final.

O workflow falha fechado quando qualquer pré-requisito de produção estiver ausente.

## Demucs

- demucs.cpp fixado por commit;
- OpenBLAS 0.3.34;
- default 1 thread;
- política aceita 1/2;
- um chunk por vez;
- modelo htdemucs_6s fixado por tamanho + SHA-256.

## Assinatura de homologação

Certificado SHA-256:
6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0

É uma chave pública de teste e nunca deve assinar release de produção.

## Evidência mais recente antes do RC1

Alpha16:
- commit c05068622d8e936cfdc5cbd139da296b7e9fc61e;
- CI #117: SUCCESS;
- APK SHA-256 8468b9ab6750cab9649425679a10ecf9e5e6f333d096dfa9d7c829ebc033af46.
