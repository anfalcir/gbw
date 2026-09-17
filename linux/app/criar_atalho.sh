#!/usr/bin/env bash
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HOME}/.local/share/guitar-backing-wizard"
APP_DIR="${APP_HOME}/app"
MENU_DIR="${HOME}/.local/share/applications"
MENU_FILE="${MENU_DIR}/guitar-backing-wizard.desktop"
ICON_NAME="guitar-backing-wizard"
ICON_DIR="${HOME}/.local/share/icons/hicolor/512x512/apps"
ICON_FILE="${ICON_DIR}/${ICON_NAME}.png"

pause_error() {
  local code=$?
  echo
  echo "============================================================"
  echo "ERRO AO CRIAR ATALHO (codigo ${code})"
  echo "============================================================"
  if [[ -t 0 ]]; then
    read -r -p "Pressione ENTER para fechar..." _
  elif command -v notify-send >/dev/null 2>&1; then
    notify-send -u critical "Guitar Backing Wizard" "Falha ao criar o atalho. Execute criar_atalho.sh pelo terminal para ver os detalhes." || true
  fi
  exit "$code"
}
trap pause_error ERR

[[ "$(uname -s 2>/dev/null || true)" == "Linux" ]] || { echo "Este atalho é destinado ao Linux."; false; }

echo "============================================================"
echo " Guitar Backing Wizard 5.23 - criar atalho"
echo "============================================================"
echo

install_app_copy() {
  echo "[1/4] Atualizando cópia instalada do aplicativo"
  mkdir -p "$APP_DIR"
  if [[ "$HERE" != "$APP_DIR" ]]; then
    rm -rf "$APP_DIR/gbw" "$APP_DIR/assets"
    cp -a "$HERE/gbw" "$APP_DIR/gbw"
    cp -a "$HERE/assets" "$APP_DIR/assets"
    cp -a "$HERE/guitar_backing_wizard.py" "$HERE/audio_intelligence.py" "$HERE/run.sh" "$HERE/install.sh" "$HERE/validate_local.sh" "$HERE/criar_atalho.sh" "$HERE/requirements.txt" "$APP_DIR/"
    [[ -f "$HERE/README.md" ]] && cp -a "$HERE/README.md" "$APP_DIR/README.md"
    [[ -f "$HERE/VALIDATION.md" ]] && cp -a "$HERE/VALIDATION.md" "$APP_DIR/VALIDATION.md"
    if [[ -d "$HERE/tests" ]]; then rm -rf "$APP_DIR/tests"; cp -a "$HERE/tests" "$APP_DIR/tests"; fi
  fi
  chmod +x "$APP_DIR/guitar_backing_wizard.py" "$APP_DIR/run.sh" "$APP_DIR/install.sh" "$APP_DIR/validate_local.sh" "$APP_DIR/criar_atalho.sh"
}

detect_desktop_dir() {
  local d=""
  if command -v xdg-user-dir >/dev/null 2>&1; then
    d="$(xdg-user-dir DESKTOP 2>/dev/null || true)"
    if [[ -n "$d" && "$d" != "$HOME" ]]; then
      printf '%s\n' "$d"
      return 0
    fi
  fi

  if [[ -r "$HOME/.config/user-dirs.dirs" ]]; then
    # Arquivo criado pelo próprio XDG; expande somente $HOME.
    d="$(sed -n 's/^XDG_DESKTOP_DIR="\(.*\)"/\1/p' "$HOME/.config/user-dirs.dirs" | head -n1)"
    d="${d//\$HOME/$HOME}"
    if [[ -n "$d" ]]; then
      printf '%s\n' "$d"
      return 0
    fi
  fi

  for d in "$HOME/Área de Trabalho" "$HOME/Área de trabalho" "$HOME/Desktop"; do
    if [[ -d "$d" ]]; then
      printf '%s\n' "$d"
      return 0
    fi
  done

  printf '%s\n' "$HOME/Desktop"
}

install_app_copy

RUNNER="$APP_DIR/run.sh"
ICON_SOURCE="$APP_DIR/assets/guitar-backing-wizard.png"
[[ -x "$RUNNER" ]] || { echo "ERRO: launcher instalado não encontrado: $RUNNER"; false; }
[[ -f "$ICON_SOURCE" ]] || { echo "ERRO: ícone não encontrado: $ICON_SOURCE"; false; }

echo "[2/4] Instalando ícone"
mkdir -p "$ICON_DIR"
cp -f "$ICON_SOURCE" "$ICON_FILE"
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" >/dev/null 2>&1 || true
fi

echo "[3/4] Criando entrada no menu de aplicativos"
mkdir -p "$MENU_DIR"
cat > "$MENU_FILE" <<DESKTOP
[Desktop Entry]
Version=1.0
Type=Application
Name=Guitar Backing Wizard
GenericName=Backing Track Wizard
Comment=Crie backings para estudo de guitarra
Exec="$RUNNER"
Path=$APP_DIR
Terminal=false
Icon=$ICON_FILE
Categories=AudioVideo;Audio;Music;
Keywords=guitar;backing;stems;audio;practice;
StartupNotify=true
StartupWMClass=Guitarbackingwizard
DESKTOP
chmod +x "$MENU_FILE"

if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$MENU_FILE"
fi
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$MENU_DIR" >/dev/null 2>&1 || true
fi

DESKTOP_DIR="$(detect_desktop_dir)"
DESKTOP_FILE="$DESKTOP_DIR/Guitar Backing Wizard.desktop"

echo "[4/4] Criando atalho na Área de Trabalho"
mkdir -p "$DESKTOP_DIR"
cp -f "$MENU_FILE" "$DESKTOP_FILE"
chmod +x "$DESKTOP_FILE"

# Cinnamon/Nemo usa esta metadata para permitir execução sem alerta de confiança.
if command -v gio >/dev/null 2>&1; then
  gio set "$DESKTOP_FILE" metadata::trusted true >/dev/null 2>&1 || true
fi

# Alguns desktops só atualizam o ícone após tocar no arquivo.
touch "$MENU_FILE" "$DESKTOP_FILE"

echo
echo "============================================================"
echo "ATALHO CRIADO COM SUCESSO"
echo "============================================================"
echo "Menu de aplicativos: $MENU_FILE"
echo "Área de Trabalho   : $DESKTOP_FILE"
echo "Ícone              : $ICON_FILE"
echo

if command -v notify-send >/dev/null 2>&1; then
  notify-send -i "$ICON_FILE" "Guitar Backing Wizard" "Atalho criado na Área de Trabalho e no menu de aplicativos." || true
fi

if [[ -t 0 ]]; then
  read -r -p "Pressione ENTER para fechar..." _
fi
