#!/usr/bin/env sh
set -eu

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

REDIS_SERVICE="${REDIS_SERVICE:-redis}"
REDIS_NAMESPACE="${REDIS_NAMESPACE:-${APP_REDIS_NAMESPACE:-${SPRING_PROFILES_ACTIVE:-dev}}}"
CACHE_PATTERNS="${CACHE_PATTERNS:-${REDIS_NAMESPACE}:loan-service:loans::* ${REDIS_NAMESPACE}:loan-service:loan_by_id::* ${REDIS_NAMESPACE}:cms-service:dashboard_stats::* ${REDIS_NAMESPACE}:cms-service:dashboard_chart::*}"

echo "Resetting Redis cache patterns: ${CACHE_PATTERNS}"

docker compose exec -T "${REDIS_SERVICE}" sh -s -- ${CACHE_PATTERNS} <<'EOF'
set -eu

for pattern in "$@"; do
  echo "Clearing Redis keys matching: ${pattern}"
  keys="$(redis-cli --scan --pattern "${pattern}" || true)"
  if [ -n "${keys}" ]; then
    printf '%s\n' "${keys}" | xargs redis-cli DEL >/dev/null
  fi
done
EOF
