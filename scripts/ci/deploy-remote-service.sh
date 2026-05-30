#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/scripts/ci/common.sh"

service="${1:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
environment="${2:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
ssh_user="${3:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
ssh_host="${4:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
repo_dir="${5:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
branch="${6:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"
env_file="${7:?Usage: deploy-remote-service.sh <service> <test|prod> <ssh-user> <ssh-host> <repo-dir> <branch> <env-file>}"

require_service "$service"

case "$environment" in
  test|prod) ;;
  *)
    echo "Environment must be test or prod" >&2
    exit 2
    ;;
esac

remote="ssh://${ssh_user}@${ssh_host}"
remote_tmp="/tmp/p2p-lending-${environment}.env"

scp -o StrictHostKeyChecking=accept-new "$env_file" "${ssh_user}@${ssh_host}:${remote_tmp}"

ssh -o StrictHostKeyChecking=accept-new "${ssh_user}@${ssh_host}" \
  "REPO_DIR='${repo_dir}' BRANCH='${branch}' SERVICE='${service}' ENVIRONMENT='${environment}' REMOTE_ENV='${remote_tmp}' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

cd "$REPO_DIR"
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

install -m 600 "$REMOTE_ENV" .env
rm -f "$REMOTE_ENV"

./scripts/ci/deploy-service.sh "$SERVICE" "$ENVIRONMENT"
REMOTE_SCRIPT

echo "Remote deploy completed via ${remote}${repo_dir}"
