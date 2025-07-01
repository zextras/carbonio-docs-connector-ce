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
    /var/log/carbonio-docs-editor

ENV LANG=C.UTF-8

USER carbonio-docs-editor

EXPOSE 10000

CMD ["/opt/zextras/docs/bin/coolwsd", "--version", "--o:sys_template_path=/opt/zextras/docs/generated/systemplate", "--o:child_root_path=/opt/zextras/docs/generated/child-roots", "--o:file_server_root_path=/opt/zextras/docs/share/coolwsd", "--port=10000"]