FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN mkdir -p /etc/carbonio/docs-connector

COPY ../../boot/target/carbonio-docs-connector-*-fatjar.jar .

COPY docker/minimal/carbonio-docs-connector/entrypoint.sh entrypoint.sh

# This is needed to make the connection to consul work
ENV CONSUL_HTTP_TOKEN=fake-consul-token

ENTRYPOINT ["/app/entrypoint.sh"]