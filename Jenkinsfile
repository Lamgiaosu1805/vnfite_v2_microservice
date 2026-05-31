pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'FORCE_DEPLOY_ALL',
            defaultValue: false,
            description: 'Deploy tất cả services, bỏ qua change detection'
        )
    }

    environment {
        TEST_SERVER = '42.113.122.119'
        LIVE_SERVER = '42.113.122.118'
        // Đường dẫn thư mục app trên cả 2 server
        APP_DIR = '/root/p2p-lending'
    }

    stages {
        stage('auth-service') {
            when {
                anyOf {
                    changeset 'apps/api/auth-service/**'
                    expression { return params.FORCE_DEPLOY_ALL }
                }
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
            }
            steps {
                script { reloadNginx() }
            }
        }
    }

    post {
        success {
            echo "Deploy hoàn thành — branch: ${env.BRANCH_NAME}, server: ${getTargetServer()}"
        }
        failure {
            echo "Deploy thất bại — xem logs ở trên để debug"
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

def getTargetServer() {
    return env.BRANCH_NAME == 'release' ? env.LIVE_SERVER : env.TEST_SERVER
}

def getCredentialId() {
    // Tên credentials phải khớp với những gì bạn tạo trong Jenkins UI
    return env.BRANCH_NAME == 'release' ? 'ssh-live-server' : 'ssh-test-server'
}

def deployService(String serviceName, String containerName) {
    def server   = getTargetServer()
    def credId   = getCredentialId()
    def branch   = env.BRANCH_NAME ?: 'main'

    withCredentials([sshUserPrivateKey(
        credentialsId : credId,
        keyFileVariable : 'SSH_KEY',
        usernameVariable: 'SSH_USER'
    )]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${server} \
                'cd ${env.APP_DIR} && git pull origin ${branch} && docker compose build ${serviceName} && docker compose up -d ${serviceName} && docker image prune -f'
        """
    }
}

def reloadNginx() {
    def server = getTargetServer()
    def credId = getCredentialId()
    def branch = env.BRANCH_NAME ?: 'main'

    withCredentials([sshUserPrivateKey(
        credentialsId : credId,
        keyFileVariable : 'SSH_KEY',
        usernameVariable: 'SSH_USER'
    )]) {
        sh """
            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no \$SSH_USER@${server} \
                'cd ${env.APP_DIR} && git pull origin ${branch} && docker compose restart nginx'
        """
    }
}
