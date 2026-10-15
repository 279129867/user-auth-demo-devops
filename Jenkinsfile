pipeline {
    agent any

    environment {
        TCR_REGISTRY = 'ccr.ccs.tencentyun.com'
        TCR_NAMESPACE = '279129867'
        IMAGE_NAME = 'user-auth-demo'
    }

    stages {
        stage('拉取代码') {
            steps {
                git branch: 'main', url: 'https://gitee.com/a279129867/user-auth-demo.git'
            }
        }

        stage('Maven构建') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    args '-v /root/.m2:/root/.m2'
                }
            }
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        stage('Docker构建') {
            steps {
                sh 'docker build -t $TCR_REGISTRY/$TCR_NAMESPACE/$IMAGE_NAME:$BUILD_NUMBER .'
            }
        }

        stage('推送镜像') {
            steps {
                sh 'echo $TCR_PASSWORD | docker login $TCR_REGISTRY -u $TCR_USERNAME --password-stdin'
                sh 'docker push $TCR_REGISTRY/$TCR_NAMESPACE/$IMAGE_NAME:$BUILD_NUMBER'
            }
        }
    }

    post {
        success {
            echo '构建成功！'
        }
        failure {
            echo '构建失败！'
        }
    }
}
