#!/usr/bin/env bash
set -Eeuo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HOME}/.local/share/guitar-backing-wizard"
VENV="${APP_HOME}/venv"
PY="${VENV}/bin/python"

pause_error() {
  local code=$?
  echo
  echo "VALIDACAO FALHOU (codigo ${code}, linha ${BASH_LINENO[0]:-?})."
  [[ -t 0 ]] && read -r -p "Pressione ENTER para fechar..." _
  exit "$code"
}
trap pause_error ERR

[[ -x "$PY" ]] || { echo "Venv nao encontrado: rode ./install.sh primeiro."; false; }
export PATH="${APP_HOME}/bin:${VENV}/bin:${HOME}/.local/bin:${PATH}"
cd "$HERE"

echo "[1/6] Sintaxe dos scripts"
bash -n install.sh
bash -n run.sh
bash -n criar_atalho.sh

echo "[2/6] Compilacao Python"
"$PY" -m compileall -q gbw guitar_backing_wizard.py audio_intelligence.py

echo "[3/6] Dependencias Python principais"
"$PY" - <<'PY'
import customtkinter, librosa
print('CustomTkinter:', customtkinter.__version__)
print('librosa:', librosa.__version__)
PY

echo "[4/6] Testes do nucleo + FFmpeg"
PYTHONPATH=. "$PY" -m unittest tests.test_core tests.test_audio_pipeline -v

echo "[5/6] Testes de GUI, lazy-loading e escala"
if [[ -n "${DISPLAY:-}" || -n "${WAYLAND_DISPLAY:-}" ]]; then
  timeout 90s env GBW_HEADLESS_TEST=1 PYTHONPATH=. "$PY" -m unittest tests.test_gui_smoke -v
else
  echo "Sessao grafica nao detectada; testes de GUI serao cobertos pelo self-test quando houver display."
fi

echo "[6/6] Self-test da aplicacao"
timeout 30s env PYTHONPATH=. "$PY" guitar_backing_wizard.py --self-test

echo
echo "============================================================"
echo "VALIDACAO LOCAL CONCLUIDA COM SUCESSO"
echo "============================================================"
