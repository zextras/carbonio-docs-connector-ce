# Prep stage runs on BUILDPLATFORM (amd64 CI builder) — no QEMU. Creates the
# arch-independent config dir so the final image needs no RUN step.
FROM --platform=$BUILDPLATFORM docker.io/library/busybox AS prep
RUN mkdir -p /staging/etc/carbonio/docs-connector

FROM --platform=linux/amd64 docker.io/library/ubuntu:noble

WORKDIR /app

COPY --from=prep /staging/etc /etc

COPY ../../app/target/*-runner carbonio-docs-connector-ce-runner
RUN chmod +x carbonio-docs-connector-ce-runner

COPY docker/minimal/carbonio-docs-connector/entrypoint.sh entrypoint.sh

# This is needed to make the connection to consul work
ENV CONSUL_HTTP_TOKEN=fake-consul-token

ENTRYPOINT ["/app/entrypoint.sh"]
