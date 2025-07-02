FROM openjdk:17-jdk-slim

WORKDIR /app

RUN mkdir -p /etc/carbonio/docs-connector

COPY ../../boot/target/carbonio-docs-connector-*-fatjar.jar .

# Default values
ENV CARBONIO_DOCS_CONNECTOR_HOST=0.0.0.0
ENV CARBONIO_DOCS_CONNECTOR_PORT=10000
ENV CARBONIO_FILES_HOST=127.0.0.1
ENV CARBONIO_FILES_PORT=20000
ENV CARBONIO_USER_MANAGEMENT_HOST=127.0.0.1
ENV CARBONIO_USER_MANAGEMENT_PORT=20001

# This is needed to make the connection to consul work
ENV CONSUL_HTTP_TOKEN=fake-consul-token

CMD sh -c 'echo "\n\
# Docs Connector Service\n\
carbonio.docs-connector.host=${CARBONIO_DOCS_CONNECTOR_HOST}\n\
carbonio.docs-connector.port=${CARBONIO_DOCS_CONNECTOR_PORT}\n\
\n\
# Files Service\n\
carbonio.files.host=${CARBONIO_FILES_HOST}\n\
carbonio.files.port=${CARBONIO_FILES_PORT}\n\
\n\
# User Management Service\n\
carbonio.user-management.host=${CARBONIO_USER_MANAGEMENT_HOST}\n\
carbonio.user-management.port=${CARBONIO_USER_MANAGEMENT_PORT}" > /etc/carbonio/docs-connector/config.properties && \
JAR=$(ls carbonio-docs-connector-*-fatjar.jar | head -n 1) && \
java -Djava.net.preferIPv4Stack=true \
  -Xmx128m \
  -DLOG_LEVEL=warn \
  -jar "$JAR"'