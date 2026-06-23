#!/usr/bin/env sh
set -eu

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_NAMESPACE="${REDIS_NAMESPACE:-${APP_REDIS_NAMESPACE:-${SPRING_PROFILES_ACTIVE:-dev}}}"
CACHE_PATTERNS="${CACHE_PATTERNS:-${REDIS_NAMESPACE}:loan-service:loans::* ${REDIS_NAMESPACE}:loan-service:loan_by_id::* ${REDIS_NAMESPACE}:cms-service:dashboard_stats::* ${REDIS_NAMESPACE}:cms-service:dashboard_chart::*}"

# Build auth args — only include -a if REDIS_PASSWORD is set and non-empty
if [ -n "${REDIS_PASSWORD:-}" ]; then
  AUTH_ARGS="-a ${REDIS_PASSWORD}"
else
  AUTH_ARGS=""
fi

echo "Resetting Redis cache patterns: ${CACHE_PATTERNS}"

for pattern in ${CACHE_PATTERNS}; do
  echo "Clearing Redis keys matching: ${pattern}"
  # shellcheck disable=SC2086
  keys="$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${AUTH_ARGS} --scan --pattern "${pattern}" 2>/dev/null || true)"
  if [ -n "${keys}" ]; then
    # shellcheck disable=SC2086
    printf '%s\n' "${keys}" | xargs redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${AUTH_ARGS} DEL 2>/dev/null >/dev/null
    echo "  Cleared $(printf '%s\n' "${keys}" | wc -l | tr -d ' ') key(s)"
  else
    echo "  No keys found"
  fi
done
