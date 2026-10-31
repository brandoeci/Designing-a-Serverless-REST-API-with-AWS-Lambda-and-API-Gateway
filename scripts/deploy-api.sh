#!/usr/bin/env bash
# Creates or updates both REST APIs from the OpenAPI definitions in infra/, deploys them to a
# stage and grants API Gateway permission to invoke the functions.
#
#   scripts/deploy-api.sh            # both architectures
#   scripts/deploy-api.sh proxy      # only Architecture B
#   scripts/deploy-api.sh custom     # only Architecture A
#
# The resulting invoke URLs are printed and stored in .deploy/outputs.env, which
# scripts/smoke-test.sh reads.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

require_credentials
cd "${PROJECT_DIR}"
mkdir -p "${DEPLOY_DIR}"

TARGET="${1:-both}"

# Replaces the {{PLACEHOLDERS}} of a definition with this account's values.
render() {
  local source_file="$1" target_file="$2"
  sed -e "s|{{REGION}}|${REGION}|g" \
      -e "s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g" \
      -e "s|{{GET_FUNCTION}}|${GET_FUNCTION}|g" \
      -e "s|{{CREATE_FUNCTION}}|${CREATE_FUNCTION}|g" \
      -e "s|{{FIND_FUNCTION}}|${FIND_FUNCTION}|g" \
      -e "s|{{DELETE_FUNCTION}}|${DELETE_FUNCTION}|g" \
      -e "s|{{PROXY_FUNCTION}}|${PROXY_FUNCTION}|g" \
      "${source_file}" > "${target_file}"
}

# API Gateway may only invoke a function if the function's resource policy says so.
allow_invoke() {
  local function_name="$1" api_id="$2"
  local statement_id="apigw-${api_id}"

  aws lambda remove-permission \
    --function-name "${function_name}" --statement-id "${statement_id}" \
    --region "${REGION}" >/dev/null 2>&1 || true

  aws lambda add-permission \
    --function-name "${function_name}" \
    --statement-id "${statement_id}" \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT_ID}:${api_id}/*/*/*" \
    --region "${REGION}" >/dev/null
  info "${function_name} may now be invoked by API ${api_id}"
}

existing_api_id() {
  aws apigateway get-rest-apis --region "${REGION}" \
    --query "items[?name=='$1'].id | [0]" --output text 2>/dev/null
}

deploy_api() {
  local api_name="$1" definition="$2" rendered="${DEPLOY_DIR}/$(basename "$2")"
  shift 2
  local functions=("$@")

  render "${definition}" "${rendered}"

  local api_id
  api_id="$(existing_api_id "${api_name}")"

  if [[ -n "${api_id}" && "${api_id}" != "None" ]]; then
    log "Updating API ${api_name} (${api_id})"
    aws apigateway put-rest-api \
      --rest-api-id "${api_id}" \
      --mode overwrite \
      --body "fileb://${rendered#"${PROJECT_DIR}/"}" \
      --region "${REGION}" \
      --query 'id' --output text >/dev/null
  else
    log "Importing API ${api_name}"
    api_id="$(aws apigateway import-rest-api \
      --body "fileb://${rendered#"${PROJECT_DIR}/"}" \
      --region "${REGION}" \
      --query 'id' --output text)"
  fi

  # The name in the OpenAPI document wins on import, so keep the configured name authoritative.
  aws apigateway update-rest-api --rest-api-id "${api_id}" --region "${REGION}" \
    --patch-operations "op=replace,path=/name,value=${api_name}" >/dev/null

  for function_name in "${functions[@]}"; do
    allow_invoke "${function_name}" "${api_id}"
  done

  aws apigateway create-deployment \
    --rest-api-id "${api_id}" \
    --stage-name "${STAGE}" \
    --description "Deployed by scripts/deploy-api.sh" \
    --region "${REGION}" \
    --query 'id' --output text >/dev/null

  info "stage      : ${STAGE}"
  info "invoke URL : $(invoke_url "${api_id}")"
  echo "${api_id}"
}

: > "${DEPLOY_DIR}/outputs.env.tmp"

if [[ "${TARGET}" == "both" || "${TARGET}" == "proxy" ]]; then
  PROXY_API_ID="$(deploy_api "${PROXY_API_NAME}" "infra/openapi-proxy.yaml" "${PROXY_FUNCTION}" | tail -1)"
  {
    echo "PROXY_API_ID=${PROXY_API_ID}"
    echo "PROXY_BASE_URL=$(invoke_url "${PROXY_API_ID}")"
  } >> "${DEPLOY_DIR}/outputs.env.tmp"
fi

if [[ "${TARGET}" == "both" || "${TARGET}" == "custom" ]]; then
  CUSTOM_API_ID="$(deploy_api "${CUSTOM_API_NAME}" "infra/openapi-custom.yaml" \
    "${GET_FUNCTION}" "${CREATE_FUNCTION}" "${FIND_FUNCTION}" "${DELETE_FUNCTION}" | tail -1)"
  {
    echo "CUSTOM_API_ID=${CUSTOM_API_ID}"
    echo "CUSTOM_BASE_URL=$(invoke_url "${CUSTOM_API_ID}")"
  } >> "${DEPLOY_DIR}/outputs.env.tmp"
fi

mv "${DEPLOY_DIR}/outputs.env.tmp" "${DEPLOY_DIR}/outputs.env"

log "Outputs"
cat "${DEPLOY_DIR}/outputs.env"
info "Next: scripts/smoke-test.sh"
