#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/scripts/ci/common.sh"

service="${1:?Usage: build-service.sh <service-name>}"
require_service "$service"

compose_service="$(service_field "$service" compose)"
kind="$(service_field "$service" kind)"
path="$(service_field "$service" path)"

cd "$ROOT_DIR"

case "$kind" in
  java)
    mvn -q -pl "$path" -am test
    ;;
  node)
    npm ci --prefix "$path"
    npm run build --prefix "$path"
    ;;
esac

compose build "$compose_service"

