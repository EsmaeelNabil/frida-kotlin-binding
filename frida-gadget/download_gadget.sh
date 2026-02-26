#!/usr/bin/env bash
set -euo pipefail

FRIDA_VERSION="17.7.3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNILIBS_DIR="$SCRIPT_DIR/src/main/jniLibs"

# Android ABI → Frida arch tag
declare -A ABI_MAP=(
  [arm64-v8a]="android-arm64"
  [armeabi-v7a]="android-arm"
  [x86_64]="android-x86_64"
  [x86]="android-x86"
)

BASE_URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}"

echo "Downloading frida-gadget ${FRIDA_VERSION} for all Android ABIs..."

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

for ABI in "${!ABI_MAP[@]}"; do
  ARCH_TAG="${ABI_MAP[$ABI]}"
  DEST="$JNILIBS_DIR/$ABI/libfrida-gadget.so"

  if [[ -f "$DEST" ]]; then
    echo "  [$ABI] already present, skipping."
    continue
  fi

  FILENAME="frida-gadget-${FRIDA_VERSION}-${ARCH_TAG}.so.gz"
  URL="${BASE_URL}/${FILENAME}"

  echo "  [$ABI] Downloading ${FILENAME}..."
  curl -fsSL "$URL" -o "$TMP_DIR/${FILENAME}"
  gunzip -c "$TMP_DIR/${FILENAME}" > "$DEST"
  echo "  [$ABI] -> $DEST"
done

echo "Done."
