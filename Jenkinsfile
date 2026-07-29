pipeline {
    agent any
    
    tools {
        nodejs 'NodeJS-22'
    }
    
    environment {
        DEPLOY_DIR = '/var/www/information-tool-manage-frontend'
    }
    
    stages {
        stage('检出代码') {
            steps {
                checkout scm
            }
        }
        
        stage('安装依赖') {
            steps {
                sh 'npm install'
            }
        }
        
        stage('构建') {
            steps {
                sh 'npm run build'
            }
        }
        
        stage('部署') {
            steps {
                sshPublisher(publishers: [
                    sshPublisherDesc(configName: 'WSL-Server', 
                        transfers: [
                            sshTransfer(
                                sourceFiles: 'dist/**',
                                removePrefix: 'dist',
                                remoteDirectory: '/var/www/information-tool-manage-frontend',
                                execCommand: '''
                                    systemctl restart nginx || true
                                '''
                            )
                        ]
                    )
                ])
            }
        }
    }
    
    post {
        success {
            echo '✅ 构建部署成功!'
        }
        failure {
            echo '❌ 构建部署失败!'
        }
    }
}
