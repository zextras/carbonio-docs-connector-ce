<!--
SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

### [0.5.1](https://github.com/zextras/carbonio-docs-connector-ce/compare/v0.5.0...v0.5.1) (2025-02-03)

## [0.5.0](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.4.2...v0.5.0) (2024-11-18)


### Features

* add live health check ([#45](https://github.com/Zextras/carbonio-docs-connector-ce/issues/45)) ([ebf6bd0](https://github.com/Zextras/carbonio-docs-connector-ce/commit/ebf6bd0c21739978c84ead6130c034112e22e1f6))
* block the opening of file that exceed the maximum size limit set ([#46](https://github.com/Zextras/carbonio-docs-connector-ce/issues/46)) ([19ba7a7](https://github.com/Zextras/carbonio-docs-connector-ce/commit/19ba7a7d924c9bd436b6c7ceefba952d23aed11d))


### Bug Fixes

* change public URL endpoint returned by openFile ([#47](https://github.com/Zextras/carbonio-docs-connector-ce/issues/47)) ([85d0d4a](https://github.com/Zextras/carbonio-docs-connector-ce/commit/85d0d4adf6ab2e6d20ccff6f8543eff5e16a0244))

### [0.4.2](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.4.1...v0.4.2) (2024-08-27)


### Features

* add ubuntu 24.04 (ubuntu-noble) support ([74b8a11](https://github.com/Zextras/carbonio-docs-connector-ce/commit/74b8a11bc4b16a388975156cef32068a08cfd3b1))


### Bug Fixes

* move jar from /usr/bin to usr/share to follow the FHS standard ([#39](https://github.com/Zextras/carbonio-docs-connector-ce/issues/39)) ([c2ee668](https://github.com/Zextras/carbonio-docs-connector-ce/commit/c2ee668c455abb7aa5afe01298625e913d100665))

### [0.4.1](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.4.0...v0.4.1) (2024-06-21)


### Bug Fixes

* update carbonio-files permission in intentions.json ([#36](https://github.com/Zextras/carbonio-docs-connector-ce/issues/36)) ([30b866b](https://github.com/Zextras/carbonio-docs-connector-ce/commit/30b866b90490e2c64853f8269fc5d0204dfc8d80)), closes [#32](https://github.com/Zextras/carbonio-docs-connector-ce/issues/32)

## [0.4.0](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.3.3...v0.4.0) (2024-06-17)


### Features

* add health check API ([#32](https://github.com/Zextras/carbonio-docs-connector-ce/issues/32)) ([fba70a2](https://github.com/Zextras/carbonio-docs-connector-ce/commit/fba70a2abb5bbe0a0c64cdcfa507ef0f4a8a3526))
* update docs connector to use new user management sdk with returned user type ([#34](https://github.com/Zextras/carbonio-docs-connector-ce/issues/34)) ([cb1e015](https://github.com/Zextras/carbonio-docs-connector-ce/commit/cb1e0150025cef25230cdb58cdbaa22bc17e1ca1))
* use new user management sdk with returned user status ([#33](https://github.com/Zextras/carbonio-docs-connector-ce/issues/33)) ([e2e8086](https://github.com/Zextras/carbonio-docs-connector-ce/commit/e2e8086c321b13ed4412265ffa229a3300a7d31f))

### [0.3.3](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.3.2...v0.3.3) (2024-04-12)

### [0.3.2](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.3.1...v0.3.2) (2024-01-16)


### Features

* move to yap agent and add rhel9 support ([#25](https://github.com/Zextras/carbonio-docs-connector-ce/issues/25)) ([db3c99d](https://github.com/Zextras/carbonio-docs-connector-ce/commit/db3c99d44d9e14f5e8466207ffc2d45563e1c83f))

### [0.3.1](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.3.0...v0.3.1) (2023-10-30)


### Bug Fixes

* update carbonio-user-management-sdk and fix locale in url ([#23](https://github.com/Zextras/carbonio-docs-connector-ce/issues/23)) ([c346a8d](https://github.com/Zextras/carbonio-docs-connector-ce/commit/c346a8d2eefdb62e6d49290ec467d1edb34474bf))

## [0.3.0](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.2.0...v0.3.0) (2023-08-31)


### Features

* add the requester locale during the docs-editor URL generation ([#20](https://github.com/Zextras/carbonio-docs-connector-ce/issues/20)) ([052b177](https://github.com/Zextras/carbonio-docs-connector-ce/commit/052b1774eb05b010524dddcc5612aaeebd047b69))

## [0.2.0](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.1.0...v0.2.0) (2023-04-27)


### Features

* add servlet filters for cookie and token validations ([#16](https://github.com/Zextras/carbonio-docs-connector-ce/issues/16)) ([3c8545f](https://github.com/Zextras/carbonio-docs-connector-ce/commit/3c8545f55b33310b9249c91a4df00b7eb8c9d9f8))

## [0.1.0](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.0.4...v0.1.0) (2022-09-29)


### ⚠ BREAKING CHANGES

* the `type` enumerator in the `/files/create` API now contains following entries:
 - LIBRE_DOCUMENT (represents the odt format)
 - LIBRE_SPREADSHEET (represents the ods format)
 - LIBRE_PRESENTATION (represents the odp format)
 - MS_DOCUMENT (represents the docx format)
 - MS_SPREADSHEET (represents the xlsx format)
 - MS_PRESENTATION (represents the pptx format)

Added the docx, xslx, pptx empty templates in the final jar.

### Features

* DOCS-175 - Allow to create Microsoft documents (docx, xlsx, pptx) ([#11](https://github.com/Zextras/carbonio-docs-connector-ce/issues/11)) ([a918029](https://github.com/Zextras/carbonio-docs-connector-ce/commit/a918029a69a6e10b4ac292601ba9be9324f59684))
* DOCS-177 - Add logging system using logback ([#12](https://github.com/Zextras/carbonio-docs-connector-ce/issues/12)) ([a9bfdc3](https://github.com/Zextras/carbonio-docs-connector-ce/commit/a9bfdc3fc55b420530df3e79b720d08e460f0021))

### [0.0.4](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.0.3...v0.0.4) (2022-08-24)


### Bug Fixes

* DOCS-167 Remove access token from the returned wopi endpoint ([#9](https://github.com/Zextras/carbonio-docs-connector-ce/issues/9)) ([cfb1029](https://github.com/Zextras/carbonio-docs-connector-ce/commit/cfb1029382f8bb64e887d4dc6bec913137369366))

### [0.0.3](https://github.com/Zextras/carbonio-docs-connector-ce/compare/v0.0.2...v0.0.3) (2022-04-05)


### Features

* DOCS-156 - Update carbonio-files-sdk ([#7](https://github.com/Zextras/carbonio-docs-connector-ce/issues/7)) ([eaed61a](https://github.com/Zextras/carbonio-docs-connector-ce/commit/eaed61a93517d0437b1b4d7344a06d3747d15dbb))
