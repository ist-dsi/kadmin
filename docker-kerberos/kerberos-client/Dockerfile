FROM debian:jessie
MAINTAINER Simão Martins "simao.martins@tecnico.ulisboa.pt"

# We need JDK and SBT because we are going to perform the tests inside this container
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list

RUN apt-get -qq -y update && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y krb5-user && \
  apt-get install -y openjdk-8-jdk sbt apt-transport-https && \
  apt-get clean

# This ensures that sbt does its initial thing. Such as:
# Getting org.scala-sbt sbt 0.13.9 ...
RUN sbt help

COPY ./init-script.sh /tmp/
COPY ./configureKerberosClient.sh /tmp/
CMD /tmp/init-script.sh