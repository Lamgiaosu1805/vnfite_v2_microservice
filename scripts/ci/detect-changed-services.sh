#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/scripts/ci/common.sh"

BASE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

if [[ -z "$BASE_REF" ]]; then
  if git rev-parse HEAD~1 >/dev/null 2>&1; then
    BASE_REF="HEAD~1"
  else
    all_services
    exit 0
  fi
fi

changed_files="$(git diff --name-only "$BASE_REF" "$HEAD_REF" || true)"

if [[ -z "$changed_files" ]]; then
  exit 0
fi

build_all=false
services=()

while IFS= read -r file; do
  [[ -z "$file" ]] && continue

  case "$file" in
    pom.xml|docker-compose.yml|nginx/*|infra/*|packages/*|scripts/ci/*|ci/jenkins/*)
      build_all=true
      ;;
    apps/api/auth-service/*)
      services+=("auth-service")
      ;;
    apps/api/loan-service/*)
      services+=("loan-service")
      ;;
    apps/api/matching-service/*)
      services+=("matching-service")
      ;;
    apps/api/cms-service/*)
      services+=("cms-service")
      ;;
    apps/api/notification-service/*)
      services+=("notification-service")
      ;;
    apps/cms/*)
      services+=("cms-web")
      ;;
  esac
done <<< "$changed_files"

if [[ "$build_all" == "true" ]]; then
  all_services
else
  printf '%s\n' "${services[@]}" | awk 'NF && !seen[$0]++'
fi

