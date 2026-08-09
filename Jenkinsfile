pipeline {
    agent any

    tools {
        maven 'maven-3.9.9'
    }

    environment {
        APP_DIR = '/var/lib/jenkins/apps/uagent'
    }

    stages {
        stage('构建') {
            steps {
                dir('uagent-project') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }
        stage('部署') {
            steps {
                sh """
                    mkdir -p ${APP_DIR}
                    cp uagent-project/start/target/uagent.jar ${APP_DIR}/uagent.jar.new
                    bash ${APP_DIR}/restart.sh
                """
            }
        }
    }

    post {
        success {
            echo '构建与部署成功'
        }
        failure {
            echo '构建或部署失败，请查看日志'
        }
    }
}
