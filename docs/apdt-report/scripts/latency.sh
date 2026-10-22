#!/usr/bin/env bash
# Warm-path latency of read-only APDT/AHDT tools against a running backend (local H2).
# Usage: ./latency.sh [base-url] [runs]
set -euo pipefail

BASE="${1:-http://localhost:8087}"
RUNS="${2:-30}"
PASSWORD='Password123!'

TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"ada@afrismart.ng\",\"password\":\"$PASSWORD\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

measure() {
  local tool="$1" args="$2"
  for _ in $(seq 1 5); do
    curl -s -o /dev/null -X POST "$BASE/api/v1/ai/tools/$tool/invoke" -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' -d "{\"arguments\":$args,\"latitude\":6.5158,\"longitude\":3.3791}"
  done
  for _ in $(seq 1 "$RUNS"); do
    curl -s -o /dev/null -w '%{time_total}\n' -X POST "$BASE/api/v1/ai/tools/$tool/invoke" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d "{\"arguments\":$args,\"latitude\":6.5158,\"longitude\":3.3791}"
  done | sort -n | awk -v tool="$tool" '{ v[NR] = $1 * 1000; s += v[NR] }
    END { printf "%s runs=%d median=%.1fms p95=%.1fms mean=%.1fms\n", tool, NR, v[int((NR + 1) / 2)], v[int(NR * 0.95)], s / NR }'
}

measure SEARCH_PRODUCTS_NEARBY '{"query":"malaria"}'
measure GET_STORE_DETAILS '{"storeName":"Ojuelegba","query":"malaria"}'
measure FIND_ALTERNATIVE_STORES '{"query":"malaria","excludeStoreName":"Ojuelegba"}'
measure COMPARE_PRICES '{"query":"malaria","radiusMeters":20000}'
