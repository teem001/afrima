#!/usr/bin/env bash
# Rebuilds diagrams (optional) and the Word reports.
# Usage: ./build.sh [--diagrams] [afrismart|apdt|all]
# Requires pandoc 3.x on PATH (or PANDOC=/path/to/pandoc); diagrams need PLANTUML_JAR and JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"

PANDOC="${PANDOC:-pandoc}"
TARGET="${2:-${1:-all}}"
[[ "${1:-}" == "--diagrams" ]] && SHIFT=1 || SHIFT=0

if [[ "${1:-}" == "--diagrams" ]]; then
  : "${PLANTUML_JAR:?set PLANTUML_JAR to plantuml.jar}"
  (cd diagrams && java -Djava.awt.headless=true -jar "$PLANTUML_JAR" -tpng -Sdpi=180 -o ../img ./*.puml)
  TARGET="${2:-all}"
fi

build_one() {
  local md="$1" docx="$2"
  "$PANDOC" "$md" -o "$docx" \
    --toc --toc-depth=2 --number-sections \
    --resource-path=. \
    --reference-doc=reference.docx \
    --lua-filter=pagebreaks.lua
  echo "Wrote $(pwd)/$docx"
}

case "$TARGET" in
  afrismart) build_one AfriSmart_Research_Report.md AfriSmart_Research_Report.docx ;;
  apdt) build_one APDT_Report.md APDT_Report.docx ;;
  all)
    build_one AfriSmart_Research_Report.md AfriSmart_Research_Report.docx
    [[ -f APDT_Report.md ]] && build_one APDT_Report.md APDT_Report.docx || true
    ;;
  *) echo "Unknown target: $TARGET (use afrismart|apdt|all)"; exit 1 ;;
esac
