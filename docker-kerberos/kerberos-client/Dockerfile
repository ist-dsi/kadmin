# We need backports for the openjdk-8-jdk package which is a dependency of sbt
FROM debian:jessie-backports
MAINTAINER Simão Martins "simao.martins@tecnico.ulisboa.pt"

ENV DEBIAN_FRONTEND noninteractive
# The -qq implies --yes
RUN apt-get -qq update
RUN apt-get -qq install apt-transport-https locales krb5-user

RUN locale-gen "en_US.UTF-8"
RUN echo "LC_ALL=\"en_US.UTF-8\"" >> /etc/default/locale

# We need JDK and SBT because we are going to perform the tests inside this container
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
RUN apt-get -qq update
RUN apt-get -qq install -t jessie-backports sbt
RUN apt-get -qq clean

# This ensures that sbt does its initial thing. Such as:
# Getting org.scala-sbt sbt 0.13.13 ...
# And downloading all of its dependencies
RUN sbt about

ENV REALM ${REALM:-EXAMPLE.COM}
ENV KADMIN_PRINCIPAL ${KADMIN_PRINCIPAL:-kadmin/admin}
ENV KADMIN_PASSWORD ${KADMIN_PASSWORD:-MITiys4K5}

COPY init-script.sh configureKerberosClient.sh /tmp/
CMD /tmp/init-script.sh
