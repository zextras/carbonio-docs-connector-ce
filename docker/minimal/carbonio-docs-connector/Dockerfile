FROM --platform=linux/amd64 ubuntu:noble

WORKDIR /app

RUN mkdir -p /etc/carbonio/docs-connector

COPY ../../app/target/*-runner carbonio-docs-connector-ce-runner
RUN chmod +x carbonio-docs-connector-ce-runner

COPY docker/minimal/carbonio-docs-connector/entrypoint.sh entrypoint.sh

# This is needed to make the connection to consul work
ENV CONSUL_HTTP_TOKEN=fake-consul-token

ENTRYPOINT ["/app/entrypoint.sh"]