#!/usr/bin/env bash
# Runs the tests and produces the deployment artifact.
#
#   scripts/build.sh

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

log "mvn clean package (tests included)"
cd "${PROJECT_DIR}/orders-api"
mvn -B clean package

log "Artifact"
ls -lh target/orders-api.jar
info "Shaded JAR: application classes + aws-lambda-java-core + aws-lambda-java-events + Jackson."
info "Lambda does not resolve Maven dependencies, so everything the function needs must be inside."
