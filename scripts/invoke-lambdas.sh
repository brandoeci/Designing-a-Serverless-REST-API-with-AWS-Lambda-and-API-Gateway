#!/usr/bin/env bash
# Part II: invokes the deployed functions directly with JSON documents, with no API Gateway in
# the picture, and prints what each one returned.
#
#   scripts/invoke-lambdas.sh
#
# This is the evidence that the JSON -> Java object -> JSON conversion is done by the Lambda Java
# runtime and not by any code in this project.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

require_credentials
cd "${PROJECT_DIR}"
mkdir -p "${DEPLOY_DIR}/invocations"

invoke() {
  local label="$1" function_name="$2" payload="$3"
  local output="${DEPLOY_DIR}/invocations/$(echo "${label}" | tr ' /{}?=' '-').json"

  log "${label}"
  info "function : ${function_name}"
  info "payload  : ${payload}"

  local status
  status="$(aws lambda invoke \
    --function-name "${function_name}" \
    --payload "${payload}" \
    --cli-binary-format raw-in-base64-out \
    --region "${REGION}" \
    --query 'FunctionError' --output text \
    "${output}")"

  if [[ "${status}" == "None" ]]; then
    info "result   : SUCCESS"
  else
    info "result   : FUNCTION ERROR (${status}) - this is how a mapped 4xx looks to API Gateway"
  fi
  info "response : $(cat "${output}")"
}

# ---- Architecture A: two different inputs per operation, as the workshop asks ----

invoke "A1 get existing order"    "${GET_FUNCTION}"    '{"id":"ORD-1001"}'
invoke "A2 get missing order"     "${GET_FUNCTION}"    '{"id":"ORD-999"}'
invoke "A3 create order"          "${CREATE_FUNCTION}" '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'
invoke "A4 create another order"  "${CREATE_FUNCTION}" '{"customerId":"CUST-7","product":"Studio Display","quantity":3}'
invoke "A5 create invalid order"  "${CREATE_FUNCTION}" '{"customerId":"CUST-42","quantity":0}'
invoke "A6 find customer orders"  "${FIND_FUNCTION}"   '{"customerId":"CUST-42"}'
invoke "A7 find unknown customer" "${FIND_FUNCTION}"   '{"customerId":"CUST-100"}'
invoke "A8 delete an order"       "${DELETE_FUNCTION}" '{"id":"ORD-1003"}'
invoke "A9 delete missing order"  "${DELETE_FUNCTION}" '{"id":"ORD-999"}'

# ---- Architecture B: the same function receives a whole HTTP request ----

log "B1 proxy handler with a saved API Gateway event"
info "function : ${PROXY_FUNCTION}"
info "payload  : orders-api/src/test/resources/events/get-order.json"
aws lambda invoke \
  --function-name "${PROXY_FUNCTION}" \
  --payload "fileb://orders-api/src/test/resources/events/get-order.json" \
  --region "${REGION}" \
  --query 'FunctionError' --output text \
  "${DEPLOY_DIR}/invocations/B1-proxy-get-order.json" >/dev/null
info "response : $(cat "${DEPLOY_DIR}/invocations/B1-proxy-get-order.json")"

log "Responses were also written to ${DEPLOY_DIR}/invocations/"
