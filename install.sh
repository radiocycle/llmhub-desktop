#!/usr/bin/env bash
set -e

BOLD='\033[1m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}${BOLD}"
echo "  _     _      __  __ _   _       _     "
echo " | |   | |    |  \/  | | | |     | |    "
echo " | |   | |    | \  / | |_| |_   _| |__  "
echo " | |   | |    | |\/| |  _  | | | | '_ \ "
echo " | |___| |____| |  | | | | | |_| | |_) |"
echo " |_____|______|_|  |_|_| |_|\__,_|_.__/ "
echo "               Desktop for Linux"
echo -e "${NC}"

ARCH="$(uname -m)"
if [ "$ARCH" != "x86_64" ]; then
    echo -e "${RED}Error: LLMHub Desktop currently only supports x86_64 Linux (detected: $ARCH).${NC}"
    exit 1
fi

REPO="radiocycle/llmhub-desktop"
INSTALL_PREFIX="${HOME}/.local"
BIN_DIR="${INSTALL_PREFIX}/bin"
LIB_DIR="${INSTALL_PREFIX}/lib/llmhub"
APPS_DIR="${INSTALL_PREFIX}/share/applications"
ICONS_DIR="${INSTALL_PREFIX}/share/icons/hicolor/512x512/apps"

mkdir -p "$BIN_DIR" "$LIB_DIR" "$APPS_DIR" "$ICONS_DIR"

echo -e "${CYAN}→ Finding latest release...${NC}"
TAG="${LLMHUB_VERSION:-}"
if [ -z "$TAG" ]; then
    TAG="$(curl -sSL "https://api.github.com/repos/${REPO}/releases/latest" 2>/dev/null | grep '"tag_name":' | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
fi
if [ -z "$TAG" ]; then
    TAG="v1.1.0"
fi
echo -e "  Using version: ${BOLD}${TAG}${NC}"

TARBALL_URL="https://github.com/${REPO}/releases/download/${TAG}/llmhub-linux-x86_64.tar.gz"
ICON_URL="https://raw.githubusercontent.com/${REPO}/main/src/main/resources/icon.png"

TMP_DIR="$(mktemp -d -t llmhub-install-XXXXXX)"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo -e "${CYAN}→ Downloading LLMHub Desktop tarball...${NC}"
if command -v curl >/dev/null 2>&1; then
    curl -# -L "$TARBALL_URL" -o "$TMP_DIR/llmhub.tar.gz"
elif command -v wget >/dev/null 2>&1; then
    wget --show-progress -q "$TARBALL_URL" -O "$TMP_DIR/llmhub.tar.gz"
else
    echo -e "${RED}Error: Neither curl nor wget was found.${NC}"
    exit 1
fi

echo -e "${CYAN}→ Extracting into ${LIB_DIR}...${NC}"
tar -xzf "$TMP_DIR/llmhub.tar.gz" -C "$TMP_DIR"

rm -rf "$LIB_DIR"
mkdir -p "$LIB_DIR"
cp -r "$TMP_DIR/llmhub"/* "$LIB_DIR/"
chmod +x "$LIB_DIR/bin/llmhub"

cat << 'EOF_BIN' > "$BIN_DIR/llmhub"
#!/bin/sh
exec "$HOME/.local/lib/llmhub/bin/llmhub" "$@"
EOF_BIN
chmod +x "$BIN_DIR/llmhub"

echo -e "${CYAN}→ Registering desktop application & icon...${NC}"
cat << EOF_DESKTOP > "$APPS_DIR/llmhub.desktop"
[Desktop Entry]
Name=LLMHub
Comment=LLM client with auto-rotation, failover, and tools
Exec=${BIN_DIR}/llmhub %u
Icon=llmhub
Terminal=false
Type=Application
Categories=Utility;Development;Network;
StartupWMClass=dev-radiocycle-llmhub-MainKt
EOF_DESKTOP

curl -sSL "$ICON_URL" -o "$ICONS_DIR/llmhub.png" 2>/dev/null || true

command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q "${INSTALL_PREFIX}/share/icons/hicolor" || true

echo ""
echo -e "${GREEN}${BOLD}✓ LLMHub Desktop successfully installed!${NC}"
echo -e "  Installed binary:  ${CYAN}${BIN_DIR}/llmhub${NC}"
echo -e "  App files:         ${CYAN}${LIB_DIR}${NC}"
echo -e "  Desktop shortcut:  ${CYAN}${APPS_DIR}/llmhub.desktop${NC}"

case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *)
        echo ""
        echo -e "${YELLOW}${BOLD}Notice:${NC} ${BIN_DIR} is not in your current PATH."
        echo -e "To launch 'llmhub' from anywhere in terminal, add this to your ${BOLD}~/.bashrc${NC} or ${BOLD}~/.zshrc${NC}:"
        echo -e "  ${CYAN}export PATH=\"\$HOME/.local/bin:\$PATH\"${NC}"
        ;;
esac

echo ""
echo -e "Launch now by running: ${BOLD}llmhub${NC} or from your application menu!"
