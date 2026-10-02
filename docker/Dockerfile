# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# native-image cannot cross-compile (oracle/graal#407): nativeBuild.aarch64 builds
# the arm64 runner on arm64 hardware and lands it in app/target-arm64/.
FROM --platform=$BUILDPLATFORM docker.io/library/busybox AS runner

ARG TARGETARCH

COPY app/target/       /src/target/
COPY app/target-arm64/ /src/target-arm64/

RUN set -eu; mkdir -p /out; \
    case "${TARGETARCH}" in \
      amd64) src=/src/target ;; \
      arm64) src=/src/target-arm64 ;; \
      *) echo "unsupported TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    cp "${src}"/*-runner /out/carbonio-docs-connector-ce-runner

FROM ubuntu:noble

LABEL maintainer="Zextras <https://www.zextras.com>"
LABEL description="Carbonio Docs Connector CE - Docs connector service (native binary)"

RUN groupadd -r carbonio-docs-connector && useradd -r -g carbonio-docs-connector carbonio-docs-connector

WORKDIR /app

COPY --from=runner --chmod=755 /out/carbonio-docs-connector-ce-runner /app/carbonio-docs-connector-ce-runner

USER carbonio-docs-connector

EXPOSE 10000

ENTRYPOINT ["/app/carbonio-docs-connector-ce-runner", "-Djava.net.preferIPv4Stack=true"]
