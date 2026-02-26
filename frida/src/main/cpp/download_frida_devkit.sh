#!/usr/bin/env bash
set -euo pipefail

FRIDA_VERSION="17.7.3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Determine OS and arch
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
  Darwin) OS_TAG="macos" ;;
  Linux)  OS_TAG="linux" ;;
  *)      echo "Unsupported OS: $OS"; exit 1 ;;
esac

case "$ARCH" in
  arm64|aarch64) ARCH_TAG="arm64" ;;
  x86_64)        ARCH_TAG="x86_64" ;;
  *)             echo "Unsupported arch: $ARCH"; exit 1 ;;
esac

DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-${OS_TAG}-${ARCH_TAG}"
DEVKIT_URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/${DEVKIT_NAME}.tar.xz"

LIB="$SCRIPT_DIR/libfrida-core.a"
HEADER="$SCRIPT_DIR/frida_core.h"

# Skip if both files are already present
if [[ -f "$LIB" && -f "$HEADER" ]]; then
  echo "frida devkit already present, skipping download."
  exit 0
fi

echo "Downloading Frida ${FRIDA_VERSION} devkit for ${OS_TAG}-${ARCH_TAG}..."

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

curl -fsSL "$DEVKIT_URL" -o "$TMP_DIR/devkit.tar.xz"
tar -xJf "$TMP_DIR/devkit.tar.xz" -C "$TMP_DIR"

cp "$TMP_DIR/libfrida-core.a" "$LIB"
cp "$TMP_DIR/frida-core.h"    "$HEADER"

echo "Frida devkit installed: libfrida-core.a and frida_core.h"
