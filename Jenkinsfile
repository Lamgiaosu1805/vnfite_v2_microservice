pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'FORCE_DEPLOY_ALL',
            defaultValue: false,
            description: 'Deploy tất cả services (dùng cho lần đầu hoặc khi cần reset)'
        )
        booleanParam(
            name: 'SYNC_ENV_ONLY',
            defaultValue: false,
            description: 'Chỉ cập nhật file .env lên server, không build/deploy gì'
        )
    }

    environment {
        TEST_SERVER = '42.113.122.119'
        LIVE_SERVER = '42.113.122.118'
        APP_DIR     = '/root/p2p-lending'
    }

    stages {

        // ── Stage 1: Luôn chạy — đẩy .env mới nhất lên server ────
        stage('Sync .env') {
            steps {
                script { syncEnv() }
            }
        }

        // ── Stage 2-7: Chỉ build service nào thay đổi ─────────────
        stage('auth-service') {
            when {
                anyOf {
                    changeset 'apps/api/auth-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { deploy('auth-service', 'p2p-auth-service') } }
        }

        stage('loan-service') {
            when {
                anyOf {
                    changeset 'apps/api/loan-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { deploy('loan-service', 'p2p-loan-service') } }
        }

        stage('matching-service') {
            when {
                anyOf {
                    changeset 'apps/api/matching-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { deploy('matching-service', 'p2p-matching-service') } }
        }

        stage('cms-service') {
            when {
                anyOf {
                    changeset 'apps/api/cms-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { deploy('cms-service', 'p2p-cms-service') } }
        }

        stage('notification-service') {
            when {
                anyOf {
                    changeset 'apps/api/notification-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { deploy('notification-service', 'p2p-notification-service') } }
        }

        stage('nginx') {
            when {
                anyOf {
                    changeset 'nginx/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps { script { reloadNginx() } }
        }
    }

    post {
        success { echo "✅ Done — ${env.BRANCH_NAME} → ${targetServer()}" }
        failure  { echo "❌ Failed — check logs above" }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────

def targetServer() {
    return env.BRANCH_NAME == 'release' ? env.LIVE_SERVER : env.TEST_SERVER
}

def sshCredId() {
    // ID phải khớp với credential tạo trong Jenkins UI
    return env.BRANCH_NAME == 'release' ? 'ssh-live-server' : 'ssh-test-server'
}

def envCredId() {
    // ID phải khớp với credential tạo trong Jenkins UI
    return env.BRANCH_NAME == 'release' ? 'env-file-live' : 'env-file-test'
}

// Copy file .env từ Jenkins Credentials lên server đích
def syncEnv() {
    withCredentials([
        sshUserPrivateKey(credentialsId: sshCredId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER'),
        file(credentialsId: envCredId(), variable: 'ENV_FILE')
    ]) {
        sh 'scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "$ENV_FILE" $SSH_USER@' + targetServer() + ':' + env.APP_DIR + '/.env'
    }
}

// Build và restart đúng 1 service
def deploy(String svc, String container) {
    def branch = env.BRANCH_NAME ?: 'main'
    withCredentials([sshUserPrivateKey(credentialsId: sshCredId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${targetServer()} '
                set -e
                cd ${env.APP_DIR}
                git pull origin ${branch}
                docker compose build ${svc}
                docker compose up -d ${svc}
                docker image prune -f
            '
        """
    }
}

// Reload nginx config mà không rebuild
def reloadNginx() {
    def branch = env.BRANCH_NAME ?: 'main'
    withCredentials([sshUserPrivateKey(credentialsId: sshCredId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${targetServer()} '
                cd ${env.APP_DIR} && git pull origin ${branch} && docker compose restart nginx
            '
        """
    }
}
