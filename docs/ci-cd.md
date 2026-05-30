# CI/CD with Jenkins

Jenkins is the web UI for build and deploy control.

## Behavior

- `TARGET_SERVICE=auto` detects changed files and builds only affected services.
- `TARGET_SERVICE=<service>` builds one selected service manually.
- `TARGET_SERVICE=all` builds every service.
- `DEPLOY_ENV=none` builds only.
- `DEPLOY_ENV=test` deploys automatically when `AUTO_DEPLOY_TEST=true`.
- `DEPLOY_ENV=prod` always waits for a manual approval button in Jenkins.
- Test and production can deploy to different servers.
- Environment variables are managed from Jenkins Credentials as Secret files.

Supported services:

- `auth-service`
- `loan-service`
- `matching-service`
- `cms-service`
- `notification-service`
- `cms-web`

## Ubuntu Server Setup

Install Docker and Compose plugin on the server, then clone this repository.

```bash
cd p2p-lending/ci/jenkins
export P2P_REPO_DIR="$(cd ../.. && pwd)"
docker compose up -d --build
```

Open Jenkins:

```text
http://<server-ip>:8080
```

Get the first admin password:

```bash
docker logs p2p-jenkins
```

Create a Pipeline job:

```text
Definition: Pipeline script from SCM
SCM: Git
Repository URL: <your-repo-url>
Script Path: ci/jenkins/Jenkinsfile
```

If Jenkins runs from the provided Docker Compose file, keep the `P2P_REPO_DIR`
environment variable. The repository is mounted into the Jenkins container with
the same absolute path as the host, which lets Docker Compose bind mounts work
correctly during deploy.

## Configure Environment Variables in Jenkins UI

Do not commit real `.env` files. Store them in Jenkins:

```text
Manage Jenkins -> Credentials -> System -> Global credentials -> Add Credentials
```

Create these credentials:

```text
Kind: Secret file
ID: p2p-test-env-file
File: your test .env

Kind: Secret file
ID: p2p-prod-env-file
File: your production .env
```

The pipeline copies the selected Secret file to `.env` on the target server
right before deploy.

For SSH deploy to separate servers, add SSH credentials:

```text
Kind: SSH Username with private key
ID: p2p-test-ssh-key
Username: deploy
Private Key: <private key that can SSH to test server>

Kind: SSH Username with private key
ID: p2p-prod-ssh-key
Username: deploy
Private Key: <private key that can SSH to production server>
```

The `deploy` user on each server should be able to run Docker:

```bash
sudo usermod -aG docker deploy
```

Log out and log back in after changing the group.

## Deploy to Test Now

On the test server:

```bash
sudo mkdir -p /opt/p2p-lending
sudo chown -R deploy:deploy /opt/p2p-lending
cd /opt/p2p-lending
git clone <your-repo-url> .
```

In Jenkins job parameters:

```text
TARGET_SERVICE=auto
DEPLOY_ENV=test
AUTO_DEPLOY_TEST=true
GIT_BRANCH=main
TEST_SERVER_HOST=<test-server-ip>
TEST_SERVER_USER=deploy
TEST_REPO_DIR=/opt/p2p-lending
TEST_SSH_CREDENTIAL_ID=p2p-test-ssh-key
TEST_ENV_FILE_CREDENTIAL_ID=p2p-test-env-file
```

Click `Build`. Jenkins will:

1. Detect changed services.
2. Build/test only those services.
3. SSH to the test server.
4. Pull the selected branch.
5. Write the test `.env`.
6. Rebuild and restart only the selected compose service.

If Jenkins runs directly on the test server, leave `TEST_SERVER_HOST` empty.
It will deploy locally in `P2P_REPO_DIR`.

## Production Server

Production uses separate parameters:

```text
PROD_SERVER_HOST=<prod-server-ip>
PROD_SERVER_USER=deploy
PROD_REPO_DIR=/opt/p2p-lending
PROD_SSH_CREDENTIAL_ID=p2p-prod-ssh-key
PROD_ENV_FILE_CREDENTIAL_ID=p2p-prod-env-file
```

Set:

```text
DEPLOY_ENV=prod
```

Jenkins will pause and require manual approval before deploying to production.

## Deployment Model

Local deploy runs:

```bash
docker compose up -d --build --no-deps <service>
```

Remote deploy SSHs to the selected test/prod server, pulls the branch, installs
the environment file, then runs the same service-level deploy command. This
recreates only the selected service container and leaves the rest running.

For production, keep `DEPLOY_ENV=none` as the default. Select `prod` only for a
controlled release; Jenkins will require manual confirmation before deploying.
