// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

library(
    identifier: 'jenkins-lib-common@dt3-pipeline',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        credentialsId: 'jenkins-integration-with-github-account',
        remote: 'git@github.com:zextras/jenkins-lib-common.git',
    ])
)

dt3_pipeline(
    repoName: 'carbonio-docs-connector-ce',
    projectType: 'CE',
    mavenPublish: ['app'],
    nativeBuild: [runnerName: 'carbonio-docs-connector-ce-runner'],
    packaging: [
        buildFlags: '-ds',
    ],
    docker: [
        [dockerfile: 'docker/Dockerfile',
         imageName: 'carbonio-docs-connector-ce',
         title: 'Carbonio Docs Connector CE',
         description: 'Carbonio Docs Connector Community Edition',
         platforms: ['linux/amd64', 'linux/arm64'] as Set],
    ],
    sonarqube: true,
    reuse: [:],
    bumpDownstream: [
        repo:                   'zextras/carbonio-docs-connector',
        branch:                 'devel',
        property:               'carbonio-docs-connector-ce.version',
        notificationRecipients: [
            'matteo.galvagni@zextras.com',
            'noman.alishaukat@zextras.com',
            'riccardo.degan@zextras.com',
        ],
    ],
    failureNotificationRecipients: [
        'matteo.galvagni@zextras.com',
        'noman.alishaukat@zextras.com',
        'riccardo.degan@zextras.com',
    ],
)
