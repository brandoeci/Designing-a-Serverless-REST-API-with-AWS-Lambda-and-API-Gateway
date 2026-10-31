#!/usr/bin/env bash
# Deletes everything the other scripts created: both REST APIs and the four functions.
#
#   scripts/teardown.sh
#
# Worth running at the end of an AWS Academy session so the next lab starts clean.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

require_credentials

delete_api() {
  local api_name="$1" api_id
  api_id="$(aws apigateway get-rest-apis --region "${REGION}" \
    --query "items[?name=='${api_name}'].id | [0]" --output text 2>/dev/null)"

  if [[ -n "${api_id}" && "${api_id}" != "None" ]]; then
    log "Deleting API ${api_name} (${api_id})"
    aws apigateway delete-rest-api --rest-api-id "${api_id}" --region "${REGION}"
  else
    info "API ${api_name} not found, nothing to delete"
  fi
}

delete_function() {
  local name="$1"
  if aws lambda get-function --function-name "${name}" --region "${REGION}" >/dev/null 2>&1; then
    log "Deleting function ${name}"
    aws lambda delete-function --function-name "${name}" --region "${REGION}"
  else
    info "Function ${name} not found, nothing to delete"
  fi
}

delete_api "${PROXY_API_NAME}"
delete_api "${CUSTOM_API_NAME}"

delete_function "${GET_FUNCTION}"
delete_function "${CREATE_FUNCTION}"
delete_function "${FIND_FUNCTION}"
delete_function "${PROXY_FUNCTION}"

rm -f "${DEPLOY_DIR}/outputs.env"
log "Done"
