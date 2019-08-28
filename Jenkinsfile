def get_offenderpollpush_version() {
    sh '''
    #!/bin/bash +x
    # App version is set in sbt build config 
    BUILD_NUMBER=$(grep "version :=" build.sbt | awk '{print $3}' | sed 's/\"//g')
    echo \$BUILD_NUMBER > ./offenderpollpush.version
    '''
    return readFile("./offenderpollpush.version")
}

pipeline {
    agent { label "jenkins_slave" }

    environment {
        docker_image = "hmpps/new-tech-offender-pollpush"
        aws_region = 'eu-west-2'
        ecr_repo = ''
        offenderpollpush_VERSION = get_offenderpollpush_version()
    }

    options { 
        disableConcurrentBuilds() 
    }

    stages {
        stage ('Notify build started') {
            steps {
                slackSend(message: "Build Started - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL.replace('http://', 'https://').replace(':8080', '')}|Open>)")
            }
        }

        stage ('Initialize') {
            steps {
                sh '''
                    #!/bin/bash +x
                    echo "PATH = ${PATH}"
                    echo "offenderpollpush_VERSION = ${offenderpollpush_VERSION}"
                '''
            }
        }

        stage('Verify Prerequisites') {
            steps {
                sh '''
                    #!/bin/bash +x
                    echo "Testing AWS Connectivity and Credentials"
                    aws sts get-caller-identity
                '''
            }
        }
        
        stage('SBT Test') {
            steps {
                sh '''
                    #!/bin/bash +x
                    make sbt-test;
                '''
            }
        }

        stage('SBT Assembly') {
            steps {
                sh '''
                    #!/bin/bash +x
                    make sbt-assembly;
                '''
            }
        }

        stage('Get ECR Login') {
            steps {
                sh '''
                    #!/bin/bash +x
                    make ecr-login
                '''
                // Stash the ecr repo to save a repeat aws api call
                stash includes: 'ecr.repo', name: 'ecr.repo'
            }
        }
        stage('Build Docker image') {
           steps {
                unstash 'ecr.repo'
                sh '''
                    #!/bin/bash +x
                    make build offenderpollpush_version=${offenderpollpush_VERSION}
                '''
            }
        }
        stage('Image Tests') {
            steps {
                // Run dgoss tests
                sh '''
                    #!/bin/bash +x
                    make test
                '''
            }
        }
        stage('Push image') {
            steps{
                unstash 'ecr.repo'
                sh '''
                    #!/bin/bash +x
                    make push offenderpollpush_version=${offenderpollpush_VERSION}
                '''
                
            }            
        }
        stage ('Remove untagged ECR images') {
            steps{
                unstash 'ecr.repo'
                sh '''
                    #!/bin/bash +x
                    make clean-remote
                '''
            }
        }
        stage('Remove Unused docker image') {
            steps{
                unstash 'ecr.repo'
                sh '''
                    #!/bin/bash +x
                    make clean-local offenderpollpush_version=${offenderpollpush_VERSION}
                '''
            }
        }
    }
    post {
        always {
            // Add a sleep to allow docker step to fully release file locks on failed run
            sleep(time: 3, unit: "SECONDS")
            deleteDir()
        }
        success {
            slackSend(message: "Build successful -${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL.replace('http://', 'https://').replace(':8080', '')}|Open>)", color: 'good')
        }
        failure {
            slackSend(message: "Build failed - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL.replace('http://', 'https://').replace(':8080', '')}|Open>)", color: 'danger')
        }
    }
}
