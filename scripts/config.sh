#!/usr/bin/env bash
# Shared configuration for every script in this folder.
# Override anything by exporting it before calling a script, e.g.:
#   REGION=us-west-2 scripts/deploy-lambdas.sh

set -euo pipefail

# ---------------------------------------------------------------- AWS account

REGION="${REGION:-$(aws configure get region 2>/dev/null || echo us-east-1)}"

ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"

# AWS Academy / Learner Lab does not allow creating IAM roles, but it provides LabRole, which
# already has the permissions a Lambda needs (including CloudWatch Logs). On a normal account,
# export EXECUTION_ROLE_ARN with your own role.
EXECUTION_ROLE_ARN="${EXECUTION_ROLE_ARN:-arn:aws:iam::${ACCOUNT_ID}:role/LabRole}"

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
