# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# native-image can't cross-compile: arm64 runner comes from nativeBuild.aarch64 at app/target-arm64/.
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

FROM registry.dev.zextras.com/dev/carbonio-sidecar:devel

# Service registration
COPY package/carbonio-docs-connector.hcl /consul/config/

# Setup files
COPY package/service-protocol.json /etc/carbonio/docs-connector/service-discover/
COPY package/intentions.json       /etc/carbonio/docs-connector/service-discover/
COPY package/policies.json         /etc/carbonio/docs-connector/service-discover/

# App binary (used for --setup only)
COPY --from=runner --chmod=755 /out/carbonio-docs-connector-ce-runner /usr/share/carbonio/carbonio-docs-connector-ce-runner

# Setup script
COPY --chmod=755 docker/docs-connector-sidecar/carbonio-docs-connector-setup /usr/local/bin/carbonio-docs-connector-setup

ENV SERVICE_NAME=carbonio-docs-connector
ENV SETUP_SCRIPT="carbonio-docs-connector-setup"
ENV TOKEN_FILE=/etc/carbonio/docs-connector/service-discover/token
