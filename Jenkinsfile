pipeline {
    agent { docker { image 'gradle:jre11' } }
    stages {
        stage('Build') {
            steps {
                sh 'gradle build -x test'
            }
        }
        stage('Test') {
            steps {
                sh 'gradle test'
            }
        }
        /*stage('Upload') {
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'my_twine', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    // Some password related push
                }
                sshagent(['my SSH']) {
                    // Some SSH key related push
                }
            }
        }*/
    }
}
