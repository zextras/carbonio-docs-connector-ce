// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
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

// carbonio-docs-connector-ce uses a maven-shade fat JAR
// (boot/target/carbonio-docs-connector-ce-*-fatjar.jar), not a Quarkus *-runner.jar.
// dt3_pipeline's jarBuild copies only *-runner.jar patterns, so we use appModule: 'boot'
// to enable the Java build stage and handle the JAR copy via packaging.overrides.preBuildScript
// (runs in the yap container after workspace unstash, before yap build).
// buildFlags: '-ds' skips makedep/dep resolution — no Zextras makedeps in PKGBUILD,
// so Zextras repo injection is not needed.
dt3_pipeline(
    repoName: 'carbonio-docs-connector-ce',
    appModule: 'boot',
    packaging: [
        pkgbuildPath: 'package/PKGBUILD',
        buildFlags: '-ds',
        overrides: [
            ubuntu: [
                preBuildScript: '''
                    cp -a boot/target/carbonio-docs-connector-ce-*-fatjar.jar package/carbonio-docs-connector.jar
                ''',
            ],
            rocky: [
                preBuildScript: '''
                    cp -a boot/target/carbonio-docs-connector-ce-*-fatjar.jar package/carbonio-docs-connector.jar
                ''',
            ],
        ],
    ],
    docker: [[
        dockerfile: 'docker/minimal/carbonio-docs-connector/Dockerfile',
        imageName: 'carbonio-docs-connector-ce',
        title: 'Carbonio Docs Connector CE',
        description: 'Carbonio Docs Connector Advanced Community Edition',
        platforms: ['linux/amd64', 'linux/arm64'] as Set,
    ]],
    reuse: [projectType: 'CE'],
    gitleaks: true,
)
