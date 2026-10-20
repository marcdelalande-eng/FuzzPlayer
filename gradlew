#!/usr/bin/env sh
set -e
VER=9.6.0
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-wrapper/gradle-$VER"
BIN="$BASE/gradle-$VER/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VER-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$VER-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -L "$URL" -o "$ZIP"; elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"; else echo "curl ou wget requis" >&2; exit 1; fi
  unzip -q "$ZIP" -d "$BASE"
fi
exec "$BIN" "$@"
