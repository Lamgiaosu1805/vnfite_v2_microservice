#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/scripts/ci/common.sh"

service="${1:?Usage: deploy-service.sh <service-name> <test|prod>}"
environment="${2:?Usage: deploy-service.sh <service-name> <test|prod>}"

require_service "$service"

case "$environment" in
  test|prod) ;;
  *)
    echo "Environment must be test or prod" >&2
    exit 2
    ;;
esac

compose_service="$(service_field "$service" compose)"

cd "$ROOT_DIR"
compose up -d --build --no-deps "$compose_service"
compose ps "$compose_service"
