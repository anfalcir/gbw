#!/usr/bin/env bash
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HOME}/.local/share/guitar-backing-wizard"
VENV="${APP_HOME}/venv"

pause_and_exit() {
  local code="${1:-1}"
  echo
  echo "============================================================"
  echo "Guitar Backing Wizard terminou com ERRO (codigo $code)."
  echo "Leia a mensagem acima antes de fechar esta janela."
  echo "============================================================"
  if [[ -t 0 ]]; then
    read -r -p "Pressione ENTER para fechar..." _
  fi
  exit "$code"
}

[[ "$(uname -s 2>/dev/null || true)" == "Linux" ]] || { echo "ERRO: este pacote foi preparado para Linux."; pause_and_exit 2; }

pretty="Linux"; version=""
if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
  pretty="${PRETTY_NAME:-Linux}"; version="${VERSION_ID:-}"
fi

echo "Guitar Backing Wizard 5.23"
echo "Sistema detectado: ${pretty}${version:+ | versao ${version}}"
echo

export PATH="${APP_HOME}/bin:${VENV}/bin:${HOME}/.local/bin:${PATH}"
if [[ -x "${VENV}/bin/python" ]]; then
  PY="${VENV}/bin/python"
else
  PY="$(command -v python3 || true)"
  [[ -n "$PY" ]] || { echo "ERRO: Python 3 nao encontrado."; pause_and_exit 3; }
  echo "AVISO: ambiente virtual do wizard nao encontrado."
  echo "Executando com Python do sistema; rode ./install.sh para instalar todas as dependencias."
  echo
fi

"$PY" "${HERE}/guitar_backing_wizard.py" "$@"
code=$?
[[ $code -eq 0 ]] || pause_and_exit "$code"
exit 0
