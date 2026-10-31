#!/usr/bin/env bash
# Creates or updates the five Lambda functions of the workshop from the same JAR.
#
#   scripts/deploy-lambdas.sh
#
# Four functions serve Architecture A (one per operation) and one serves Architecture B
# (the whole API). They share a single artifact: the handler string is what selects the entry point.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

require_credentials
cd "${PROJECT_DIR}"

[[ -f "${JAR_RELATIVE_PATH}" ]] || die "Missing ${JAR_RELATIVE_PATH}. Run scripts/build.sh first."

EXECUTION_ROLE_ARN="$(execution_role_arn)"
info "execution role: ${EXECUTION_ROLE_ARN}"

deploy_function() {
  local name="$1" handler="$2" description="$3"

  if aws lambda get-function --function-name "${name}" --region "${REGION}" >/dev/null 2>&1; then
    log "Updating ${name}"
    aws lambda update-function-code \
      --function-name "${name}" \
      --zip-file "fileb://${JAR_RELATIVE_PATH}" \
      --region "${REGION}" \
      --query 'LastModified' --output text
    aws lambda wait function-updated --function-name "${name}" --region "${REGION}"
    aws lambda update-function-configuration \
      --function-name "${name}" \
      --handler "${handler}" \
      --memory-size "${MEMORY_SIZE}" \
      --timeout "${TIMEOUT}" \
      --region "${REGION}" \
      --query 'FunctionArn' --output text
  else
    log "Creating ${name}"
    aws lambda create-function \
      --function-name "${name}" \
      --runtime "${RUNTIME}" \
      --role "${EXECUTION_ROLE_ARN}" \
      --handler "${handler}" \
      --zip-file "fileb://${JAR_RELATIVE_PATH}" \
      --memory-size "${MEMORY_SIZE}" \
      --timeout "${TIMEOUT}" \
      --description "${description}" \
      --region "${REGION}" \
      --query 'FunctionArn' --output text
  fi
  aws lambda wait function-updated --function-name "${name}" --region "${REGION}"
  info "handler = ${handler}"
}

deploy_function "${GET_FUNCTION}"    "${GET_HANDLER}"    "Architecture A - GET /orders/{id}"
deploy_function "${CREATE_FUNCTION}" "${CREATE_HANDLER}" "Architecture A - POST /orders"
deploy_function "${FIND_FUNCTION}"   "${FIND_HANDLER}"   "Architecture A - GET /orders?customerId="
deploy_function "${DELETE_FUNCTION}" "${DELETE_HANDLER}" "Architecture A - DELETE /orders/{id}"
deploy_function "${PROXY_FUNCTION}"  "${PROXY_HANDLER}"  "Architecture B - whole Orders API (proxy)"

log "Done"
info "Next: scripts/invoke-lambdas.sh (Part II) and scripts/deploy-api.sh (Parts III-V)."
