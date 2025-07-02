FROM ubuntu:22.04

USER root

RUN apt update && \
    apt install -y \
    gnupg2 \
    ca-certificates \
    && apt clean

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 52FD40243E584A21

RUN echo deb https://repo.zextras.io/release/ubuntu jammy main > /etc/apt/sources.list.d/zextras.list

RUN echo "resolvconf resolvconf/linkify-resolvconf boolean false" | debconf-set-selections

RUN apt update && \
    DEBIAN_FRONTEND=noninteractive apt install -y \
    --no-install-recommends \
    carbonio-docs-editor \
    && apt clean \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/zextras/docs/generated/systemplate \
    /opt/zextras/docs/generated/child-roots \
    /var/log/carbonio-docs-editor \
    /etc/coolwsd

RUN chown -R carbonio-docs-editor:carbonio-docs-editor /opt/zextras/docs/generated \
    && chown -R carbonio-docs-editor:carbonio-docs-editor /var/log/carbonio-docs-editor

ENV LANG=C.UTF-8

RUN echo '#!/bin/bash\n\
echo "Starting Carbonio Docs Editor..."\n\
/opt/zextras/docs/bin/coolwsd \
    --version \
    --o:sys_template_path=/opt/zextras/docs/generated/systemplate \
    --o:child_root_path=/opt/zextras/docs/generated/child-roots \
    --o:file_server_root_path=/opt/zextras/docs/share/coolwsd \
    --o:net.listen=0.0.0.0 \
    --o:net.port=10000 \
    --o:ssl.enable=false \
    --o:ssl.termination=true \
    --o:security.seccomp=false \
    --o:mount_jail_tree=false \
    --o:logging.level=warning \
    --o:logging.level_startup=trace \
    --o:storage.wopi[@allow]=true \
    --o:storage.wopi.host[0]=docs-connector \
    --o:storage.wopi.host[1]=localhost \
    --o:storage.wopi.host[2]=127.0.0.1 \
    --o:net.frame_ancestors=localhost:9000 \
    --port=10000' > /opt/zextras/start-coolwsd.sh

RUN chmod +x /opt/zextras/start-coolwsd.sh

USER carbonio-docs-editor

EXPOSE 10000

CMD ["/opt/zextras/start-coolwsd.sh"]