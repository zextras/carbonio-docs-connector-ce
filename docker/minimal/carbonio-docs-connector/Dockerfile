FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

RUN mkdir -p /etc/carbonio/docs-connector

COPY ../../boot/target/carbonio-docs-connector-*-fatjar.jar .

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
carbonio.user-management.port=${CARBONIO_USER_MANAGEMENT_PORT}\n\
\n\
# WOPI Configuration\n\
carbonio.wopi.host=${CARBONIO_WOPI_HOST}\n\
carbonio.wopi.port=${CARBONIO_WOPI_PORT}" > /etc/carbonio/docs-connector/config.properties && \
if [ -n "${CARBONIO_REQUESTER_DOMAIN_OVERRIDE}" ]; then \
  echo "\n# Requester Domain Override\ncarbonio.docs-connector.requester-domain-override=${CARBONIO_REQUESTER_DOMAIN_OVERRIDE}" >> /etc/carbonio/docs-connector/config.properties; \
fi && \
JAR=$(ls carbonio-docs-connector-*-fatjar.jar | head -n 1) && \
java -Djava.net.preferIPv4Stack=true \
  -Xmx128m \
  -DLOG_LEVEL=debug \
  -jar "$JAR"'