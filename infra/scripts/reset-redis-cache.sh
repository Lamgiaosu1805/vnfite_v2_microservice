#!/usr/bin/env sh
set -eu

REDIS_SERVICE="${REDIS_SERVICE:-redis}"
CACHE_PATTERNS="${CACHE_PATTERNS:-loans::* loan_by_id::* dashboard_stats::* dashboard_chart::*}"

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
