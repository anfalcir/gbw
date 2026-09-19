#!/usr/bin/env bash
set -euo pipefail

umask 077
export LC_ALL=C

: "${GBW_RELEASE_STORE_PASSWORD:?Defina GBW_RELEASE_STORE_PASSWORD}"
: "${GBW_RELEASE_KEY_ALIAS:?Defina GBW_RELEASE_KEY_ALIAS}"
: "${GBW_RELEASE_DNAME:?Defina GBW_RELEASE_DNAME, por exemplo CN=...,O=...,C=...}"

command -v keytool >/dev/null || {
  echo "keytool não encontrado. Instale/use um JDK 17+." >&2
  exit 1
}
command -v base64 >/dev/null || {
  echo "base64 não encontrado." >&2
  exit 1
}

OUT="${1:-gbw-production.p12}"
case "$OUT" in
  *.p12) ;;
  *) echo "Use um arquivo de saída .p12" >&2; exit 1 ;;
esac

if [[ -e "$OUT" || -e "$OUT.b64" || -e "$OUT.sha256.txt" ]]; then
  echo "Saída já existe; não vou sobrescrever uma identidade de produção." >&2
  exit 1
fi

keytool -genkeypair   -keystore "$OUT"   -storetype PKCS12   -storepass "$GBW_RELEASE_STORE_PASSWORD"   -alias "$GBW_RELEASE_KEY_ALIAS"   -keypass "$GBW_RELEASE_STORE_PASSWORD"   -keyalg RSA   -keysize 4096   -validity 10000   -dname "$GBW_RELEASE_DNAME"

FINGERPRINT="$(
  keytool -list -v     -keystore "$OUT"     -storepass "$GBW_RELEASE_STORE_PASSWORD"     -alias "$GBW_RELEASE_KEY_ALIAS" |
  sed -n 's/^[[:space:]]*SHA256: //p' |
  head -1 |
  tr -d ':' |
  tr '[:upper:]' '[:lower:]'
)"

if [[ ! "$FINGERPRINT" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Não foi possível extrair o fingerprint SHA-256." >&2
  exit 1
fi

base64 -w 0 "$OUT" > "$OUT.b64"
printf '%s\n' "$FINGERPRINT" > "$OUT.sha256.txt"
chmod 600 "$OUT" "$OUT.b64" "$OUT.sha256.txt"

cat <<EOF

Identidade de produção criada localmente.

Arquivos privados (NÃO COMMITAR):
  $OUT
  $OUT.b64

Fingerprint público:
  $FINGERPRINT

Configure no GitHub estes Actions secrets:
  GBW_RELEASE_KEYSTORE_B64      = conteúdo de $OUT.b64
  GBW_RELEASE_STORE_PASSWORD    = o password usado agora
  GBW_RELEASE_KEY_ALIAS         = $GBW_RELEASE_KEY_ALIAS
  GBW_RELEASE_KEY_PASSWORD      = o mesmo valor de GBW_RELEASE_STORE_PASSWORD
  GBW_RELEASE_CERT_SHA256       = $FINGERPRINT

Guarde $OUT e a senha em backup seguro separado do GitHub.
EOF
