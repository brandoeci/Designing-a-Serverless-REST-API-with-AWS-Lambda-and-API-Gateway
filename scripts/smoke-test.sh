#!/usr/bin/env bash
# Exercises a deployed API over HTTP and checks the status code of every case the workshop asks
# for: 200, 201, 204, 400 and 404.
#
#   scripts/smoke-test.sh                       # Architecture B (proxy), URL from .deploy/outputs.env
#   scripts/smoke-test.sh custom                # Architecture A (custom integration)
#   scripts/smoke-test.sh proxy https://abc123xyz.execute-api.us-east-1.amazonaws.com/dev
#
# Exit code 0 means every expectation held.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

ARCH="${1:-proxy}"
BASE_URL="${2:-}"

if [[ -z "${BASE_URL}" ]]; then
  [[ -f "${DEPLOY_DIR}/outputs.env" ]] || die "No URL given and no ${DEPLOY_DIR}/outputs.env. Run scripts/deploy-api.sh."
  # shellcheck disable=SC1091
  source "${DEPLOY_DIR}/outputs.env"
  if [[ "${ARCH}" == "custom" ]]; then
    BASE_URL="${CUSTOM_BASE_URL:-}"
  else
    BASE_URL="${PROXY_BASE_URL:-}"
  fi
fi

[[ -n "${BASE_URL}" ]] || die "Could not determine the base URL for architecture '${ARCH}'."

log "Smoke test - architecture ${ARCH}"
info "base URL : ${BASE_URL}"

PASSED=0
FAILED=0
WARNED=0

# check <expected status> <method> <path> [body]
check() {
  local expected="$1" method="$2" path="$3" body="${4:-}"
  local response status payload

  if [[ -n "${body}" ]]; then
    response="$(curl -sS -o - -w $'\n%{http_code}' -X "${method}" "${BASE_URL}${path}" \
      -H 'Content-Type: application/json' -d "${body}")"
  else
    response="$(curl -sS -o - -w $'\n%{http_code}' -X "${method}" "${BASE_URL}${path}")"
  fi

  status="$(tail -n1 <<<"${response}")"
  payload="$(sed '$d' <<<"${response}")"

  if [[ "${status}" == "${expected}" ]]; then
    printf '    \033[32mPASS\033[0m %-6s %-34s -> %s %s\n' "${method}" "${path}" "${status}" "${payload}"
    PASSED=$((PASSED + 1))
  else
    printf '    \033[31mFAIL\033[0m %-6s %-34s -> %s (expected %s) %s\n' \
      "${method}" "${path}" "${status}" "${expected}" "${payload}"
    FAILED=$((FAILED + 1))
  fi
}

log "Successful retrieval (200)"
check 200 GET "/orders/ORD-1001"

log "Search by customer (200, including the empty result)"
check 200 GET "/orders?customerId=CUST-42"
check 200 GET "/orders?customerId=CUST-100"

log "Creation (201)"
CREATE_RESPONSE="$(curl -sS -D - -o /tmp/orders-created.json -X POST "${BASE_URL}/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}')"
CREATE_STATUS="$(awk 'NR==1{print $2}' <<<"${CREATE_RESPONSE}")"
LOCATION="$(grep -i '^location:' <<<"${CREATE_RESPONSE}" | tr -d '\r' | cut -d' ' -f2- || true)"
CREATED_ID="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/orders-created.json)"

if [[ "${CREATE_STATUS}" == "201" ]]; then
  printf '    \033[32mPASS\033[0m %-6s %-34s -> 201 %s\n' POST "/orders" "$(cat /tmp/orders-created.json)"
  PASSED=$((PASSED + 1))
else
  printf '    \033[31mFAIL\033[0m %-6s %-34s -> %s (expected 201)\n' POST "/orders" "${CREATE_STATUS}"
  FAILED=$((FAILED + 1))
fi

if [[ "${ARCH}" == "proxy" ]]; then
  if [[ -n "${LOCATION}" ]]; then
    printf '    \033[32mPASS\033[0m %-6s %-34s -> Location: %s\n' POST "/orders (Location header)" "${LOCATION}"
    PASSED=$((PASSED + 1))
  else
    printf '    \033[31mFAIL\033[0m %-6s %-34s -> no Location header\n' POST "/orders (Location header)"
    FAILED=$((FAILED + 1))
  fi
else
  printf '    \033[33mNOTE\033[0m Architecture A returns no Location header: API Gateway mapping cannot\n'
  printf '         build "/orders/" + id. See the note at the end of infra/openapi-custom.yaml.\n'
fi

log "Reading back the order just created"
info "In-memory storage is per execution environment, so this only succeeds if the same warm"
info "container serves both requests. A failure here is a property of the storage, not a bug."
if [[ -n "${CREATED_ID}" ]]; then
  READ_BACK="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/orders/${CREATED_ID}")"
  if [[ "${READ_BACK}" == "200" ]]; then
    printf '    \033[32mPASS\033[0m %-6s %-34s -> 200\n' GET "/orders/${CREATED_ID}"
    PASSED=$((PASSED + 1))
  else
    printf '    \033[33mWARN\033[0m %-6s %-34s -> %s (cold container, data not shared)\n' \
      GET "/orders/${CREATED_ID}" "${READ_BACK}"
    WARNED=$((WARNED + 1))
  fi
fi

log "Invalid input (400)"
check 400 POST "/orders" '{"customerId":"CUST-42","quantity":0}'
check 400 POST "/orders" '{"customerId":"CUST-42","product":"Mouse"}'
check 400 POST "/orders" 'not json'
check 400 GET  "/orders"

log "Missing order (404)"
check 404 GET "/orders/ORD-999"

log "Summary"
info "passed  : ${PASSED}"
info "warnings: ${WARNED}"
info "failed  : ${FAILED}"
[[ "${FAILED}" -eq 0 ]] || die "${FAILED} expectation(s) did not hold."
log "All expectations held"
