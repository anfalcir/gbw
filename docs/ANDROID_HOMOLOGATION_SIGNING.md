# GBW Android — Assinatura de Homologação

## Objetivo

Os APKs de desenvolvimento/homologação precisam aceitar atualização por cima entre execuções diferentes da CI.

O Android exige que versões sucessivas do mesmo `applicationId` sejam assinadas pelo mesmo certificado. O keystore debug padrão do Android é criado localmente/por runner e, em CI efêmera, muda entre execuções. Isso impediu a atualização do alpha8 para o primeiro alpha9.

## Perfil estável

A partir de `6.0.0-alpha9.1`, builds `debug` da CI usam:

- arquivo: `app/gbw-homologation.p12`;
- alias: `gbw-homologation`;
- tipo: PKCS12;
- certificado SHA-256: `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`;
- uso: **somente homologação/desenvolvimento**.

Essa chave é intencionalmente pública no repositório e **não possui valor de segurança para distribuição de produção**. Seu propósito é apenas dar identidade estável aos APKs de teste para permitir `install -r`/atualização normal.

## Regra para produção

O APK/Bundle de release público **não deve** usar esta chave.

Antes do RC:

1. criar chave de produção privada;
2. armazená-la fora do repositório, via secret/keystore seguro;
3. configurar build type de release separado;
4. documentar fingerprint de produção;
5. nunca publicar o private key de produção.

## Migração one-time

APKs alpha8 e alpha9 inicial foram assinados por debug keys efêmeras diferentes. Como o private key do alpha8 não foi preservado, não existe forma segura de assinar um novo APK que atualize aquele alpha8.

Portanto, para entrar na cadeia estável de homologação:

1. exportar qualquer stem/dado que precise ser preservado no alpha8;
2. desinstalar o alpha8;
3. instalar `6.0.0-alpha9.1`;
4. a partir daí, APKs de homologação futuros com o mesmo certificado poderão atualizar por cima.

A CI falha se o APK final não estiver assinado com o fingerprint fixado acima.
