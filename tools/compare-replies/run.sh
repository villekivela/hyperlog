#!/usr/bin/env bash
# Plays the check-in scenarios against the Claude API, once on the commit before the training style
# reached the context and once on this checkout, then writes the side-by-side page.
#
#   tools/compare-replies/run.sh [--frames-only] [--only key,key]
#
# The key is read from ANTHROPIC_API_KEY, else from ~/.config/hyperlog/claude-key. It is never
# printed, put on a command line or written to a file. Results go to build/compare-replies/.
set -euo pipefail

BEFORE_REF="${COMPARE_BEFORE:-2a431ef}"
root="$(git rev-parse --show-toplevel)"
out="${COMPARE_OUT:-$root/build/compare-replies}"
frames_only=0
only=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --frames-only) frames_only=1 ;;
    --only) only="$2"; shift ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done

tool="$root/tools/compare-replies"
export JAVA_HOME="${JAVA_HOME:-$(mise where java)}"
mkdir -p "$out"

# Runs the harness in the checkout $1 with Shim.$2.kt, in mode $3.
harness() {
  local tree="$1" shim="$2" mode="$3"
  local dest="$tree/app/src/test/java/com/hyperlog/app/coach/compare"
  mkdir -p "$dest"
  cp "$tool/CompareReplies.kt" "$dest/CompareReplies.kt"
  cp "$tool/Shim.$shim.kt" "$dest/Shim.kt"
  trap 'rm -rf "$dest"' RETURN
  # --rerun: the mode and the key are environment, which Gradle does not count as inputs.
  (cd "$tree" && COMPARE_MODE="$mode" ./gradlew --max-workers=3 -q testDebugUnitTest --rerun --tests 'com.hyperlog.app.coach.CompareReplies')
}

before_tree="$out/before-src"
if [[ ! -d "$before_tree" ]]; then
  mkdir -p "$before_tree"
  git -C "$root" archive "$BEFORE_REF" | tar -x -C "$before_tree"
  cp "$root/local.properties" "$before_tree/local.properties"
fi

COMPARE_DIR="$out/before" harness "$before_tree" before frames
COMPARE_DIR="$out/after" harness "$root" after frames
[[ $frames_only == 1 ]] && exit 0

if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
  ANTHROPIC_API_KEY="$(< "$HOME/.config/hyperlog/claude-key")"
  export ANTHROPIC_API_KEY
fi
for side in before after; do
  COMPARE_FRAMES="$out/$side" COMPARE_DIR="$out/$side" COMPARE_ONLY="$only" harness "$root" "after" run
done

python3 "$tool/report.py" "$out" "$root/app/src/main/assets/exercises.json" "$root/app/src/main/assets/programs/full-body-3-day.json" > "$out/compare.html"
echo "$out/compare.html"
