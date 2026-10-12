#!/usr/bin/env bash
# Adds Material Symbols (Rounded, weight 400, unfilled, 24 dp) as vector drawables, so the app
# ships only the icons it uses instead of the 15 MB variable font.
# Usage: scripts/add-symbol.sh settings fitness_center
# Each icon lands in app/src/main/res/drawable/symbol_<name>.xml; then add it to Symbol.kt.
set -euo pipefail
cd "$(dirname "$0")/.."
base=https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android
for name in "$@"; do
  out="app/src/main/res/drawable/symbol_${name}.xml"
  # The tint attribute needs AppCompat; Compose's Icon tints the drawable itself.
  curl -sSf "$base/$name/materialsymbolsrounded/${name}_24px.xml" \
    | sed -E 's/[[:space:]]*android:tint="[^"]*"//' > "$out"
  echo "$out"
done
