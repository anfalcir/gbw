# GBW Android — Assinatura

## Homologação

Builds debug de homologação usam identidade estável para permitir upgrade entre candidatos.

Perfil:
- app/gbw-homologation.p12;
- alias gbw-homologation;
- PKCS12;
- certificado SHA-256 6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0;
- uso exclusivo: desenvolvimento/homologação.

A chave é intencionalmente pública no repositório e não oferece identidade segura para distribuição.

## Produção

Build type release possui configuração separada e só recebe signingConfig production quando todas as variáveis de ambiente de release estão presentes.

Workflow:
.github/workflows/android-release.yml

Secrets necessários:
- GBW_RELEASE_KEYSTORE_B64;
- GBW_RELEASE_STORE_PASSWORD;
- GBW_RELEASE_KEY_ALIAS;
- GBW_RELEASE_KEY_PASSWORD;
- GBW_RELEASE_CERT_SHA256.

O keystore:
- é materializado apenas no runner;
- recebe chmod restritivo;
- não é commitado;
- não aparece em artifacts;
- deve ser preservado pelo proprietário fora do GitHub como backup seguro.

O workflow verifica que o certificado real do APK:
- coincide com GBW_RELEASE_CERT_SHA256;
- é diferente do fingerprint público de homologação.

## Bloqueios

R5 não fecha até existir:
- identidade privada de produção provisionada;
- fingerprint registrado;
- compliance de distribuição resolvido conforme docs/ANDROID_THIRD_PARTY.md.

## Cadeia antiga

Builds alpha8/alpha9 inicial usaram debug keys efêmeras e não podem ser atualizados com a identidade estável atual sem reinstalação. A cadeia estável de homologação passa a usar o fingerprint acima.
