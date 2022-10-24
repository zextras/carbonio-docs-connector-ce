<!--
SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

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
