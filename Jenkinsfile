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
    identifier: 'jenkins-lib-common@1.1.2',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        credentialsId: 'jenkins-integration-with-github-account',
        remote: 'git@github.com:zextras/jenkins-lib-common.git',
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
            description: 'Check this to prepare a new release (creates pre-release branch and PR)'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip unit tests and integration tests'
        )
        booleanParam(
            name: 'SKIP_CHECKS',
            defaultValue: false,
            description: 'Skip coverage and SonarQube analysis'
        )
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                }
            }
        }

        stage('Build jar') {
            steps {
                script {
                    def profile = '-P dev'
                    if (env.TAG_NAME) {
                        profile = '-P prod'
                    }
                    container('jdk-17') {
                        sh """
                            mvn -B clean package ${profile}
                            cp boot/target/carbonio-docs-connector-*-fatjar.jar package/carbonio-docs-connector.jar
                        """
                    }
                }
            }
        }

        stage('Unit tests') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P run-unit-tests'
                }
            }
        }

        stage('Integration tests') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P run-integration-tests'
                }
            }
        }

        stage('Coverage') {
            when {
                expression { params.SKIP_CHECKS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P generate-jacoco-full-report'
                    recordCoverage(
                        tools: [[parser: 'JACOCO']],
                        sourceCodeRetention: 'MODIFIED'
                    )
                }
            }
        }

        stage('SonarQube analysis') {
            when {
               allOf {
                   expression { params.SKIP_CHECKS == false }
                   anyOf {
                       branch 'devel'
                       expression { env.BRANCH_NAME.contains("PR") }
                   }
               }
            }
            steps {
                container('jdk-17') {
                    withSonarQubeEnv(credentialsId: 'sonarqube-user-token', installationName: 'SonarQube instance') {
                        sh 'mvn -B sonar:sonar'
                    }
                }
            }
        }

        stage('Build deb/rpm') {
            steps {
                script {
                    buildPackages([
                        pkgbuildPath: 'package/PKGBUILD',
                        buildStageConfig: [
                            rockySinglePkg: true,
                            ubuntuSinglePkg: true
                        ]
                    ])
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
                uploadStage([
                    packages: yapHelper.resolvePackageNames(),
                    rockySinglePkg: true,
                    ubuntuSinglePkg: true,
                ])
            }
        }

        stage('Prepare Release') {
            agent {
                node {
                    label 'nodejs-v1'
                }
            }
            when {
                allOf {
                    branch 'devel'
                    expression { params.PREPARE_RELEASE == true }
                    not {
                        expression {
                            return env.GIT_COMMIT_MSG.contains('[skip ci]') ||
                                   env.GIT_COMMIT_MSG.contains('chore(release):')
                        }
                    }
                }
            }
            steps {
                script {
                    container('nodejs-20') {
                        prepareRelease(
                            repoName: 'carbonio-docs-connector-ce'
                        )
                    }
                }
            }
        }

        stage('Tag for release') {
            when {
                allOf {
                    branch 'devel'
                    expression {
                        return env.GIT_COMMIT_MSG.contains('chore(release):') &&
                               env.GIT_COMMIT_MSG.contains('[skip ci]')
                    }
                }
            }
            steps {
                script {
                    tagRelease()
                }
            }
        }

        stage('Publish docker images') {
            steps {
                dockerStage([
                    dockerfile: 'docker/minimal/carbonio-docs-connector/Dockerfile',
                    imageName: 'registry.dev.zextras.com/dev/carbonio-docs-connector-ce',
                    ocLabels: [
                        title: 'Carbonio Docs Connector CE',
                        description: 'Carbonio Docs Connector Advanced Community Edition',
                    ]
                ])
            }
        }
    }
}
