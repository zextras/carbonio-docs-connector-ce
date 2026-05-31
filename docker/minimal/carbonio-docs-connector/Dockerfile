# Prep stage runs on BUILDPLATFORM (amd64 CI builder) — no QEMU. Creates the
# arch-independent config dir so the final image needs no RUN step.
FROM --platform=$BUILDPLATFORM busybox AS prep
RUN mkdir -p /staging/etc/carbonio/docs-connector

FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY --from=prep /staging/etc /etc

COPY ../../boot/target/carbonio-docs-connector-*-fatjar.jar .

COPY docker/minimal/carbonio-docs-connector/entrypoint.sh entrypoint.sh

# This is needed to make the connection to consul work
ENV CONSUL_HTTP_TOKEN=fake-consul-token

ENTRYPOINT ["/app/entrypoint.sh"]