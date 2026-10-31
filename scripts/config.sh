#!/usr/bin/env bash
# Shared configuration for every script in this folder.
# Override anything by exporting it before calling a script, e.g.:
#   REGION=us-west-2 scripts/deploy-lambdas.sh

set -euo pipefail

# ---------------------------------------------------------------- AWS account

REGION="${REGION:-$(aws configure get region 2>/dev/null || echo us-east-1)}"

ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"

# The role the functions run as. Two kinds of account need two different answers:
#   - AWS Academy / Learner Lab forbids creating roles but provides LabRole;
#   - a personal or organisation account has no LabRole, so scripts/create-execution-role.sh
#     creates orders-api-lambda-role.
# Resolved at deployment time rather than here, because a student in Academy is usually not
# allowed to call iam:GetRole and this must not fail for them.
execution_role_arn() {
  if [[ -n "${EXECUTION_ROLE_ARN:-}" ]]; then
    echo "${EXECUTION_ROLE_ARN}"
    return
  fi
  for candidate in LabRole orders-api-lambda-role; do
    if aws iam get-role --role-name "${candidate}" >/dev/null 2>&1; then
      echo "arn:aws:iam::${ACCOUNT_ID}:role/${candidate}"
      return
    fi
  done
  # iam:GetRole denied, or neither role exists: assume Academy, and let Lambda report the truth.
  echo "arn:aws:iam::${ACCOUNT_ID}:role/LabRole"
}

# ---------------------------------------------------------------- artifact

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_RELATIVE_PATH="orders-api/target/orders-api.jar"
DEPLOY_DIR="${PROJECT_DIR}/.deploy"

# ---------------------------------------------------------------- Lambda functions

RUNTIME="java17"
MEMORY_SIZE="${MEMORY_SIZE:-512}"   # more memory also means more CPU, which shortens Java cold starts
TIMEOUT="${TIMEOUT:-15}"

# Architecture A: one function per operation, each receiving a document built by API Gateway.
GET_FUNCTION="${GET_FUNCTION:-orders-get-order}"
GET_HANDLER="com.eci.orders.adapter.lambda.custom.GetOrderHandler::handleRequest"

CREATE_FUNCTION="${CREATE_FUNCTION:-orders-create-order}"
CREATE_HANDLER="com.eci.orders.adapter.lambda.custom.CreateOrderHandler::handleRequest"

FIND_FUNCTION="${FIND_FUNCTION:-orders-find-orders-by-customer}"
FIND_HANDLER="com.eci.orders.adapter.lambda.custom.FindOrdersByCustomerHandler::handleRequest"

DELETE_FUNCTION="${DELETE_FUNCTION:-orders-delete-order}"
DELETE_HANDLER="com.eci.orders.adapter.lambda.custom.DeleteOrderHandler::handleRequest"

# Architecture B: one function for the whole API, receiving the HTTP request as an event.
PROXY_FUNCTION="${PROXY_FUNCTION:-orders-api-proxy}"
PROXY_HANDLER="com.eci.orders.adapter.lambda.proxy.OrdersApiHandler::handleRequest"

# ---------------------------------------------------------------- API Gateway

CUSTOM_API_NAME="${CUSTOM_API_NAME:-orders-api-custom}"
PROXY_API_NAME="${PROXY_API_NAME:-orders-api-proxy}"
STAGE="${STAGE:-dev}"

# ---------------------------------------------------------------- helpers

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

require_credentials() {
  aws sts get-caller-identity >/dev/null 2>&1 \
    || die "No usable AWS credentials. In AWS Academy, open the lab, click 'AWS Details' and copy the
       credentials into ~/.aws/credentials (they expire after a few hours)."
}

invoke_url() {
  local api_id="$1"
  echo "https://${api_id}.execute-api.${REGION}.amazonaws.com/${STAGE}"
}
