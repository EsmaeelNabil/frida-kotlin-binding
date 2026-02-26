#!/usr/bin/env bash
set -euo pipefail

FRIDA_VERSION="17.7.3"
# Use current working directory as output directory
SCRIPT_DIR="$(pwd)"

# Usage: ./download_frida_devkit.sh [os] [arch]
# If not provided, defaults to host OS/arch.

TARGET_OS="${1:-$(uname -s)}"
TARGET_ARCH="${2:-$(uname -m)}"

# Normalize OS
case "$TARGET_OS" in
  Darwin|macos|Mac*) OS_TAG="macos" ;;
  Linux|linux|Lin*)  OS_TAG="linux" ;;
  ios|iOS)           OS_TAG="ios" ;;
  *)                 echo "Unsupported OS: $TARGET_OS"; exit 1 ;;
esac

# Normalize Arch
if [[ -z "${2:-}" && "$OS_TAG" == "ios" ]]; then
  # Default to arm64 for iOS if not specified
  ARCH_TAG="arm64"
else
  case "$TARGET_ARCH" in
    arm64|aarch64) ARCH_TAG="arm64" ;;
    x86_64)        ARCH_TAG="x86_64" ;;
    *)             echo "Unsupported arch: $TARGET_ARCH"; exit 1 ;;
  esac
fi

DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-${OS_TAG}-${ARCH_TAG}"
DEVKIT_URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/${DEVKIT_NAME}.tar.xz"

# Output files
LIB="$SCRIPT_DIR/libfrida-core.a"
HEADER="$SCRIPT_DIR/frida_core.h"

# Skip if both files are already present
if [[ -f "$LIB" && -f "$HEADER" ]]; then
  echo "frida devkit ($OS_TAG-$ARCH_TAG) already present, skipping download."
  exit 0
fi

echo "Downloading Frida ${FRIDA_VERSION} devkit for ${OS_TAG}-${ARCH_TAG}..."

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if ! curl -fsSL "$DEVKIT_URL" -o "$TMP_DIR/devkit.tar.xz"; then
  echo "Failed to download $DEVKIT_URL"
  exit 1
fi

tar -xJf "$TMP_DIR/devkit.tar.xz" -C "$TMP_DIR"

cp "$TMP_DIR/libfrida-core.a" "$LIB"
cp "$TMP_DIR/frida-core.h"    "$HEADER"

echo "Frida devkit installed: libfrida-core.a and frida_core.h in $SCRIPT_DIR"
