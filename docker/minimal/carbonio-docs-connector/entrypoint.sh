#!/bin/sh

echo "" > /etc/carbonio/docs-connector/config.properties

addEnvToProperties() {
  if [ -n "$2" ];
  then echo "$1=$2" >> /etc/carbonio/docs-connector/config.properties;
  else echo "$1 is not set. Skipping it.";
  fi
}

addEnvToProperties "carbonio.docs-connector.host" "${CARBONIO_DOCS_CONNECTOR_HOST}"
addEnvToProperties "carbonio.docs-connector.port" "${CARBONIO_DOCS_CONNECTOR_PORT}"

addEnvToProperties "carbonio.files.host" "${CARBONIO_FILES_HOST}"
addEnvToProperties "carbonio.files.port" "${CARBONIO_FILES_PORT}"

addEnvToProperties "carbonio.user-management.host" "${CARBONIO_USER_MANAGEMENT_HOST}"
addEnvToProperties "carbonio.user-management.port" "${CARBONIO_USER_MANAGEMENT_PORT}"

addEnvToProperties "carbonio.wopi.host" "${CARBONIO_WOPI_HOST}"
addEnvToProperties "carbonio.wopi.port" "${CARBONIO_WOPI_PORT}"

# Build optional JVM args for application-config keys (not networking — these
# cannot go through config.properties because loadConfigFile prefixes them with
# networking-config., but ApplicationConfigService expects application-config.).
EXTRA_ARGS=""
if [ -n "${CARBONIO_REQUESTER_DOMAIN_OVERRIDE}" ]; then
  EXTRA_ARGS="${EXTRA_ARGS} -Dapplication-config.requester-domain-override=${CARBONIO_REQUESTER_DOMAIN_OVERRIDE}"
fi

exec ./carbonio-docs-connector-ce-runner \
     -Djava.net.preferIPv4Stack=true \
     -Dquarkus.log.level=DEBUG \
     ${EXTRA_ARGS}