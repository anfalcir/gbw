#!/usr/bin/env bash
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HOME}/.local/share/guitar-backing-wizard"
VENV="${APP_HOME}/venv"
RUBBERBAND_VERSION="4.0.0"
RUBBERBAND_SHA256="af050313ee63bc18b35b2e064e5dce05b276aaf6d1aa2b8a82ced1fe2f8028e9"

pause_error() {
  local code=$?
  local line="${BASH_LINENO[0]:-?}"
  echo
  echo "============================================================"
  echo "ERRO NA INSTALACAO (codigo ${code}, linha ${line})"
  echo "O terminal foi mantido aberto para voce ler a mensagem acima."
  echo "============================================================"
  if [[ -t 0 ]]; then
    read -r -p "Pressione ENTER para fechar..." _
  fi
  exit "$code"
}
trap pause_error ERR

[[ "$(uname -s)" == "Linux" ]] || { echo "Este instalador automatico foi preparado para Linux."; false; }

DISTRO_ID="linux"; DISTRO_LIKE=""; DISTRO_PRETTY="Linux"; DISTRO_VERSION=""; UBUNTU_BASE=""
if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
  DISTRO_ID="${ID:-linux}"
  DISTRO_LIKE="${ID_LIKE:-}"
  DISTRO_PRETTY="${PRETTY_NAME:-$DISTRO_ID}"
  DISTRO_VERSION="${VERSION_ID:-}"
  UBUNTU_BASE="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
fi

family="unknown"; manager=""
blob="${DISTRO_ID,,} ${DISTRO_LIKE,,}"
if command -v apt-get >/dev/null 2>&1 || [[ "$blob" == *debian* || "$blob" == *ubuntu* || "$blob" == *linuxmint* ]]; then
  family="debian"; manager="apt-get"
elif command -v dnf >/dev/null 2>&1 || [[ "$blob" == *fedora* || "$blob" == *rhel* || "$blob" == *centos* ]]; then
  family="fedora"; manager="dnf"
elif command -v pacman >/dev/null 2>&1 || [[ "$blob" == *arch* ]]; then
  family="arch"; manager="pacman"
elif command -v zypper >/dev/null 2>&1 || [[ "$blob" == *suse* ]]; then
  family="suse"; manager="zypper"
fi

echo "============================================================"
echo " Guitar Backing Wizard 5.23 - instalador Linux universal"
echo "============================================================"
echo "Distribuicao : ${DISTRO_PRETTY}"
echo "ID/versao   : ${DISTRO_ID} ${DISTRO_VERSION}"
[[ -n "$UBUNTU_BASE" ]] && echo "Base/codename: ${UBUNTU_BASE}"
echo "Familia     : ${family}"
echo "Gerenciador : ${manager:-nao detectado}"
echo

install_runtime_packages() {
  case "$family" in
    debian)
      sudo apt-get update
      sudo apt-get install -y ffmpeg rubberband-cli python3-tk python3-venv python3-pip curl unzip ca-certificates
      ;;
    fedora)
      sudo dnf install -y ffmpeg-free rubberband python3-tkinter python3-pip curl unzip ca-certificates
      ;;
    arch)
      sudo pacman -S --needed --noconfirm ffmpeg rubberband tk python python-pip curl unzip ca-certificates
      ;;
    suse)
      sudo zypper --non-interactive install ffmpeg rubberband python3-tk python3-pip curl unzip ca-certificates
      ;;
    *)
      echo "Distribuicao nao reconhecida automaticamente."
      echo "Instale manualmente: ffmpeg, Rubber Band CLI >= 3, Tkinter, python3-venv/pip, curl e unzip."
      false
      ;;
  esac
}

rubberband_r3_available() {
  local rb=""
  if [[ -x "${APP_HOME}/bin/rubberband-r3" ]]; then return 0; fi
  if [[ -x "${APP_HOME}/bin/rubberband" ]]; then rb="${APP_HOME}/bin/rubberband"
  elif command -v rubberband-r3 >/dev/null 2>&1; then return 0
  elif command -v rubberband >/dev/null 2>&1; then rb="$(command -v rubberband)"
  else return 1
  fi
  local help ver major
  help="$($rb --help 2>&1 || true)"
  grep -Eqi '(^|[[:space:]])-3([[:space:],]|$)|--fine|R3' <<<"$help" && return 0
  ver="$($rb --version 2>&1 || true)"
  major="$(grep -Eo '[0-9]+\.[0-9]+' <<<"$ver" | head -n1 | cut -d. -f1 || true)"
  [[ -n "$major" && "$major" -ge 3 ]]
}

install_build_deps() {
  case "$family" in
    debian)
      sudo apt-get install -y build-essential meson ninja-build pkg-config libsndfile1-dev libsamplerate0-dev bzip2
      ;;
    fedora)
      sudo dnf install -y gcc-c++ meson ninja-build pkgconf-pkg-config libsndfile-devel libsamplerate-devel bzip2
      ;;
    arch)
      sudo pacman -S --needed --noconfirm base-devel meson ninja pkgconf libsndfile libsamplerate bzip2
      ;;
    suse)
      sudo zypper --non-interactive install gcc-c++ meson ninja pkg-config libsndfile-devel libsamplerate-devel bzip2
      ;;
  esac
}

install_modern_rubberband() {
  echo
  echo "Rubber Band do sistema nao oferece R3 moderno."
  echo "Para preservar a qualidade do pitch, sera compilado Rubber Band ${RUBBERBAND_VERSION} em:"
  echo "  ${APP_HOME}"
  echo "Isto nao substitui arquivos do sistema."
  echo
  install_build_deps
  local tmp tarball src
  tmp="$(mktemp -d)"
  tarball="$tmp/rubberband.tar.bz2"
  curl -fL --retry 3 --retry-delay 2 \
    "https://breakfastquay.com/files/releases/rubberband-${RUBBERBAND_VERSION}.tar.bz2" -o "$tarball"
  echo "${RUBBERBAND_SHA256}  ${tarball}" | sha256sum -c -
  tar -xjf "$tarball" -C "$tmp"
  src="$tmp/rubberband-${RUBBERBAND_VERSION}"
  meson setup "$src/build" "$src" --prefix="$APP_HOME" --buildtype=release -Ddefault_library=static
  meson compile -C "$src/build"
  meson install -C "$src/build"
  rm -rf "$tmp"
  [[ -x "${APP_HOME}/bin/rubberband" ]]
}

echo "[1/7] Dependencias do sistema"
install_runtime_packages

if rubberband_r3_available; then
  echo "[2/7] Rubber Band R3: OK"
else
  echo "[2/7] Atualizando Rubber Band para engine R3 de alta qualidade"
  install_modern_rubberband
  rubberband_r3_available || { echo "R3 ainda nao foi confirmado apos compilacao."; false; }
fi

echo
echo "[3/7] Instalando Deno oficial (runtime recomendado pelo yt-dlp para YouTube)"
mkdir -p "${APP_HOME}/bin"
arch="$(uname -m)"
case "$arch" in
  x86_64|amd64) deno_target="x86_64-unknown-linux-gnu" ;;
  aarch64|arm64) deno_target="aarch64-unknown-linux-gnu" ;;
  *) deno_target="" ;;
esac
if [[ -n "$deno_target" ]]; then
  deno_ver="$(curl -fsSL --retry 3 https://dl.deno.land/release-latest.txt)"
  deno_base="https://github.com/denoland/deno/releases/download/${deno_ver}/deno-${deno_target}.zip"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 --retry-delay 2 "$deno_base" -o "$tmp/deno.zip"
  # O asset .sha256sum referencia o NOME OFICIAL do ZIP. Como salvamos localmente como
  # deno.zip, validamos o hash diretamente em vez de usar `sha256sum -c` pelo nome.
  if curl -fL --retry 2 "${deno_base}.sha256sum" -o "$tmp/deno.zip.sha256sum" 2>/dev/null; then
    expected_hash="$(awk 'NF {print $1; exit}' "$tmp/deno.zip.sha256sum")"
    actual_hash="$(sha256sum "$tmp/deno.zip" | awk '{print $1}')"
    if [[ -z "$expected_hash" || "$expected_hash" != "$actual_hash" ]]; then
      echo "ERRO: checksum SHA-256 do Deno nao confere."
      echo "Esperado: ${expected_hash:-indisponivel}"
      echo "Obtido  : ${actual_hash}"
      false
    fi
    echo "Deno SHA-256: OK"
  else
    echo "AVISO: asset de checksum do Deno indisponivel; download HTTPS foi concluido, mas sem validacao SHA-256 publicada."
  fi
  unzip -oq "$tmp/deno.zip" -d "$tmp/unpack"
  install -m 0755 "$tmp/unpack/deno" "${APP_HOME}/bin/deno"
  rm -rf "$tmp"
else
  echo "Arquitetura ${arch}: Deno automatico ignorado; yt-dlp continua instalado."
fi

echo
echo "[4/7] Criando ambiente Python isolado"
mkdir -p "${APP_HOME}"
python3 -m venv "${VENV}"
"${VENV}/bin/python" -m pip install -U pip wheel

echo
echo "[5/7] Instalando ferramentas de audio/IA"
if [[ "$(${VENV}/bin/python -c 'import sys; print(int(sys.version_info >= (3,12)))')" == "1" ]]; then
  librosa_spec="librosa"
else
  # librosa 1.0 requer Python >=3.12; 0.11 suporta Python >=3.8.
  librosa_spec="librosa==0.11.0"
fi
echo "Python do venv: $(${VENV}/bin/python --version 2>&1) | ${librosa_spec}"
"${VENV}/bin/pip" install -U customtkinter 'yt-dlp[default,curl-cffi]' 'bs-roformer-infer>=0.1.5' demucs "${librosa_spec}"

echo
echo "[6/7] Instalando arquivos do aplicativo"
APP_DIR="${APP_HOME}/app"
mkdir -p "${APP_DIR}"
rm -rf "${APP_DIR}/gbw" "${APP_DIR}/assets"
cp -a "${HERE}/gbw" "${APP_DIR}/gbw"
cp -a "${HERE}/assets" "${APP_DIR}/assets"
cp -a "${HERE}/guitar_backing_wizard.py" "${HERE}/audio_intelligence.py" "${HERE}/run.sh" "${HERE}/install.sh" "${HERE}/criar_atalho.sh" "${HERE}/validate_local.sh" "${HERE}/requirements.txt" "${APP_DIR}/"
[[ ! -f "${HERE}/README.md" ]] || cp -a "${HERE}/README.md" "${APP_DIR}/README.md"
[[ ! -f "${HERE}/VALIDATION.md" ]] || cp -a "${HERE}/VALIDATION.md" "${APP_DIR}/VALIDATION.md"
if [[ -d "${HERE}/tests" ]]; then rm -rf "${APP_DIR}/tests"; cp -a "${HERE}/tests" "${APP_DIR}/tests"; fi
chmod +x "${APP_DIR}/guitar_backing_wizard.py" "${APP_DIR}/run.sh" "${APP_DIR}/install.sh" "${APP_DIR}/criar_atalho.sh" "${APP_DIR}/validate_local.sh"

echo
echo "[7/7] Validando instalacao"
export PATH="${APP_HOME}/bin:${VENV}/bin:${HOME}/.local/bin:${PATH}"
command -v ffmpeg >/dev/null
rubberband_r3_available
python3 - <<'PY'
import tkinter
print('Tkinter: OK')
PY
"${VENV}/bin/yt-dlp" --version
bs_help="$(${VENV}/bin/bs-roformer-infer --help 2>&1)"
if grep -q -- '--output_format' <<<"${bs_help}"; then
  echo "BS-RoFormer CLI: --output_format OK"
else
  echo "AVISO: BS-RoFormer CLI sem --output_format; o aplicativo usará modo de compatibilidade."
fi
"${VENV}/bin/demucs" --help >/dev/null
"${VENV}/bin/python" - <<'PYLIB'
import customtkinter, librosa
print('CustomTkinter:', customtkinter.__version__)
print('librosa:', librosa.__version__)
PYLIB
[[ ! -x "${APP_HOME}/bin/deno" ]] || "${APP_HOME}/bin/deno" --version

chmod +x "${HERE}/guitar_backing_wizard.py" "${HERE}/run.sh" "${HERE}/criar_atalho.sh" "${HERE}/validate_local.sh" 2>/dev/null || true

echo
echo "============================================================"
echo "INSTALACAO CONCLUIDA"
echo "============================================================"
echo "Execute:"
echo "  ./run.sh"
echo
echo "Criar atalho no menu e na Área de Trabalho:"
echo "  ./criar_atalho.sh"
echo
echo "Cópia instalada em:"
echo "  ${APP_HOME}/app"
echo
echo "Na primeira separacao A, o BS-RoFormer baixa automaticamente o checkpoint recomendado."
