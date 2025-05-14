pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *') // Polls GitHub every 5 minutes
    }

    environment {
        STAGING_SERVER = 'ec2-user@staging-server-ip'
        PROD_SERVER = 'ec2-user@prod-server-ip'
        SSH_KEY = credentials('ssh-key-id') // Add your SSH private key to Jenkins credentials
    }

    stages {

        stage('Build') {
            steps {
                echo 'Building the project...'
                sh 'mvn clean package'
            }
        }

        stage('Unit and Integration Tests') {
            steps {
                echo 'Running unit and integration tests...'
                sh 'mvn test' // Runs both unit and integration tests
            }
        }

        stage('Code Analysis') {
            steps {
                echo 'Analyzing code with SonarQube...'
                withSonarQubeEnv('My SonarQube Server') {
                    sh 'mvn sonar:sonar'
                }
            }
        }

        stage('Security Scan') {
            steps {
                echo 'Running security scan...'
                sh 'dependency-check.sh --project my-app --scan . --format HTML --out dependency-check-report.html'
            }
        }

        stage('Deploy to Staging') {
            steps {
                echo 'Deploying to staging...'
                sh '''
                    scp -i $SSH_KEY target/my-app.jar $STAGING_SERVER:/home/ec2-user/
                    ssh -i $SSH_KEY $STAGING_SERVER 'java -jar /home/ec2-user/my-app.jar &'
                '''
            }
        }

        stage('Integration Tests on Staging') {
            steps {
                echo 'Running integration tests on staging...'
                sh 'newman run postman-collection.json --environment staging-env.json'
            }
        }

        stage('Deploy to Production') {
            steps {
                input message: "Approve deployment to production?"
                echo 'Deploying to production...'
                sh '''
                    scp -i $SSH_KEY target/my-app.jar $PROD_SERVER:/home/ec2-user/
                    ssh -i $SSH_KEY $PROD_SERVER 'pkill -f my-app.jar || true && java -jar /home/ec2-user/my-app.jar &'
                '''
            }
        }
    }

    post {
        always {
            echo 'Pipeline execution completed.'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}
