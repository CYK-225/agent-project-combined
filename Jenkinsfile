pipeline {
    agent any
    
    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9'
    }
    
    environment {
        APP_NAME = 'uagent-project-new'
        DEPLOY_DIR = '/opt/apps/uagent-project-new'
    }
    
    stages {
        stage('检出代码') {
            steps {
                checkout scm
            }
        }
        
        stage('编译打包') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        
        stage('部署') {
            steps {
                sshPublisher(publishers: [
                    sshPublisherDesc(configName: 'WSL-Server', 
                        transfers: [
                            sshTransfer(
                                sourceFiles: 'start/target/*.jar',
                                removePrefix: 'start/target',
                                remoteDirectory: '/opt/apps/uagent-project-new',
                                execCommand: '''
                                    cd /opt/apps/uagent-project-new
                                    pkill -f uagent-project-new || true
                                    nohup java -jar *.jar > app.log 2>&1 &
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
