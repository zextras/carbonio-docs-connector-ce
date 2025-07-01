FROM openjdk:17-jdk-slim

WORKDIR /app

RUN mkdir -p /etc/carbonio/docs-connector

COPY ../../boot/target/carbonio-docs-connector-*-SNAPSHOT-jar-with-dependencies.jar .

# Default values
ENV CARBONIO_TASKS_HOST=0.0.0.0
ENV CARBONIO_TASKS_PORT=10000
ENV CARBONIO_POSTGRES_HOST=127.0.0.1
ENV CARBONIO_POSTGRES_PORT=20000
ENV CARBONIO_USER_MANAGEMENT_HOST=127.0.0.1
ENV CARBONIO_USER_MANAGEMENT_PORT=20001

CMD sh -c 'echo "\n\
carbonio.tasks.host=${CARBONIO_TASKS_HOST}\n\
carbonio.tasks.port=${CARBONIO_TASKS_PORT}\n\
\n\
carbonio.postgres.host=${CARBONIO_POSTGRES_HOST}\n\
carbonio.postgres.port=${CARBONIO_POSTGRES_PORT}\n\
\n\
carbonio.user-management.host=${CARBONIO_USER_MANAGEMENT_HOST}\n\
carbonio.user-management.port=${CARBONIO_USER_MANAGEMENT_PORT}" > /etc/carbonio/docs-connector/config.properties && \
JAR=$(ls carbonio-docs-connector-*-jar-with-dependencies.jar | head -n 1) && \
java -Djava.net.preferIPv4Stack=true \
     -Xms1024m \
     -Xmx2048m \
     -jar "$JAR"'
