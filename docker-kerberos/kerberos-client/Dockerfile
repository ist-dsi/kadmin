# We need backports for the openjdk-8-jdk package
FROM debian:jessie-backports
MAINTAINER Simão Martins "simao.martins@tecnico.ulisboa.pt"

RUN apt-get -qq -y update && \
  apt-get install -y apt-transport-https

# We need JDK and SBT because we are going to perform the tests inside this container
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list

RUN apt-get -qq -y update
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y krb5-user openjdk-8-jdk sbt && \
  apt-get clean

# This ensures that sbt does its initial thing. Such as:
# Getting org.scala-sbt sbt 0.13.11 ...
# And downloading all of its dependencies
RUN sbt -version

ENV REALM ${REALM:-EXAMPLE.COM}
ENV KADMIN_PRINCIPAL ${KADMIN_PRINCIPAL:-kadmin/admin}
ENV KADMIN_PASSWORD ${KADMIN_PASSWORD:-MITiys4K5}

COPY init-script.sh configureKerberosClient.sh /tmp/
CMD /tmp/init-script.sh
