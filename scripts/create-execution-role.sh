#!/usr/bin/env bash
# Creates the IAM role the Lambda functions run as.
#
#   scripts/create-execution-role.sh
#
# AWS Academy provides LabRole and forbids creating roles, so this script is for personal or
# organisation accounts, where no such role exists. It is idempotent: run it twice and the second
# run simply reports the role that is already there.
#
# The role gets exactly one managed policy, AWSLambdaBasicExecutionRole, which allows writing to
# CloudWatch Logs and nothing else. The Orders application talks to no other AWS service - its
# storage is an in-memory map - so anything beyond that would be permission this code cannot use.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

require_credentials

ROLE_NAME="${ROLE_NAME:-orders-api-lambda-role}"
BASIC_EXECUTION_POLICY="arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"

# Only the Lambda service may assume it.
TRUST_POLICY='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}'

if aws iam get-role --role-name "${ROLE_NAME}" >/dev/null 2>&1; then
  log "Role ${ROLE_NAME} already exists"
else
  log "Creating role ${ROLE_NAME}"
  aws iam create-role \
    --role-name "${ROLE_NAME}" \
    --assume-role-policy-document "${TRUST_POLICY}" \
    --description "Execution role for the Orders API workshop functions" \
    --query 'Role.Arn' --output text
fi

aws iam attach-role-policy --role-name "${ROLE_NAME}" --policy-arn "${BASIC_EXECUTION_POLICY}"
info "attached ${BASIC_EXECUTION_POLICY}"

ROLE_ARN="$(aws iam get-role --role-name "${ROLE_NAME}" --query 'Role.Arn' --output text)"

# IAM is eventually consistent: Lambda can reject a role that was created seconds ago with
# "The role defined for the function cannot be assumed by Lambda". Waiting here is cheaper than
# debugging that error during deployment.
log "Waiting for IAM to propagate"
sleep 12

log "Done"
info "role ARN: ${ROLE_ARN}"
info "The other scripts pick this role up automatically; to be explicit, export it:"
info "  export EXECUTION_ROLE_ARN=${ROLE_ARN}"
