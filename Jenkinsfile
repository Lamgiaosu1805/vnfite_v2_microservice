pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'FORCE_DEPLOY_ALL',
            defaultValue: false,
            description: 'Deploy tất cả services, bỏ qua change detection'
        )
        booleanParam(
            name: 'SYNC_ENV_ONLY',
            defaultValue: false,
            description: 'Chỉ cập nhật file .env lên server, không build/deploy gì cả'
        )
    }

    environment {
        TEST_SERVER = '42.113.122.119'
        LIVE_SERVER = '42.113.122.118'
        APP_DIR     = '/root/p2p-lending'
    }

    stages {
        // ── Bước 1: Copy file .env từ Jenkins lên server ──────────
        // Chạy MỌI LÚC — đảm bảo server luôn có config mới nhất trước khi build
        stage('Sync .env to server') {
            steps {
                script { syncEnv() }
            }
        }

        // ── Bước 2: Build/deploy chỉ service nào thay đổi ─────────
        stage('auth-service') {
            when {
                anyOf {
                    changeset 'apps/api/auth-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { deployService('auth-service', 'p2p-auth-service') }
            }
        }

        stage('loan-service') {
            when {
                anyOf {
                    changeset 'apps/api/loan-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { deployService('loan-service', 'p2p-loan-service') }
            }
        }

        stage('matching-service') {
            when {
                anyOf {
                    changeset 'apps/api/matching-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { deployService('matching-service', 'p2p-matching-service') }
            }
        }

        stage('cms-service') {
            when {
                anyOf {
                    changeset 'apps/api/cms-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { deployService('cms-service', 'p2p-cms-service') }
            }
        }

        stage('notification-service') {
            when {
                anyOf {
                    changeset 'apps/api/notification-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { deployService('notification-service', 'p2p-notification-service') }
            }
        }

        stage('nginx') {
            when {
                anyOf {
                    changeset 'nginx/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
                not { expression { return params.SYNC_ENV_ONLY } }
            }
            steps {
                script { reloadNginx() }
            }
        }
    }

    post {
        success {
            echo "Hoàn thành — branch: ${env.BRANCH_NAME}, server: ${getTargetServer()}"
        }
        failure {
            echo "Thất bại — xem logs bên trên để debug"
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

def getTargetServer() {
    return env.BRANCH_NAME == 'release' ? env.LIVE_SERVER : env.TEST_SERVER
}

def getSshCredentialId() {
    return env.BRANCH_NAME == 'release' ? 'ssh-live-server' : 'ssh-test-server'
}

def getEnvCredentialId() {
    // Secret file credential chứa toàn bộ nội dung file .env
    // Tên phải khớp với credential bạn tạo trong Jenkins UI
    return env.BRANCH_NAME == 'release' ? 'env-file-live' : 'env-file-test'
}

// Copy file .env từ Jenkins Credentials lên server
def syncEnv() {
    def server = getTargetServer()
    withCredentials([
        sshUserPrivateKey(credentialsId: getSshCredentialId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER'),
        file(credentialsId: getEnvCredentialId(), variable: 'ENV_FILE')
    ]) {
        sh """
            scp -i "\$SSH_KEY" -o StrictHostKeyChecking=no "\$ENV_FILE" \$SSH_USER@${server}:${env.APP_DIR}/.env
        """
    }
}

def deployService(String serviceName, String containerName) {
    def server = getTargetServer()
    def branch = env.BRANCH_NAME ?: 'main'
    withCredentials([sshUserPrivateKey(credentialsId: getSshCredentialId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${server} \
                'cd ${env.APP_DIR} && git pull origin ${branch} && docker compose build ${serviceName} && docker compose up -d ${serviceName} && docker image prune -f'
        """
    }
}

def reloadNginx() {
    def server = getTargetServer()
    def branch = env.BRANCH_NAME ?: 'main'
    withCredentials([sshUserPrivateKey(credentialsId: getSshCredentialId(), keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${server} \
                'cd ${env.APP_DIR} && git pull origin ${branch} && docker compose restart nginx'
        """
    }
}
