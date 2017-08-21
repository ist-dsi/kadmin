FROM base/archlinux
MAINTAINER Simão Martins "simao.martins@tecnico.ulisboa.pt"

RUN pacman --noconfirm -Sy krb5 jre8-openjdk sbt git

# This ensures that sbt does its initial thing. Such as:
# Getting org.scala-sbt sbt 1.0.0 ...
# And downloading all of its dependencies
RUN sbt about

ENV REALM ${REALM:-EXAMPLE.COM}
ENV KADMIN_PRINCIPAL ${KADMIN_PRINCIPAL:-kadmin/admin}
ENV KADMIN_PASSWORD ${KADMIN_PASSWORD:-MITiys4K5}

COPY init-script.sh configureKerberosClient.sh /tmp/
CMD /tmp/init-script.sh
