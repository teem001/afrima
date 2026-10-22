#!/usr/bin/env bash
# Replays the patient-access scenario used in the report against a running backend
# and records tool outputs plus per-call latency.
# Usage: ./scenario.sh [base-url] [output-dir]
set -euo pipefail

BASE="${1:-http://localhost:8087}"
OUT="${2:-./scenario-output}"
LAT=6.5158
LNG=3.3791
PASSWORD='Password123!'
mkdir -p "$OUT"

login() {
  curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'
}

# invoke <token> <tool> <arguments-json> <output-name>
invoke() {
  curl -s -o "$OUT/$4.json" -w "$4 %{http_code} %{time_total}\n" \
    -X POST "$BASE/api/v1/ai/tools/$2/invoke" \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"arguments\":$3,\"latitude\":$LAT,\"longitude\":$LNG}"
}

PATIENT=$(login ada@afrismart.ng)
PHARMACIST=$(login pharmacy.yaba@afrismart.ng)

invoke "$PATIENT" SEARCH_PRODUCTS_NEARBY '{"query":"malaria"}' 01-search
invoke "$PATIENT" GET_STORE_DETAILS '{"storeName":"Ojuelegba","query":"malaria"}' 02-usual-store
invoke "$PATIENT" FIND_ALTERNATIVE_STORES '{"query":"malaria","quantity":1,"excludeStoreName":"Ojuelegba"}' 03-reroute
invoke "$PATIENT" COMPARE_PRICES '{"query":"malaria","quantity":1,"radiusMeters":20000}' 04-compare

STORE_ID=$(sed -E 's/.*"recommended":\{"storeId":"([^"]+)".*/\1/' "$OUT/03-reroute.json")
PRODUCT_ID=$(sed -E 's/.*"recommended":\{[^}]*"productId":"([^"]+)".*/\1/' "$OUT/03-reroute.json")
invoke "$PATIENT" PLACE_ORDER "{\"storeId\":\"$STORE_ID\",\"items\":[{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}]}" 05-order

invoke "$PHARMACIST" GET_INCOMING_ORDERS '{}' 06-incoming
invoke "$PHARMACIST" GET_LOW_STOCK_ITEMS '{"threshold":10}' 07-low-stock
invoke "$PHARMACIST" PLACE_ORDER '{}' 08-role-check
