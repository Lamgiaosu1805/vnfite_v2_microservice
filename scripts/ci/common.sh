#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVICES_FILE="${ROOT_DIR}/scripts/ci/services.conf"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

all_services() {
  awk -F'|' 'NF == 4 && $1 !~ /^#/ { print $1 }' "$SERVICES_FILE"
}

service_field() {
  local service="$1"
  local field="$2"
  awk -F'|' -v service="$service" -v field="$field" '
    NF == 4 && $1 == service {
      if (field == "compose") print $2;
      if (field == "path") print $3;
      if (field == "kind") print $4;
    }
  ' "$SERVICES_FILE"
}

require_service() {
  local service="$1"
  if ! all_services | grep -qx "$service"; then
    echo "Unknown service: $service" >&2
    echo "Valid services:" >&2
    all_services >&2
    exit 2
  fi
}

