# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# native-image can't cross-compile: arm64 runner comes from nativeBuild.aarch64 at app/target-arm64/.
ARG SIDECAR_IMAGE=registry.dev.zextras.com/dev/carbonio-sidecar:main
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

FROM --platform=$BUILDPLATFORM docker.io/library/alpine:3.23 AS consul-template
ARG TARGETARCH
ARG CONSUL_TEMPLATE_VERSION=0.42.1
RUN apk add --no-cache curl unzip && \
    curl -fsSL -o /tmp/ct.zip "https://releases.hashicorp.com/consul-template/${CONSUL_TEMPLATE_VERSION}/consul-template_${CONSUL_TEMPLATE_VERSION}_linux_${TARGETARCH}.zip" && \
    curl -fsSL -o /tmp/ct.sums "https://releases.hashicorp.com/consul-template/${CONSUL_TEMPLATE_VERSION}/consul-template_${CONSUL_TEMPLATE_VERSION}_SHA256SUMS" && \
    echo "$(grep "_linux_${TARGETARCH}.zip" /tmp/ct.sums | cut -d' ' -f1)  /tmp/ct.zip" | sha256sum -c - && \
    unzip -j /tmp/ct.zip consul-template -d /out && \
    chmod +x /out/consul-template

FROM ${SIDECAR_IMAGE}

# Service registration
COPY --from=consul-template /out/consul-template /usr/local/bin/consul-template
COPY package/carbonio-docs-connector-template.hcl /consul/templates/carbonio-docs-connector-template.hcl

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

COPY --chmod=755 docker/docs-connector-sidecar/carbonio-docs-connector-hcl-entrypoint /usr/local/bin/carbonio-docs-connector-hcl-entrypoint
ENTRYPOINT ["carbonio-docs-connector-hcl-entrypoint"]
