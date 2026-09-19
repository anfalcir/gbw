# GBW Android — Provisionamento da Assinatura de Produção

A chave de produção é um segredo permanente do proprietário do aplicativo. Ela nunca deve ser
commitada nem enviada em mensagens, issues, artifacts ou logs.

## 1. Gerar localmente

Em uma máquina Linux confiável com JDK 17+:

```bash
cd android
export GBW_RELEASE_STORE_PASSWORD='uma-senha-forte-e-unica'
export GBW_RELEASE_KEY_ALIAS='gbw-production'
export GBW_RELEASE_DNAME='CN=GBW Android Release,O=GBW,C=BR'
./scripts/prepare_production_signing.sh
```

O script:
- usa RSA 4096 / PKCS12;
- não sobrescreve identidade existente;
- cria o keystore com umask 077;
- cria uma cópia Base64 para cadastrar como secret;
- extrai o fingerprint SHA-256;
- nunca grava a senha em arquivo.

Os valores de DNAME acima são apenas exemplo. Use a identidade que o proprietário decidir registrar.

## 2. Guardar backup

Guardar fora do Git:
- gbw-production.p12;
- senha;
- alias;
- fingerprint.

Manter pelo menos um backup seguro separado. Perder a chave pode impedir atualizações futuras de
uma instalação distribuída com aquela identidade.

## 3. Configurar GitHub Actions secrets

No repositório GitHub, cadastrar:

- GBW_RELEASE_KEYSTORE_B64 = conteúdo integral de gbw-production.p12.b64;
- GBW_RELEASE_STORE_PASSWORD = senha do PKCS12;
- GBW_RELEASE_KEY_ALIAS = alias;
- GBW_RELEASE_KEY_PASSWORD = mesma senha do PKCS12;
- GBW_RELEASE_CERT_SHA256 = conteúdo de gbw-production.p12.sha256.txt.

O conector usado pelo desenvolvimento não possui permissão para ler/escrever secrets; essa etapa
é intencionalmente humana.

## 4. Compliance antes da release

O workflow de produção também exige LICENSE na raiz e mantém o gate de
docs/ANDROID_THIRD_PARTY.md. Não contorne esse gate com uma licença escolhida apenas para fazer a
CI passar.

## 5. Executar release

Somente depois de assinatura + compliance:
GitHub → Actions → Android Production Release → Run workflow.

O job:
- valida pré-requisitos;
- materializa o keystore apenas no runner;
- executa gates/testes/lint;
- gera assembleRelease;
- compara o certificado real com GBW_RELEASE_CERT_SHA256;
- rejeita a chave pública de homologação;
- publica APK, SHA256SUMS.txt e build-info.txt.

O APK de produção só deve ser promovido após a campanha física R6.
