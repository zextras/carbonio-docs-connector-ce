library(
    identifier: 'jenkins-packages-build-library@1.0.3',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        remote: 'git@github.com:zextras/jenkins-packages-build-library.git',
        credentialsId: 'jenkins-integration-with-github-account'
    ])
)

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
        parallelsAlwaysFailFast()
        skipDefaultCheckout()
        timeout(time: 2, unit: 'HOURS')
    }

    parameters {
        booleanParam defaultValue: false,
            description: 'Whether to upload the packages in playground repositories',
            name: 'PLAYGROUND'
    }

    tools {
        jfrog 'jfrog-cli'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                }
            }
        }

        stage('Setup') {
            steps {
                container('jdk-17') {
                    withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                        sh 'cp $SETTINGS_PATH settings-jenkins.xml'
                    }
                }
            }
        }

        stage('Build jar') {
            steps {
                container('jdk-17') {
                    sh 'mvn -B -settings settings-jenkins.xml clean package'
                    // having every file within the package directory is great simplification
                    sh 'cp boot/target/carbonio-docs-connector-*-fatjar.jar package/carbonio-docs-connector.jar'
                }
            }
        }

        stage('Unit tests') {
            steps {
                container('jdk-17') {
                    sh 'mvn -B --settings settings-jenkins.xml verify -P run-unit-tests'
                }
            }
        }

        stage('Integration tests') {
            steps {
                container('jdk-17') {
                    sh 'mvn -B --settings settings-jenkins.xml verify -P run-integration-tests'
                }
            }
        }

        stage('Coverage') {
            steps {
                container('jdk-17') {
                    sh 'mvn -B --settings settings-jenkins.xml verify -P generate-jacoco-full-report'
                    recordCoverage(tools: [[parser: 'JACOCO']], sourceCodeRetention: 'MODIFIED')
                }
            }
        }

        stage('Build deb/rpm') {
            stages {
                // Replace the pkgrel value with the git commit hash to ensure that
                // each merged PR has unique artifacts and to prevent conflicts between them.
                // Note that the pkgrel value will remain as it was in the codebase to avoid
                // conflicts between multiple open PRs
                stage('Add timestamp and commit hash') {
                    when{
                        branch 'develop'
                    }
                    steps{
                        script{
                            String timestamp = sh(script: 'date +%s', returnStdout: true).trim()
                            String gitCommitShort = env.GIT_COMMIT.take(8)
                            sh """
                                sed -i "s/pkgrel=\\".*\\"/pkgrel=\\"${timestamp}+${gitCommitShort}\\"/" ./package/PKGBUILD
                            """
                        }
                    }
                }

                stage('Build dep/rpm') {
                    steps {
                        sh 'rm settings-jenkins.xml'
                        buildStage([
                            ubuntuSinglePkg: true,
                            rockySinglePkg: true,
                            skipTsOverride: true
                        ])
                    }
                }
            }
        }

        stage('Upload artifacts')
        {
            steps {
                uploadStage([
                    packages: yapHelper.getPackageNames(),
                    rockySinglePkg: true,
                    ubuntuSinglePkg: true,
                ])
            }
        }

        stage('Build and Publish Docker Image') {
            when {
                not {
                    expression { env.BRANCH_NAME.startsWith("PR-") }
                }
            }
            steps {
                container('dind') {
                    withDockerRegistry([
                        credentialsId: 'private-registry',
                        url: 'https://registry.dev.zextras.com'
                    ]) {
                        script {
                            String branchTag = env.BRANCH_NAME.replaceAll('/', '-').toLowerCase()
                            Set<String> imageTags = [ branchTag ]

                            if (env.BRANCH_NAME == 'develop') {
                                imageTags.add('latest')
                            } else if (buildingTag() && env.TAG_NAME?.trim()) {
                                imageTags.add(env.TAG_NAME?.startsWith('v') ? env.TAG_NAME.substring(1) : env.TAG_NAME)
                            }

                            dockerHelper.buildImage([
                                imageName: 'registry.dev.zextras.com/dev/carbonio-docs-connector-ce',
                                imageTags: imageTags,
                                dockerfile: 'docker/minimal/carbonio-docs-connector/Dockerfile',
                                ocLabels: [
                                    title: 'Carbonio Docs Connector CE',
                                    description: 'Carbonio Docs Connector Advanced Community Edition',
                                    version: branchTag
                                ]
                            ])
                        }
                    }
                }
            }
        }
    }
}
