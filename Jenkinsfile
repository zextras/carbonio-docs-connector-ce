// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

library(
    identifier: 'jenkins-dt3-lib@v1.2.0',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        remote: 'git@github.com:zextras/jenkins-dt3-lib.git',
        credentialsId: 'jenkins-integration-with-github-account'
    ])
)

library(
    identifier: 'jenkins-lib-common@v2.8.8',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        credentialsId: 'jenkins-integration-with-github-account',
        remote: 'git@github.com:zextras/jenkins-lib-common.git'
    ])
)

properties(defaultPipelineProperties())

pipeline {
    agent {
        node {
            label 'zextras-v1'
        }
    }

    environment {
        JAVA_OPTS = '-Dfile.encoding=UTF8'
        LC_ALL = 'C.UTF-8'
        jenkins_build = 'true'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '25'))
        skipDefaultCheckout()
        timeout(time: 2, unit: 'HOURS')
    }

    parameters {
        booleanParam(
            name: 'PREPARE_RELEASE',
            defaultValue: false,
            description: 'Check this to prepare a new release (runs semantic-release)'
        )
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                    semanticRelease.guard()
                }
            }
        }

        stage('Maven') {
            steps {
                script {
                    mavenStage(
                        splitTests: true,
                        postBuildScript: 'cp boot/target/carbonio-docs-connector-*-fatjar.jar package/carbonio-docs-connector.jar'
                    )
                }
            }
        }

        stage('Build deb/rpm') {
            steps {
                script {
                    buildStage(
                        buildFlags: ' -ds '
                    )
                }
            }
        }

        stage('Upload artifacts') {
            when {
                expression { return uploadStage.shouldUpload() }
            }
            tools {
                jfrog 'jfrog-cli'
            }
            steps {
                uploadStage()
            }
        }

        stage('Prepare Release') {
            when {
                allOf {
                    branch 'devel'
                    expression { params.PREPARE_RELEASE == true }
                }
            }
            steps {
                script {
                    semanticRelease()
                }
            }
        }

        stage('Publish docker images') {
            steps {
                dockerStage(
                    dockerfile: 'docker/minimal/carbonio-docs-connector/Dockerfile',
                    imageName: 'registry.dev.zextras.com/dev/carbonio-docs-connector-ce',
                    ocLabels: [
                        title: 'Carbonio Docs Connector CE',
                        description: 'Carbonio Docs Connector Advanced Community Edition'
                    ]
                )
            }
        }
    }
}
