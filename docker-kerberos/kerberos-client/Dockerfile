FROM base/archlinux
MAINTAINER Simão Martins "simao.martins@tecnico.ulisboa.pt"

RUN pacman --noconfirm -Sy krb5 jre8-openjdk

# Unfortunately the sbt package is not up to date so we need to use the one from AUR
RUN pacman --noconfirm -S base-devel git rsync
RUN groupadd --system builder && \
    useradd --system --gid builder builder
RUN mkdir /tmp/builder && \
    chown -R builder:builder /tmp/builder
USER builder
RUN cd /tmp/builder && \
    git clone https://aur.archlinux.org/sbt-latest.git && \
    cd /tmp/builder/sbt-latest && \
    makepkg --clean
USER root
RUN pacman --noconfirm -U /tmp/builder/sbt-latest/*.tar.xz

# This ensures that sbt does its initial thing. Such as:
# Getting org.scala-sbt sbt 0.13.16 ...
# And downloading all of its dependencies
RUN sbt about

ENV REALM ${REALM:-EXAMPLE.COM}
ENV KADMIN_PRINCIPAL ${KADMIN_PRINCIPAL:-kadmin/admin}
ENV KADMIN_PASSWORD ${KADMIN_PASSWORD:-MITiys4K5}

COPY init-script.sh configureKerberosClient.sh /tmp/
CMD /tmp/init-script.sh
