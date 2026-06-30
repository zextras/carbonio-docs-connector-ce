# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM --platform=linux/amd64 ubuntu:noble

LABEL maintainer="Zextras <https://www.zextras.com>"
LABEL description="Carbonio Docs Connector CE - Docs connector service (native binary)"

RUN groupadd -r carbonio-docs-connector && useradd -r -g carbonio-docs-connector carbonio-docs-connector

WORKDIR /app

COPY app/target/*-runner /app/carbonio-docs-connector-ce-runner
RUN chmod +x /app/carbonio-docs-connector-ce-runner

USER carbonio-docs-connector

EXPOSE 10000

ENTRYPOINT ["/app/carbonio-docs-connector-ce-runner", "-Djava.net.preferIPv4Stack=true"]
